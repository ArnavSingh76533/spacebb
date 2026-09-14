package com.space.browser;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records CDP events without reissuing requests or changing the page's response stream. */
final class NetworkRecorder implements AutoCloseable {
    static final int BODY_LIMIT=2*1024*1024, TOTAL_BODY_LIMIT=12*1024*1024, RECORD_LIMIT=2000;
    private final ArrayList<NetworkRecord> records=new ArrayList<>();
    private final Map<String,Chain> chains=new LinkedHashMap<>();
    private final CdpSocket cdp=new CdpSocket();
    private volatile boolean closed,connected,paused;
    private int limit=RECORD_LIMIT;
    synchronized void setLimit(int value){limit=Math.max(50,Math.min(2000,value));trim();revision++;}
    synchronized void pause(boolean value){paused=value;state=value?"Capture paused":"Recording browser requests";revision++;}
    boolean paused(){return paused;}
    synchronized void pin(long id){for(NetworkRecord r:records)if(r.id==id)r.pinned=!r.pinned;revision++;}
    synchronized void addImported(NetworkRecord r){r.complete=true;records.add(r);sequence=Math.max(sequence,r.id);trim();revision++;}
    synchronized void markBlocked(String url){for(int i=records.size()-1;i>=0;i--){NetworkRecord r=records.get(i);if(r.url.equals(url)){r.blocked=true;r.error="Blocked by Space shields";revision++;return;}}}

    private volatile String state="Connecting to browser capture…";
    private long sequence,revision;
    private static final class Chain {final List<NetworkRecord> hops=new ArrayList<>();final ArrayDeque<JSONObject> requests=new ArrayDeque<>(),responses=new ArrayDeque<>();NetworkRecord latest(){return hops.isEmpty()?null:hops.get(hops.size()-1);}}
    boolean connected(){return connected;}
    String state(){return state;}
    synchronized long revision(){return revision;}
    synchronized List<NetworkRecord> snapshots(){List<NetworkRecord> list=new ArrayList<>();for(int i=records.size()-1;i>=0;i--)list.add(records.get(i).copy());return list;}
    synchronized NetworkRecord get(long id){for(NetworkRecord r:records)if(r.id==id)return r.copy();return null;}
    synchronized void clear(){records.removeIf(r->!r.pinned);chains.clear();revision++;}
    void attach(String marker,Runnable ready){
        AtomicBoolean released=new AtomicBoolean();Runnable release=()->{if(released.compareAndSet(false,true))ready.run();};
        Thread connect=new Thread(()->{
            try{cdp.connect(marker,this::event,reason->{connected=false;state="Capture disconnected: "+reason;release.run();});
                cdp.command("Network.enable",json("maxTotalBufferSize",TOTAL_BODY_LIMIT,"maxResourceBufferSize",BODY_LIMIT,"maxPostDataSize",BODY_LIMIT),"",reply->{
                    if(reply.has("error")){state="Capture unavailable: "+reply.optJSONObject("error").optString("message");closeTransport();}
                    else {connected=true;state="Recording browser requests";cdp.command("Target.setAutoAttach",json("autoAttach",true,"waitForDebuggerOnStart",false,"flatten",true),"",null);}
                    release.run();
                });
            }catch(Exception e){state="Capture unavailable: "+e.getMessage();closeTransport();release.run();}
        },"Space-network-attach");connect.setDaemon(true);connect.start();
        // Never let a debugger/provider failure hold up normal browsing indefinitely.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(!released.get()){state="Capture timed out. Update Android System WebView and retry.";closeTransport();release.run();}},6500);
    }
    static JSONObject json(Object... pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put(String.valueOf(pairs[i]),pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return j;}
    private static void merge(Map<String,String> target,JSONObject source){if(source==null)return;Iterator<String> keys=source.keys();while(keys.hasNext()){String key=keys.next();target.put(key,source.optString(key));}}
    private synchronized void event(JSONObject event){
        if(closed||paused)return;String method=event.optString("method"),session=event.optString("sessionId");JSONObject p=event.optJSONObject("params");if(p==null)return;
        if(method.equals("Target.attachedToTarget")){String child=p.optString("sessionId");cdp.command("Network.enable",json("maxTotalBufferSize",TOTAL_BODY_LIMIT,"maxResourceBufferSize",BODY_LIMIT,"maxPostDataSize",BODY_LIMIT),child,null);cdp.command("Target.setAutoAttach",json("autoAttach",true,"waitForDebuggerOnStart",false,"flatten",true),child,null);return;}
        String requestId=p.optString("requestId");if(requestId.isEmpty())return;String key=session+"|"+requestId;
        Chain chain=chains.get(key);if(chain==null){if(chains.size()>RECORD_LIMIT*2)chains.remove(chains.keySet().iterator().next());chain=new Chain();chains.put(key,chain);}
        switch(method){
            case "Network.requestWillBeSent": {
                JSONObject request=p.optJSONObject("request");if(request==null)break;
                NetworkRecord previous=chain.latest();JSONObject redirect=p.optJSONObject("redirectResponse");
                if(previous!=null&&redirect!=null){response(previous,redirect);previous.redirect=true;previous.complete=true;previous.finished=p.optDouble("timestamp");previous.expectsExtra=p.optBoolean("redirectHasExtraInfo",false);previous.responseBodyNote="Redirect response body is not exposed by WebView. See the next request for the destination.";pump(chain);}
                NetworkRecord r=new NetworkRecord(++sequence,requestId,session);r.url=request.optString("url");r.method=request.optString("method","GET");r.type=p.optString("type","Other");r.started=p.optDouble("timestamp");r.time=(long)(p.optDouble("wallTime",System.currentTimeMillis()/1000d)*1000);merge(r.requestHeaders,request.optJSONObject("headers"));r.hasPostData=request.optBoolean("hasPostData",request.has("postData"));
                add(chain,r);
                if(request.has("postData"))setBody(r,true,request.optString("postData").getBytes(StandardCharsets.UTF_8),"");
                else if(r.hasPostData)cdp.command("Network.getRequestPostData",json("requestId",requestId),session,reply->{synchronized(this){if(!records.contains(r))return;JSONObject result=reply.optJSONObject("result");if(result!=null)setBody(r,true,result.optString("postData").getBytes(StandardCharsets.UTF_8),"Multipart file contents may be omitted by WebView.");else r.requestBodyNote="WebView did not expose this upload body.";revision++;}});
                break;
            }
            case "Network.requestWillBeSentExtraInfo":chain.requests.add(p);pump(chain);break;
            case "Network.responseReceivedExtraInfo":chain.responses.add(p);pump(chain);break;
            case "Network.responseReceived": {NetworkRecord r=chain.latest();if(r!=null){response(r,p.optJSONObject("response"));r.type=p.optString("type",r.type);r.expectsExtra=p.optBoolean("hasExtraInfo",false);pump(chain);}break;}
            case "Network.requestServedFromCache":if(chain.latest()!=null)chain.latest().fromCache=true;break;
            case "Network.loadingFinished": {NetworkRecord r=chain.latest();if(r!=null){r.complete=true;r.finished=p.optDouble("timestamp");r.transferBytes=(long)p.optDouble("encodedDataLength");fetchBody(r);}break;}
            case "Network.loadingFailed": {NetworkRecord r=chain.latest();if(r!=null){r.complete=true;r.finished=p.optDouble("timestamp");r.error=p.optString("errorText")+(p.has("blockedReason")?" · "+p.optString("blockedReason"):"");r.responseBodyNote="No response body: "+r.error;if(r.expectsExtra==null)r.expectsExtra=false;pump(chain);}break;}
            case "Network.webSocketCreated": {NetworkRecord r=new NetworkRecord(++sequence,requestId,session);r.url=p.optString("url");r.type="WebSocket";r.webSocket=true;r.responseBodyNote="WebSocket frames are shown below, not an HTTP response body.";add(chain,r);break;}
            case "Network.webSocketWillSendHandshakeRequest":if(chain.latest()!=null){merge(chain.latest().requestHeaders,p.optJSONObject("request")==null?null:p.optJSONObject("request").optJSONObject("headers"));}break;
            case "Network.webSocketHandshakeResponseReceived":if(chain.latest()!=null){response(chain.latest(),p.optJSONObject("response"));}break;
            case "Network.webSocketFrameSent":case "Network.webSocketFrameReceived": {NetworkRecord r=chain.latest();JSONObject frame=p.optJSONObject("response");if(r!=null&&frame!=null){String old=new String(r.responseBody,StandardCharsets.UTF_8);String next=old+(method.endsWith("Sent")?"SENT":"RECEIVED")+" opcode="+frame.optInt("opcode")+"\n"+frame.optString("payloadData")+"\n\n";setBody(r,false,next.getBytes(StandardCharsets.UTF_8),"WebSocket frames; binary-frame payloads are Base64. Capped at 2 MiB.");}break;}
            case "Network.webSocketClosed":if(chain.latest()!=null)chain.latest().complete=true;break;
            default:break;
        }
        revision++;
    }
    private void add(Chain chain,NetworkRecord r){chain.hops.add(r);records.add(r);trim();}
    private void trim(){while(records.size()>limit){int index=-1;for(int i=0;i<records.size();i++)if(!records.get(i).pinned){index=i;break;}if(index<0)index=0;NetworkRecord old=records.remove(index);String key=old.session+"|"+old.requestId;Chain c=chains.get(key);if(c!=null){c.hops.remove(old);if(c.hops.isEmpty())chains.remove(key);}}}
    private void response(NetworkRecord r,JSONObject response){if(response==null)return;r.finalUrl=response.optString("url",r.url);r.timing=response.optJSONObject("timing")==null?"":response.optJSONObject("timing").toString();r.status=response.optInt("status");r.statusText=response.optString("statusText");r.protocol=response.optString("protocol");r.mime=response.optString("mimeType");r.remoteAddress=response.optString("remoteIPAddress");if(response.has("remotePort"))r.remoteAddress+=":"+response.optInt("remotePort");r.fromCache=response.optBoolean("fromDiskCache")||response.optBoolean("fromServiceWorker")||response.optBoolean("fromPrefetchCache")||r.fromCache;merge(r.responseHeaders,response.optJSONObject("headers"));merge(r.requestHeaders,response.optJSONObject("requestHeaders"));r.requestHeadersText=response.optString("requestHeadersText",r.requestHeadersText);r.responseHeadersText=response.optString("headersText",r.responseHeadersText);}
    /** Extra-info events may arrive before the request/response and across reused redirect IDs. */
    private void pump(Chain c){for(NetworkRecord r:c.hops){if(r.expectsExtra==null)break;if(!r.expectsExtra)continue;if(!r.requestExtra&&!c.requests.isEmpty()){JSONObject extra=c.requests.removeFirst();merge(r.requestHeaders,extra.optJSONObject("headers"));r.requestExtra=true;}if(!r.responseExtra&&!c.responses.isEmpty()){JSONObject extra=c.responses.removeFirst();merge(r.responseHeaders,extra.optJSONObject("headers"));r.responseHeadersText=extra.optString("headersText",r.responseHeadersText);r.status=extra.optInt("statusCode",r.status);r.responseExtra=true;}}}
    private void fetchBody(NetworkRecord r){
        if(r.redirect||r.webSocket)return;
        cdp.command("Network.getResponseBody",json("requestId",r.requestId),r.session,reply->{synchronized(this){if(!records.contains(r)||closed)return;JSONObject result=reply.optJSONObject("result");
            if(result==null){r.responseBodyNote="Body unavailable: "+(reply.optJSONObject("error")==null?"Not retained by WebView":reply.optJSONObject("error").optString("message","Not retained by WebView"))+". The request was not replayed.";}
            else try{r.browserDecoded=true;String body=result.optString("body");byte[] bytes=result.optBoolean("base64Encoded")?java.util.Base64.getMimeDecoder().decode(body):body.getBytes(StandardCharsets.UTF_8);setBody(r,false,bytes,"Browser-decoded body; gzip/Brotli/other transfer compression is already removed by WebView.");}catch(IllegalArgumentException e){r.responseBodyNote="WebView returned an invalid Base64 body.";}
            revision++;
        }});
    }
    private void setBody(NetworkRecord record,boolean request,byte[] bytes,String note){
        if(bytes.length>BODY_LIMIT){bytes=Arrays.copyOf(bytes,BODY_LIMIT);note="Truncated: only the first 2 MiB were retained. "+note;}
        if(request){record.requestBody=bytes;record.requestBodyNote=note;}else{record.responseBody=bytes;record.responseBodyNote=note;}
        long total=0;for(NetworkRecord r:records)total+=r.requestBody.length+r.responseBody.length;
        for(NetworkRecord r:records){if(total<=TOTAL_BODY_LIMIT)break;if(r==record)continue;total-=r.requestBody.length+r.responseBody.length;if(r.requestBody.length>0){r.requestBody=new byte[0];r.requestBodyNote="Body evicted to keep capture within its 12 MiB memory budget.";}if(r.responseBody.length>0){r.responseBody=new byte[0];r.responseBodyNote="Body evicted to keep capture within its 12 MiB memory budget.";}}
    }
    private void closeTransport(){connected=false;cdp.close();}
    @Override public void close(){closed=true;connected=false;state="Capture stopped";cdp.close();}
}
