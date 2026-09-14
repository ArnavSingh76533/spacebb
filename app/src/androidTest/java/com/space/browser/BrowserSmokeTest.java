package com.space.browser;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the real native activity and WebView against an offline HTTP fixture. */
public final class BrowserSmokeTest extends InstrumentationTestCase {
    private Activity activity;
    private ServerSocket server;
    private final AtomicReference<String> privateCookie = new AtomicReference<>();
    @Override protected void tearDown() throws Exception {
        if(server!=null)server.close();
        if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());
        super.tearDown();
    }
    public void testBrowserJourney() throws Exception {
        server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        new Thread(()->{while(!server.isClosed())try(Socket socket=server.accept()){
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));String first=in.readLine();String line;String cookie="";int length=0;while((line=in.readLine())!=null&&!line.isEmpty()){if(line.toLowerCase().startsWith("cookie:"))cookie=line;if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.split(":",2)[1].trim());}if(length>0){char[] body=new char[length];int offset=0;while(offset<length){int n=in.read(body,offset,length-offset);if(n<0)break;offset+=n;}}
            if(first!=null&&first.contains("/private"))privateCookie.set(cookie);
            String html="<!doctype html><html><head><title>Space test page</title><meta name='viewport' content='width=device-width'></head><body><article><h1>Space test page</h1><p>"+String.join("",java.util.Collections.nCopies(30,"A quieter web for curious minds. "))+"</p><a href='/next'>Next page</a><input type='file'><script>fetch('/api',{method:'POST',headers:{'Content-Type':'application/json','X-Space-Test':'yes'},body:JSON.stringify({message:'SPACE_POST_OK'})});fetch('/gzip');fetch('/redirect');fetch('/binary');</script></article></body></html>";
            String path=first==null?"/":first.split(" ")[1];String mime="text/html; charset=utf-8",extra="",status="200 OK";
            byte[] bytes=html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(path.equals("/api")||path.equals("/final")){mime="application/json";bytes="{\"message\":\"SPACE_RESPONSE_OK\",\"count\":3}".getBytes();}
            if(path.equals("/redirect")){status="302 Found";extra="Location: /final\r\n";bytes=new byte[0];}
            if(path.equals("/binary")){mime="application/octet-stream";bytes=new byte[]{0,1,2,3,65,66,67,(byte)255};}
            if(path.equals("/gzip")){mime="application/json";ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(buffer)){gzip.write("{\"compressed\":\"SPACE_GZIP_OK\"}".getBytes());}bytes=buffer.toByteArray();extra="Content-Encoding: gzip\r\n";}
            OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+"\r\nContent-Type: "+mime+"\r\nX-Space-Response: captured\r\n"+extra+"Content-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes());out.write(bytes);out.flush();
        }catch(IOException ignored){}},"test-http").start();
        activity=getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        assertNotNull(find(activity.getWindow().getDecorView(),"Browser menu"));
        screenshot("01-home-dark.png");
        java.lang.reflect.Field filters=MainActivity.class.getDeclaredField("shields");filters.setAccessible(true);
        ShieldEngine engine=(ShieldEngine)filters.get(activity);
        assertTrue("The bundled rules must block known ad domains",engine.blocked("https://doubleclick.net/banner.js"));
        assertFalse(engine.blocked("https://en.wikipedia.org/"));
        assertTrue("Full offline filter list must be present",engine.size()>50000);
        final EditText input=activity.findViewById(R.id.address_bar);assertNotNull(input);
        getInstrumentation().runOnMainSync(()->{input.setText("http://127.0.0.1:"+server.getLocalPort()+"/");input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_GO);});
        WebView web=null;
        for(int i=0;i<60;i++){Thread.sleep(200);AtomicReference<WebView> found=new AtomicReference<>();getInstrumentation().runOnMainSync(()->found.set(findWeb(activity.getWindow().getDecorView())));web=found.get();if(web!=null){AtomicReference<String> title=new AtomicReference<>();final WebView w=web;getInstrumentation().runOnMainSync(()->title.set(w.getTitle()));if("Space test page".equals(title.get()))break;}}
        assertNotNull(web);final WebView target=web;
        AtomicReference<String> title=new AtomicReference<>();getInstrumentation().runOnMainSync(()->title.set(target.getTitle()));assertEquals("Space test page",title.get());
        screenshot("02-browsing.png");
        java.lang.reflect.Field activeTab=MainActivity.class.getDeclaredField("current");activeTab.setAccessible(true);
        MainActivity.Tab browserTab=(MainActivity.Tab)activeTab.get(activity);
        NetworkRecorder recorder=browserTab.network;assertNotNull("Capture is enabled by default",recorder);
        for(int i=0;i<100;i++){boolean ready=false;for(NetworkRecord r:recorder.snapshots())if(r.url.endsWith("/gzip")&&r.responseBody.length>0)ready=true;if(ready)break;Thread.sleep(100);}
        assertTrue("CDP must attach to this process's actual WebView: "+recorder.state(),recorder.connected());
        NetworkRecord post=null,gzip=null,binary=null;boolean redirect=false,document=false;
        for(NetworkRecord r:recorder.snapshots()){if(r.url.endsWith("/api"))post=r;if(r.url.endsWith("/gzip"))gzip=r;if(r.url.endsWith("/binary"))binary=r;if(r.status==302)redirect=true;if(r.type.equals("Document"))document=true;}
        assertTrue("Initial document must be captured",document);assertNotNull("Fetch POST captured",post);assertEquals("POST",post.method);
        assertTrue(post.requestText().contains("SPACE_POST_OK"));assertTrue("Actual User-Agent must be available",NetworkFormats.header(post.requestHeaders,"User-Agent").contains("Mozilla"));
        assertEquals("yes",NetworkFormats.header(post.requestHeaders,"X-Space-Test"));assertEquals("captured",NetworkFormats.header(post.responseHeaders,"X-Space-Response"));
        assertTrue(new String(post.responseBody).contains("SPACE_RESPONSE_OK"));assertNotNull(gzip);assertTrue(new String(gzip.responseBody).contains("SPACE_GZIP_OK"));assertNotNull(binary);assertEquals(8,binary.responseBody.length);assertTrue("Redirect hop preserved",redirect);
        assertTrue(NetworkFormats.matches(post,"space_post_ok"));assertTrue(NetworkFormats.matches(gzip,"space_gzip_ok"));assertFalse(NetworkFormats.matches(post,"nonexistent_test_phrase"));
        String har=NetworkFormats.har(recorder.snapshots());java.util.List<NetworkRecord> imported=NetworkFormats.importHar(har);assertEquals(recorder.snapshots().size(),imported.size());
        String mask=NetworkFormats.masked("Authorization: Bearer secret\nCookie: session=secret\n{\"access_token\":\"secret\"}\nhttps://example.test/?api_key=secret");assertFalse(mask.contains("secret"));
        NetworkRecord decoder=new NetworkRecord(1,"decoder","");decoder.responseBody="eyJvayI6dHJ1ZX0=".getBytes();assertTrue(NetworkFormats.body(decoder,false,true,"Base64").contains("{\"ok\":true}"));
        NetworkRecorder storageTest=new NetworkRecorder();NetworkRecord large=new NetworkRecord(100,"large","");large.responseBody=new byte[100000];java.util.Arrays.fill(large.responseBody,(byte)'A');storageTest.addImported(large);NetworkRecord spilled=storageTest.get(100);assertNotNull("Large body spills to private cache",spilled.responseFile);assertEquals(0,spilled.responseBody.length);assertEquals(100000,spilled.responseBytes().length);storageTest.clear();assertFalse("Clear deletes unpinned spill files",spilled.responseFile.exists());storageTest.dispose();
        assertTrue(NetworkFormats.curl(post).contains("base64 --decode"));assertTrue(NetworkFormats.python(post).contains("base64.b64decode"));
        recorder.pin(post.id);recorder.clear();assertEquals(1,recorder.snapshots().size());assertTrue(recorder.snapshots().get(0).pinned);
        // Reload after exercising clear so screenshots show a complete, real trace.
        getInstrumentation().runOnMainSync(target::reload);Thread.sleep(1800);
        getInstrumentation().runOnMainSync(()->find(activity.getWindow().getDecorView(),"Tabs").performClick());
        getInstrumentation().waitForIdleSync();screenshot("03-tabs.png");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().runOnMainSync(()->find(activity.getWindow().getDecorView(),"Site shields").performClick());getInstrumentation().waitForIdleSync();screenshot("04-shields.png");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        // Directly invoke the menu action to inspect real session data in the native developer panel.
        getInstrumentation().runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("showDevTools");m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();Thread.sleep(500);screenshot("05-developer-tools.png");
        getInstrumentation().runOnMainSync(()->((EditText)find(activity.getWindow().getDecorView(),"Search network traffic")).setText("space_post_ok"));Thread.sleep(600);screenshot("10-network-search.png");
        getInstrumentation().runOnMainSync(()->{android.widget.ListView requests=findList(activity.getWindow().getDecorView());assertNotNull(requests);assertTrue("POSTs and matching HTML response appear",requests.getCount()>=3);int posts=0;for(int i=0;i<requests.getCount();i++){NetworkRecord match=((NetworkInspector.Item)requests.getAdapter().getItem(i)).record;String path=android.net.Uri.parse(match.url).getPath();assertTrue("Only fixture bodies containing SPACE_POST_OK match: "+path,path.equals("/api")||path.equals("/")||path.equals("/favicon.ico"));if(path.equals("/api"))posts++;}assertEquals("Both matching POST requests remain visible",2,posts);((EditText)find(activity.getWindow().getDecorView(),"Search network traffic")).setText("");});Thread.sleep(400);
        getInstrumentation().runOnMainSync(()->{android.widget.ListView requests=findList(activity.getWindow().getDecorView());assertNotNull(requests);assertTrue(requests.getCount()>0);int selected=0;for(int i=0;i<requests.getCount();i++)if(((NetworkInspector.Item)requests.getAdapter().getItem(i)).record.url.endsWith("/api")){selected=i;break;}requests.performItemClick(requests.getChildAt(0),selected,selected);});
        screenshot("07-network-overview.png");
        getInstrumentation().runOnMainSync(()->findText(activity.getWindow().getDecorView(),"Request").performClick());screenshot("08-network-request.png");
        getInstrumentation().runOnMainSync(()->{findText(activity.getWindow().getDecorView(),"Response").performClick();findText(activity.getWindow().getDecorView(),"Text").performClick();});screenshot("09-network-response.png");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        final NetworkRecord aiRecord=post;
        getInstrumentation().runOnMainSync(()->new SpaceAi((MainActivity)activity).show(java.util.Collections.singletonList(aiRecord),true));screenshot("11-space-ai.png");getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().getTargetContext().getSharedPreferences("space",0).edit().putBoolean("dev_popup",true).commit();
        getInstrumentation().runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("showDevTools");m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(300);screenshot("12-network-popup.png");getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().getTargetContext().getSharedPreferences("space",0).edit().putBoolean("dev_popup",false).commit();
        getInstrumentation().runOnMainSync(()->{
            assertFalse(target.getSettings().getAllowFileAccess());assertFalse(target.getSettings().getAllowContentAccess());
            assertFalse(android.webkit.CookieManager.getInstance().acceptThirdPartyCookies(target));
            assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW,target.getSettings().getMixedContentMode());
        });
        // Verify that the private process cannot see the regular session cookie.
        final java.util.concurrent.CountDownLatch cookieSet=new java.util.concurrent.CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->target.evaluateJavascript("document.cookie='space_session=normal; path=/'; document.cookie",value->cookieSet.countDown()));
        assertTrue("Cookie setup must complete while its tab is active",cookieSet.await(5,java.util.concurrent.TimeUnit.SECONDS));
        android.webkit.CookieManager.getInstance().flush();
        getInstrumentation().runOnMainSync(()->find(activity.getWindow().getDecorView(),"New tab").performClick());getInstrumentation().waitForIdleSync();
        assertNull(findWeb(activity.getWindow().getDecorView()));

        getInstrumentation().getTargetContext().startActivity(new Intent(getInstrumentation().getTargetContext(),PrivateActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("http://127.0.0.1:"+server.getLocalPort()+"/private")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<100&&privateCookie.get()==null;i++)Thread.sleep(200);
        assertNotNull("Private page should load",privateCookie.get());
        assertFalse("Regular cookies must not leak to private process",privateCookie.get().contains("space_session"));
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        Thread.sleep(300);
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        Thread.sleep(500);
        assertTrue(android.webkit.CookieManager.getInstance().getCookie("http://127.0.0.1:"+server.getLocalPort()).contains("space_session=normal"));
        getInstrumentation().getTargetContext().getSharedPreferences("space",0).edit().putBoolean("dark",false).commit();
        getInstrumentation().runOnMainSync(()->{try{
            java.lang.reflect.Field theme=MainActivity.class.getDeclaredField("dark");theme.setAccessible(true);theme.set(activity,false);
            java.lang.reflect.Method shell=MainActivity.class.getDeclaredMethod("buildShell");shell.setAccessible(true);shell.invoke(activity);
            java.lang.reflect.Field tab=MainActivity.class.getDeclaredField("current");tab.setAccessible(true);
            java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("switchTab",MainActivity.Tab.class);select.setAccessible(true);select.invoke(activity,tab.get(activity));
        }catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();screenshot("06-light.png");
    }
    private android.widget.ListView findList(View v){if(v instanceof android.widget.ListView)return (android.widget.ListView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){android.widget.ListView found=findList(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private View findText(View v,String text){if(v instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findText(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    private View find(View v,String description){if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),description);if(found!=null)return found;}return null;}
    private WebView findWeb(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=findWeb(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
    private void screenshot(String name) throws Exception {Thread.sleep(300);Bitmap image=null;for(int attempt=0;attempt<8&&image==null;attempt++){image=getInstrumentation().getUiAutomation().takeScreenshot();if(image==null)Thread.sleep(250);}assertNotNull("Screenshot must be available outside private browsing",image);File dir=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
