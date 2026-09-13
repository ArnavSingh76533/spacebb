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
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));String first=in.readLine();String line;String cookie="";while((line=in.readLine())!=null&&!line.isEmpty()){if(line.toLowerCase().startsWith("cookie:"))cookie=line;}
            if(first!=null&&first.contains("/private"))privateCookie.set(cookie);
            String html="<!doctype html><html><head><title>Space test page</title><meta name='viewport' content='width=device-width'></head><body><article><h1>Space test page</h1><p>"+String.join("",java.util.Collections.nCopies(30,"A quieter web for curious minds. "))+"</p><a href='/next'>Next page</a><input type='file'><script>console.log('SPACE_CONSOLE_OK');</script></article></body></html>";
            byte[] bytes=html.getBytes(java.nio.charset.StandardCharsets.UTF_8);OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes());out.write(bytes);out.flush();
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
        getInstrumentation().runOnMainSync(()->find(activity.getWindow().getDecorView(),"Tabs").performClick());
        getInstrumentation().waitForIdleSync();screenshot("03-tabs.png");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().runOnMainSync(()->find(activity.getWindow().getDecorView(),"Site shields").performClick());getInstrumentation().waitForIdleSync();screenshot("04-shields.png");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        // Directly invoke the menu action to inspect real session data in the native developer panel.
        getInstrumentation().runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("showDevTools");m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();screenshot("05-developer-tools.png");getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().runOnMainSync(()->{
            assertFalse(target.getSettings().getAllowFileAccess());assertFalse(target.getSettings().getAllowContentAccess());
            assertFalse(android.webkit.CookieManager.getInstance().acceptThirdPartyCookies(target));
            assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW,target.getSettings().getMixedContentMode());
        });
        getInstrumentation().runOnMainSync(()->find(activity.getWindow().getDecorView(),"New tab").performClick());getInstrumentation().waitForIdleSync();
        assertNull(findWeb(activity.getWindow().getDecorView()));
        // Verify that the private process cannot see the regular session cookie.
        final java.util.concurrent.CountDownLatch cookieSet=new java.util.concurrent.CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->target.evaluateJavascript("document.cookie='space_session=normal; path=/'; document.cookie",value->cookieSet.countDown()));
        assertTrue(cookieSet.await(5,java.util.concurrent.TimeUnit.SECONDS));
        android.webkit.CookieManager.getInstance().flush();
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
    private View find(View v,String description){if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),description);if(found!=null)return found;}return null;}
    private WebView findWeb(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=findWeb(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
    private void screenshot(String name) throws Exception {Thread.sleep(300);Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
