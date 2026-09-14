package com.space.browser;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** CDP transport to this process's own WebView. No TCP listener, proxy or TLS interception. */
final class CdpSocket implements Closeable {
    private static final int MAX_MESSAGE=16*1024*1024;
    private static final Set<String> CLAIMED=ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Integer,Consumer<JSONObject>> callbacks=new ConcurrentHashMap<>();
    private final AtomicInteger ids=new AtomicInteger();
    private final SecureRandom random=new SecureRandom();
    private volatile LocalSocket socket;
    private volatile boolean closed;
    private String targetId="";
    private InputStream input;
    private OutputStream output;
    private Consumer<JSONObject> events;
    private Consumer<String> disconnected;

    static String socketName(){return "webview_devtools_remote_"+Process.myPid();}
    private static LocalSocket local() throws IOException {
        LocalSocket s=new LocalSocket();
        try{s.connect(new LocalSocketAddress(socketName(),LocalSocketAddress.Namespace.ABSTRACT));s.setSoTimeout(1500);return s;}
        catch(IOException e){s.close();throw e;}
    }
    private static String headers(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();int state=0;
        while(out.size()<65536){int b=in.read();if(b<0)throw new EOFException("Incomplete debugger HTTP headers");out.write(b);state=(state==0&&b==13)?1:(state==1&&b==10)?2:(state==2&&b==13)?3:(state==3&&b==10)?4:0;if(state==4)return out.toString("ISO-8859-1");}
        throw new IOException("Debugger headers too large");
    }
    private static Map<String,String> headerMap(String h){Map<String,String> m=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);for(String line:h.split("\r\n")){int colon=line.indexOf(':');if(colon>0)m.put(line.substring(0,colon).trim(),line.substring(colon+1).trim());}return m;}
    private static byte[] exact(InputStream in,int length) throws IOException {byte[] b=new byte[length];int n=0;while(n<length){int count=in.read(b,n,length-n);if(count<0)throw new EOFException();n+=count;}return b;}
    private static JSONArray targets() throws Exception {
        try(LocalSocket s=local()){
            s.getOutputStream().write("GET /json/list HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            InputStream in=s.getInputStream();String h=headers(in);if(!h.startsWith("HTTP/1.1 200"))throw new IOException("Debugger discovery unavailable");
            String length=headerMap(h).get("Content-Length");byte[] body;
            if(length!=null){int n=Integer.parseInt(length);if(n>1024*1024)throw new IOException("Too many debugger targets");body=exact(in,n);}
            else {ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>=0){if(b.size()+n>1024*1024)throw new IOException("Target list too large");b.write(buf,0,n);}body=b.toByteArray();}
            return new JSONArray(new String(body,StandardCharsets.UTF_8));
        }
    }
    /** Called off the UI thread. The unique blank-page marker prevents cross-tab attribution. */
    void connect(String marker,Consumer<JSONObject> eventSink,Consumer<String> onDisconnect) throws Exception {
        events=eventSink;disconnected=onDisconnect;JSONObject selected=null;
        Exception last=null;
        for(int attempt=0;attempt<35&&!closed;attempt++){
            try {JSONArray list=targets();
                for(int i=0;i<list.length();i++){JSONObject t=list.getJSONObject(i);String id=t.optString("id");
                    if(!t.optString("url").equals(marker)||id.isEmpty()||t.optString("webSocketDebuggerUrl").isEmpty())continue;
                    synchronized(CLAIMED){if(CLAIMED.add(id)){selected=t;targetId=id;break;}}
                }
            }catch(Exception e){last=e;}
            if(selected!=null)break;Thread.sleep(100);
        }
        if(closed)throw new IOException("Capture stopped");
        if(selected==null)throw new IOException("Cannot attach to this WebView"+(last==null?"":": "+last.getMessage()));
        try{
            socket=local();input=new BufferedInputStream(socket.getInputStream(),32768);output=socket.getOutputStream();
            byte[] nonce=new byte[16];random.nextBytes(nonce);String key=java.util.Base64.getEncoder().encodeToString(nonce);
            String path=new java.net.URI(selected.getString("webSocketDebuggerUrl")).getRawPath();
            String request="GET "+path+" HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: "+key+"\r\nSec-WebSocket-Version: 13\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));output.flush();String h=headers(input);
            String expected=java.util.Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            if(!h.startsWith("HTTP/1.1 101")||!expected.equals(headerMap(h).get("Sec-WebSocket-Accept")))throw new IOException("Debugger handshake rejected");
            socket.setSoTimeout(0);
            Thread reader=new Thread(this::readLoop,"Space-network-events");reader.setDaemon(true);reader.start();
        }catch(Exception e){close();throw e;}
    }
    void command(String method,JSONObject params,String session,Consumer<JSONObject> reply){
        if(closed){if(reply!=null)reply.accept(error("Capture disconnected"));return;}
        int id=ids.incrementAndGet();if(reply!=null){if(callbacks.size()>1000){reply.accept(error("Too many pending capture commands"));return;}callbacks.put(id,reply);}
        try{JSONObject message=new JSONObject().put("id",id).put("method",method).put("params",params==null?new JSONObject():params);if(session!=null&&!session.isEmpty())message.put("sessionId",session);frame(1,message.toString().getBytes(StandardCharsets.UTF_8));}
        catch(Exception e){Consumer<JSONObject> cb=callbacks.remove(id);if(cb!=null)cb.accept(error(e.getMessage()));}
    }
    static JSONObject error(String reason){try{return new JSONObject().put("error",new JSONObject().put("message",reason));}catch(JSONException impossible){return new JSONObject();}}
    private synchronized void frame(int opcode,byte[] bytes) throws IOException {
        if(output==null||closed)throw new IOException("Capture disconnected");
        ByteArrayOutputStream header=new ByteArrayOutputStream();header.write(0x80|opcode);int n=bytes.length;
        if(n<126)header.write(0x80|n);else if(n<=65535){header.write(0x80|126);header.write(n>>>8);header.write(n);}else{header.write(0x80|127);for(int i=7;i>=0;i--)header.write((int)((long)n>>>(8*i)));}
        byte[] mask=new byte[4];random.nextBytes(mask);header.write(mask);output.write(header.toByteArray());
        byte[] block=new byte[Math.min(8192,n)];for(int start=0;start<n;start+=block.length){int length=Math.min(block.length,n-start);for(int i=0;i<length;i++)block[i]=(byte)(bytes[start+i]^mask[(start+i)%4]);output.write(block,0,length);}output.flush();
    }
    private void readLoop(){
        String reason="Capture disconnected";
        try {ByteArrayOutputStream message=new ByteArrayOutputStream();int kind=0;
            while(!closed){int a=input.read(),b=input.read();if(a<0||b<0)break;boolean fin=(a&128)!=0;int opcode=a&15;long length=b&127;
                if(length==126){byte[] size=exact(input,2);length=((size[0]&255)<<8)|(size[1]&255);}else if(length==127){length=0;for(byte v:exact(input,8)){if(length>(MAX_MESSAGE>>>8))throw new IOException("Capture frame exceeds memory limit");length=(length<<8)|(v&255);}}
                if(length>MAX_MESSAGE)throw new IOException("Capture frame exceeds memory limit");byte[] mask=(b&128)!=0?exact(input,4):null;byte[] payload=exact(input,(int)length);if(mask!=null)for(int i=0;i<payload.length;i++)payload[i]^=mask[i%4];
                if(opcode==8)break;if(opcode==9){frame(10,payload);continue;}if(opcode==10)continue;
                if(opcode!=0){kind=opcode;message.reset();}if(message.size()+length>MAX_MESSAGE)throw new IOException("Capture message exceeds memory limit");message.write(payload);
                if(fin){if(kind==1){JSONObject json=new JSONObject(message.toString("UTF-8"));if(json.has("id")){Consumer<JSONObject> cb=callbacks.remove(json.getInt("id"));if(cb!=null)cb.accept(json);}else if(events!=null)events.accept(json);}message.reset();}
            }
        }catch(Exception e){reason=e.getMessage()==null?reason:e.getMessage();}
        boolean notify=!closed;close();if(notify&&disconnected!=null)disconnected.accept(reason);
    }
    @Override public void close(){
        closed=true;LocalSocket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}
        if(!targetId.isEmpty())CLAIMED.remove(targetId);
        List<Consumer<JSONObject>> pending=new ArrayList<>(callbacks.values());callbacks.clear();
        for(Consumer<JSONObject> cb:pending)cb.accept(error("Capture disconnected"));
    }
}
