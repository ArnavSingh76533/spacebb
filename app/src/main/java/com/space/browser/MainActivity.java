package com.space.browser;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    static final int UPLOAD=101, SITE_PERMISSIONS=102;
    private static final int MAX_LIVE_TABS=4, MAX_TABS=30;
    private final ArrayList<Tab> tabs = new ArrayList<>();
    private Tab current;
    private BrowserStore store;
    private ShieldEngine shields;
    private LinearLayout root, chrome, toolbar, addressRow;
    private FrameLayout stage;
    private EditText address;
    private TextView tabCount;
    private ProgressBar progress;
    private View home, fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ValueCallback<Uri[]> uploadCallback;
    private PermissionRequest pendingPermission;
    private String[] pendingResources;
    private boolean dark, clearing;
    private int bg, panel, ink, muted, line, accent;
    private volatile Set<String> allowedSites=Collections.emptySet();
    private volatile boolean shieldsOn=true;
    private boolean captureEnabled;
    private byte[] pendingExport;
    private final ArrayList<NetworkRecorder> importedCaptures=new ArrayList<>();
    private final AtomicInteger totalBlocked = new AtomicInteger();
    private final Handler handler=new Handler(Looper.getMainLooper());

    static final class Tab {
        WebView web;
        String url="", title="New tab";
        volatile String site="";
        boolean desktop, reader;
        final AtomicInteger blocked=new AtomicInteger();
        NetworkRecorder network;
        NetworkInspector inspector;
        Tab inspected;
        boolean attaching,clearCaptureHistory;
    }
    protected boolean privateMode() { return false; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store=new BrowserStore(this); shields=new ShieldEngine(this);CaptureStorage.init(getCacheDir(),privateMode());
        totalBlocked.set(privateMode()?0:store.prefs.getInt("blocked",0));
        captureEnabled=store.prefs.getBoolean("dev_capture",true);
        WebView.setWebContentsDebuggingEnabled(captureEnabled);
        dark=store.prefs.getBoolean("dark",true);
        shieldsOn=store.prefs.getBoolean("shields",true);
        allowedSites=new HashSet<>(store.prefs.getStringSet("allowSites",Collections.emptySet()));
        if(privateMode()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        buildShell();
        if(!privateMode()) {
            for(BrowserStore.Entry e:store.entries("session")) { if(tabs.size()>=MAX_TABS)break; Tab t=new Tab(); t.url=e.url;t.title=e.title;tabs.add(t); }
        }
        if(tabs.isEmpty()) newTab(""); else switchTab(tabs.get(0));
        handleIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent);setIntent(intent);handleIntent(intent); }
    private void handleIntent(Intent intent) {
        if(intent!=null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData()!=null) {
            String url=intent.getData().toString(); if(BrowserLogic.webUrl(url)) { if(current!=null&&current.url.isEmpty())navigate(url);else newTab(url); }
        }
    }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private GradientDrawable surface(int color,int radius) { GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g; }
    private GradientDrawable outlined(int color,int radius) { GradientDrawable g=surface(color,radius);g.setStroke(dp(1),line);return g; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;
    }
    private void gap(LinearLayout l,int height) { l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private View button(String icon,String label,Runnable action) {
        FrameLayout f=new FrameLayout(this); f.setContentDescription(label);f.setFocusable(true);f.setClickable(true);
        android.util.TypedValue value=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,value,true);f.setBackgroundResource(value.resourceId);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);f.addView(new SpaceIcon(this,icon,ink),ip);
        f.setOnClickListener(v->action.run()); f.setOnLongClickListener(v->{toast(label);return true;}); return f;
    }
    private void palette() {
        bg=Color.parseColor(dark?"#0B0D14":"#F7F5FB");panel=Color.parseColor(dark?"#151822":"#FFFFFF");ink=Color.parseColor(dark?"#F5F2FF":"#201B32");
        muted=Color.parseColor(dark?"#9195AA":"#706A80");line=Color.parseColor(dark?"#272B3A":"#E6E1EF");accent=Color.parseColor(dark?"#B5A0FF":"#7050CD");
    }
    private void buildShell() {
        palette();if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=column();root.setBackgroundColor(bg);root.setFitsSystemWindows(true);setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return WindowInsets.CONSUMED;});
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        chrome=column();chrome.setPadding(dp(12),dp(6),dp(12),0);root.addView(chrome,new LinearLayout.LayoutParams(-1,-2));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        chrome.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));progress.setVisibility(View.INVISIBLE);
        addressRow=row();addressRow.setBackground(outlined(panel,20));
        LinearLayout.LayoutParams arp=new LinearLayout.LayoutParams(-1,dp(56));arp.topMargin=dp(6);chrome.addView(addressRow,arp);
        addressRow.addView(button("shield","Site shields",this::showShields),new LinearLayout.LayoutParams(dp(48),dp(48)));
        address=new EditText(this);address.setId(R.id.address_bar);address.setTextColor(ink);address.setHintTextColor(muted);address.setTextSize(15);address.setSingleLine(true);address.setSelectAllOnFocus(true);
        address.setHint("Search or enter address");address.setBackgroundColor(Color.TRANSPARENT);address.setPadding(0,0,0,0);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        addressRow.addView(address,new LinearLayout.LayoutParams(0,-1,1));
        addressRow.addView(button("refresh","Reload page",()->{if(current.web!=null&&!current.url.isEmpty())current.web.reload();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        address.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_GO||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_UP)){navigate(address.getText().toString());return true;}return false;});
        address.setOnFocusChangeListener((v,hasFocus)->{if(hasFocus&&current!=null)address.setText(current.url);else updateChrome();});
        toolbar=row();chrome.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(58)));
        toolbar.addView(button("back","Back",this::goBack),new LinearLayout.LayoutParams(0,dp(48),1));
        toolbar.addView(button("next","Forward",()->{if(current.web!=null&&current.web.canGoForward())current.web.goForward();}),new LinearLayout.LayoutParams(0,dp(48),1));
        toolbar.addView(button("home","New tab",()->newTab("")),new LinearLayout.LayoutParams(0,dp(48),1));
        FrameLayout tabButton=new FrameLayout(this);tabButton.setContentDescription("Tabs");tabButton.setFocusable(true);tabButton.setOnClickListener(v->showTabs());
        tabCount=text("1",13,ink,true);tabCount.setGravity(Gravity.CENTER);tabCount.setBackground(outlined(Color.TRANSPARENT,6));tabButton.addView(tabCount,new FrameLayout.LayoutParams(dp(24),dp(25),Gravity.CENTER));
        toolbar.addView(tabButton,new LinearLayout.LayoutParams(0,dp(48),1));toolbar.addView(button("menu","Browser menu",this::showMenu),new LinearLayout.LayoutParams(0,dp(48),1));
        stage.setFocusableInTouchMode(true);stage.requestFocus();
    }
    private void newTab(String url) {
        if(tabs.size()>=MAX_TABS){toast("Close a tab before opening more (30 tab limit).");return;}
        Tab t=new Tab();t.url=url;tabs.add(t);switchTab(t);
    }
    private void switchTab(Tab t) {
        if(current!=null&&current.web!=null&&t.inspected!=current)current.web.onPause();
        stage.removeAllViews();current=t;
        if(t.inspector!=null){if(t.inspector.getParent()!=null)((ViewGroup)t.inspector.getParent()).removeView(t.inspector);stage.addView(t.inspector,new FrameLayout.LayoutParams(-1,-1));if(t.inspected!=null&&t.inspected.web!=null)t.inspected.web.onResume();}else if(t.url.isEmpty())showHome();else{ensureWeb(t);stage.addView(t.web,new FrameLayout.LayoutParams(-1,-1));t.web.onResume();}
        trimTabs();updateChrome();saveSession();
    }
    private void trimTabs() {
        int live=0;for(Tab t:tabs)if(t.web!=null)live++;
        for(Tab t:tabs)if(live>MAX_LIVE_TABS&&t!=current&&(current==null||current.inspected!=t)&&t.web!=null){if(t.network!=null)t.network.close();t.web.destroy();t.web=null;live--;}
    }
    private void closeTab(Tab t) {
        boolean selected=t==current;if(t.inspector!=null)t.inspector.dispose();if(t.network!=null)t.network.dispose();if(t.web!=null){if(t.web.getParent()!=null)((android.view.ViewGroup)t.web.getParent()).removeView(t.web);t.web.destroy();}
        tabs.remove(t);if(tabs.isEmpty()){current=null;newTab("");}else if(selected)switchTab(tabs.get(tabs.size()-1));updateChrome();saveSession();
    }
    private void navigate(String input) {
        String url=BrowserLogic.resolve(input,store.prefs.getString("engine","DuckDuckGo"));if(url.isEmpty())return;
        if(current.inspector!=null){newTab(url);return;}
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);stage.requestFocus();
        current.url=url;current.reader=false;current.site=BrowserLogic.host(url);stage.removeAllViews();boolean created=current.web==null;ensureWeb(current);
        stage.addView(current.web,new FrameLayout.LayoutParams(-1,-1));current.web.onResume();if(!created&&!current.attaching)current.web.loadUrl(url);updateChrome();trimTabs();
    }
    private void updateChrome() {
        if(current==null||address==null)return;addressRow.setVisibility(current.inspector==null?View.VISIBLE:View.GONE);if(current.inspector!=null)progress.setVisibility(View.INVISIBLE);
        if(!address.hasFocus())address.setText(current.url.isEmpty()?"":current.url);
        tabCount.setText(String.valueOf(tabs.size()));address.setHint(privateMode()?"Private search or address":"Search or enter address");
    }
    private void showHome() {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout content=column();content.setPadding(dp(24),dp(14),dp(24),dp(20));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        LinearLayout heading=row();TextView brand=text("space",25,ink,true);brand.setLetterSpacing(-.04f);heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text(privateMode()?"●  PRIVATE":"✦  YOUR SPACE",10,accent,true);badge.setLetterSpacing(.12f);badge.setPadding(dp(12),dp(9),dp(12),dp(9));badge.setBackground(outlined(panel,20));heading.addView(badge);content.addView(heading);
        gap(content,18);
        FrameLayout hero=new FrameLayout(this);GradientDrawable heroBackground=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark?0xFF201B35:0xFFEDE7FA,bg});heroBackground.setCornerRadius(dp(22));hero.setBackground(heroBackground);hero.setClipToOutline(true);
        hero.addView(new OrbitView(this),new FrameLayout.LayoutParams(-1,dp(116),Gravity.TOP));
        LinearLayout heroText=column();heroText.setPadding(dp(12),dp(108),dp(12),dp(16));
        TextView eyebrow=text(privateMode()?"LEAVE LESS BEHIND":"A LITTLE LESS NOISE.",10,accent,true);eyebrow.setLetterSpacing(.2f);heroText.addView(eyebrow);gap(heroText,9);
        TextView title=text(privateMode()?"Just you.\nAnd the web.":"More space.\nMore possibility.",34,ink,true);title.setLetterSpacing(-.045f);title.setLineSpacing(dp(0),1.03f);heroText.addView(title);gap(heroText,10);
        heroText.addView(text(privateMode()?"Separate session. No saved history.":"A lighter browser for a curious mind.",14,muted,false));hero.addView(heroText,new FrameLayout.LayoutParams(-1,-2));content.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        gap(content,18);LinearLayout shortcutHeading=row();TextView quick=text("QUICK LAUNCH",10,muted,true);quick.setLetterSpacing(.15f);shortcutHeading.addView(quick,new LinearLayout.LayoutParams(0,-2,1));
        TextView edit=text("Bookmarks  ›",12,accent,true);edit.setPadding(dp(8),dp(12),0,dp(12));edit.setOnClickListener(v->showEntries("bookmarks"));shortcutHeading.addView(edit);content.addView(shortcutHeading);
        LinearLayout quickRow=row();String[][] shortcuts={{"G","Google","https://www.google.com","#A9BDFB"},{"▶","YouTube","https://m.youtube.com","#F59BA6"},{"W","Wikipedia","https://en.wikipedia.org","#D7D0EE"},{"⌘","GitHub","https://github.com","#A4D8C4"}};
        for(String[] s:shortcuts){LinearLayout item=column();item.setGravity(Gravity.CENTER);TextView tile=text(s[0],24,Color.parseColor(s[3]),true);tile.setGravity(Gravity.CENTER);tile.setBackground(outlined(panel,18));item.addView(tile,new LinearLayout.LayoutParams(dp(56),dp(56)));gap(item,8);item.addView(text(s[1],11,muted,false));item.setContentDescription("Open "+s[1]);item.setFocusable(true);item.setOnClickListener(v->navigate(s[2]));quickRow.addView(item,new LinearLayout.LayoutParams(0,dp(92),1));}content.addView(quickRow);
        gap(content,22);LinearLayout stats=row();stats.setPadding(dp(18),dp(17),dp(18),dp(17));stats.setBackground(outlined(panel,20));
        int blocked=totalBlocked.get();
        LinearLayout left=column();left.addView(text(String.format(java.util.Locale.US,"%,d",blocked),26,accent,true));gap(left,4);left.addView(text("Requests blocked",11,muted,false));stats.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        View divider=new View(this);divider.setBackgroundColor(line);stats.addView(divider,new LinearLayout.LayoutParams(dp(1),dp(40)));
        LinearLayout right=column();right.setPadding(dp(20),0,0,0);right.addView(text(shieldsOn?"Active":"Paused",22,ink,true));gap(right,6);right.addView(text("Space shields",11,muted,false));stats.addView(right,new LinearLayout.LayoutParams(0,-2,1));stats.setOnClickListener(v->showShields());content.addView(stats);
        gap(content,20);TextView footer=text(privateMode()?"Close private browsing from the menu to end this session.":"Less tracking. More exploring.",12,muted,false);footer.setGravity(Gravity.CENTER);content.addView(footer);
        home=scroll;stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));progress.setVisibility(View.INVISIBLE);
    }
    private int sessionBlocked(){int total=0;for(Tab t:tabs)total+=t.blocked.get();return total;}

    @SuppressWarnings("SetJavaScriptEnabled")
    private void ensureWeb(Tab t) {
        if(t.web!=null)return;
        WebView w=new WebView(this);t.web=w;w.setBackgroundColor(Color.WHITE);
        WebSettings s=w.getSettings();s.setJavaScriptEnabled(store.prefs.getBoolean("javascript",true));s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);
        s.setSupportZoom(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(true);
        s.setCacheMode(privateMode()?WebSettings.LOAD_NO_CACHE:WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w,false);
        setDesktop(t);
        w.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();
                if(BrowserLogic.webUrl(url))return false;
                if(request.isForMainFrame()&&request.hasGesture())openExternal(url);
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();
                boolean blocked=shieldsOn&&!allowedSites.contains(t.site)&&!request.isForMainFrame()&&shields.blocked(url);
                if(blocked&&t.network!=null)t.network.markBlocked(url);
                if(blocked){t.blocked.incrementAndGet();totalBlocked.incrementAndGet();return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
                return null;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){
                if(!BrowserLogic.webUrl(url))return;if(captureEnabled&&t.network==null)attachCapture(t,url,false);t.url=url;t.site=BrowserLogic.host(url);t.reader=false;if(t==current){progress.setVisibility(View.VISIBLE);updateChrome();}saveSession();
            }
            @Override public void onPageFinished(WebView view,String url){
                if(t.clearCaptureHistory&&BrowserLogic.webUrl(url)){view.clearHistory();t.clearCaptureHistory=false;}
                if(t==current){progress.setVisibility(View.INVISIBLE);updateChrome();}
                if(!privateMode()&&BrowserLogic.webUrl(url)&&!clearing)store.add("history",t.title,url,500);saveSession();
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler ssl,SslError error){ssl.cancel();if(t==current)toast("Connection blocked: this site's certificate is not valid.");}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()&&t==current)toast("Page could not load. Check the address or your connection.");}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                if(view.getParent()!=null)((ViewGroup)view.getParent()).removeView(view);if(t.network!=null)t.network.close();view.destroy();t.web=null;if(t==current){toast("This tab stopped. Reloading it now.");switchTab(t);}return true;
            }
        });
        w.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){if(t==current){progress.setProgress(value);progress.setVisibility(value==100?View.INVISIBLE:View.VISIBLE);}}
            @Override public void onReceivedTitle(WebView view,String title){t.title=title==null?"Page":title;saveSession();}
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,Message result){
                if(!gesture||tabs.size()>=MAX_TABS)return false;
                Tab popup=new Tab();tabs.add(popup);ensureWeb(popup);popup.url="about:blank";switchTab(popup);
                WebView.WebViewTransport transport=(WebView.WebViewTransport)result.obj;transport.setWebView(popup.web);result.sendToTarget();return true;
            }
            @Override public void onCloseWindow(WebView window){closeTab(t);}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(uploadCallback!=null)uploadCallback.onReceiveValue(null);uploadCallback=callback;
                try {Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");String[] types=params.getAcceptTypes();if(types.length>0&&!types[0].isEmpty())pick.putExtra(Intent.EXTRA_MIME_TYPES,types);pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);startActivityForResult(pick,UPLOAD);}
                catch(ActivityNotFoundException e){uploadCallback.onReceiveValue(null);uploadCallback=null;toast("No file picker is available.");}return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request){handler.post(()->askSitePermission(request));}
            @Override public void onPermissionRequestCanceled(PermissionRequest request){if(pendingPermission==request){pendingPermission=null;pendingResources=null;}}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){callback.invoke(origin,false,false);toast("Location access is disabled in Space.");}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(fullscreen!=null){callback.onCustomViewHidden();return;}fullscreen=view;fullscreenCallback=callback;
                ((FrameLayout)getWindow().getDecorView()).addView(view,new FrameLayout.LayoutParams(-1,-1));root.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override public void onHideCustomView(){exitFullscreen();}
        });
        w.setDownloadListener((url,ua,disposition,mime,length)->confirmDownload(url,ua,disposition,mime));
        w.setOnLongClickListener(v->{WebView.HitTestResult hit=w.getHitTestResult();String url=hit.getExtra();if(url!=null&&BrowserLogic.webUrl(url)){showLinkActions(url);return true;}return false;});
        if(BrowserLogic.webUrl(t.url)){if(captureEnabled){t.clearCaptureHistory=true;String marker="about:blank#space-"+UUID.randomUUID();w.loadUrl(marker);attachCapture(t,marker,true);}else w.loadUrl(t.url);}
    }
    private void attachCapture(Tab t,String marker,boolean navigateWhenReady){
        if(t.network==null)t.network=new NetworkRecorder();t.network.setLimit(store.prefs.getInt("dev_limit",2000));t.attaching=true;WebView target=t.web;
        t.network.attach(marker,()->handler.post(()->{if(isDestroyed()||t.web!=target||!tabs.contains(t))return;t.attaching=false;if(navigateWhenReady&&BrowserLogic.webUrl(t.url))target.loadUrl(t.url);}));
    }
    private void setDesktop(Tab t){if(t.web==null)return;String ua=WebSettings.getDefaultUserAgent(this);t.web.getSettings().setUserAgentString(t.desktop?ua.replace("; wv","").replace("Mobile ","").replaceAll("\\(Linux; Android [^)]+\\)","(X11; Linux x86_64)"):ua);}
    private void saveSession(){
        if(privateMode()||clearing||store==null)return;List<BrowserStore.Entry> list=new ArrayList<>();
        if(current!=null&&BrowserLogic.webUrl(current.url))list.add(new BrowserStore.Entry(current.title,current.url));
        for(Tab t:tabs)if(t!=current&&BrowserLogic.webUrl(t.url))list.add(new BrowserStore.Entry(t.title,t.url));store.write("session",list);
    }
    private void goBack(){if(current!=null&&current.inspector!=null){Tab inspected=current.inspected;closeTab(current);if(inspected!=null&&tabs.contains(inspected))switchTab(inspected);return;}if(fullscreen!=null){exitFullscreen();return;}if(current.web!=null&&current.web.canGoBack()){current.web.goBack();return;}if(!current.url.isEmpty()){current.url="";if(current.web!=null){current.web.stopLoading();if(current.network!=null)current.network.close();current.web.destroy();current.web=null;}switchTab(current);return;}if(privateMode())finishAndRemoveTask();else super.onBackPressed();}
    @Override public void onBackPressed(){goBack();}
    private void exitFullscreen(){if(fullscreen==null)return;((ViewGroup)fullscreen.getParent()).removeView(fullscreen);fullscreen=null;root.setVisibility(View.VISIBLE);if(fullscreenCallback!=null)fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    @Override protected void onPause(){super.onPause();saveSession();if(!privateMode())store.prefs.edit().putInt("blocked",totalBlocked.get()).apply();if(current!=null&&current.web!=null&&fullscreen==null)current.web.onPause();}
    @Override protected void onResume(){super.onResume();if(current!=null&&current.web!=null)current.web.onResume();}
    @Override protected void onDestroy(){if(uploadCallback!=null)uploadCallback.onReceiveValue(null);if(pendingPermission!=null)pendingPermission.deny();handler.removeCallbacksAndMessages(null);for(Tab t:tabs){if(t.inspector!=null)t.inspector.dispose();if(t.network!=null)t.network.dispose();if(t.web!=null)t.web.destroy();}for(NetworkRecorder r:importedCaptures)r.close();CaptureStorage.clearSession();super.onDestroy();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private Dialog sheet(String title,String subtitle,java.util.function.Consumer<LinearLayout> body){
        Dialog d=new Dialog(this);LinearLayout container=column();container.setPadding(dp(22),dp(14),dp(22),dp(24));container.setBackground(surface(panel,26));
        View handle=new View(this);handle.setBackground(surface(line,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(36),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(18);container.addView(handle,hp);
        LinearLayout heading=row();heading.addView(text(title,24,ink,true),new LinearLayout.LayoutParams(0,-2,1));heading.addView(button("close","Close panel",d::dismiss),new LinearLayout.LayoutParams(dp(48),dp(48)));container.addView(heading);
        if(subtitle!=null){TextView sub=text(subtitle,12,muted,false);sub.setPadding(0,0,0,dp(14));container.addView(sub);}
        ScrollView scroll=new ScrollView(this);LinearLayout contents=column();scroll.addView(contents);container.addView(scroll,new LinearLayout.LayoutParams(-1,-2));body.accept(contents);d.setContentView(container);
        Window win=d.getWindow();if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=win.getAttributes();a.width=-1;a.height=-2;a.gravity=Gravity.BOTTOM;a.dimAmount=.5f;win.setAttributes(a);win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        d.show(); if(win!=null) {win.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(620)),-2);container.post(()->{int limit=(int)(getResources().getDisplayMetrics().heightPixels*.82);if(container.getHeight()>limit)win.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(620)),limit);});}return d;
    }
    private void action(LinearLayout list,String icon,String title,String subtitle,Runnable run){
        LinearLayout r=row();r.setPadding(0,dp(8),0,dp(8));r.setMinimumHeight(dp(60));r.setFocusable(true);r.setContentDescription(title+(subtitle==null?"":", "+subtitle));
        FrameLayout bubble=new FrameLayout(this);bubble.setBackground(surface(bg,12));bubble.addView(new SpaceIcon(this,icon,accent),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));r.addView(bubble,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout labels=column();labels.setPadding(dp(14),0,dp(8),0);labels.addView(text(title,15,ink,true));if(subtitle!=null){TextView s=text(subtitle,11,muted,false);s.setMaxLines(2);labels.addView(s);}r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));r.addView(new SpaceIcon(this,"arrow",muted),new LinearLayout.LayoutParams(dp(16),dp(16)));r.setOnClickListener(v->run.run());list.addView(r,new LinearLayout.LayoutParams(-1,-2));
    }
    private void toggle(LinearLayout list,String title,String description,boolean value,java.util.function.Consumer<Boolean> change){
        LinearLayout r=row();r.setPadding(0,dp(12),0,dp(12));LinearLayout labels=column();labels.addView(text(title,15,ink,true));if(description!=null){TextView sub=text(description,11,muted,false);sub.setPadding(0,dp(4),dp(12),0);labels.addView(sub);}r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Switch s=new Switch(this);s.setContentDescription(title);s.setChecked(value);s.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));s.setOnCheckedChangeListener((v,on)->change.accept(on));r.addView(s);list.addView(r);
    }
    private void showTabs(){
        final Dialog[] dialog=new Dialog[1];dialog[0]=sheet(privateMode()?"Private tabs":"Your open spaces",tabs.size()+" tabs · up to 4 kept in memory",list->{
            for(Tab t:new ArrayList<>(tabs)){LinearLayout r=row();r.setPadding(dp(12),dp(8),dp(2),dp(8));r.setBackground(outlined(t==current?(dark?0xFF25203B:0xFFEFE8FF):bg,16));
                LinearLayout labels=column();TextView title=text(t.url.isEmpty()?"New tab":t.title,15,ink,true);title.setMaxLines(1);labels.addView(title);TextView url=text(t.url.isEmpty()?"Ready to explore":BrowserLogic.host(t.url)+(t.web==null?" · sleeping":""),11,muted,false);url.setMaxLines(1);labels.addView(url);r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));labels.setPadding(0,dp(9),0,dp(9));labels.setOnClickListener(v->{dialog[0].dismiss();switchTab(t);});r.addView(button("close","Close "+t.title,()->{dialog[0].dismiss();closeTab(t);showTabs();}),new LinearLayout.LayoutParams(dp(48),dp(48)));list.addView(r);gap(list,8);}
            action(list,"plus","New tab","A fresh place to start",()->{dialog[0].dismiss();newTab("");});
            if(tabs.size()>1)action(list,"close","Close all tabs",null,()->new AlertDialog.Builder(this).setTitle("Close all tabs?").setMessage("All open tabs in this session will close.").setNegativeButton("Cancel",null).setPositiveButton("Close tabs",(a,b)->{dialog[0].dismiss();destroyTabs();newTab("");}).show());
        });
    }
    private void showMenu(){
        final Dialog[] d=new Dialog[1];d[0]=sheet("Make it your space",privateMode()?"Private browsing session":"Space Browser",list->{
            action(list,"plus","New tab",null,()->{d[0].dismiss();newTab("");});
            action(list,"moon",privateMode()?"Close private browsing":"Private browsing",privateMode()?"End this separate session":"Separate cookies · no saved history",()->{d[0].dismiss();if(privateMode())finishAndRemoveTask();else startActivity(new Intent(this,PrivateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
            action(list,"star","Bookmarks",null,()->{d[0].dismiss();showEntries("bookmarks");});
            if(!privateMode())action(list,"history","History",null,()->{d[0].dismiss();showEntries("history");});
            action(list,"download","Downloads",null,()->{d[0].dismiss();try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(Exception e){toast("Open your device's Files app to see downloads.");}});
            if(current.web!=null&&!current.url.isEmpty()){
                action(list,"star","Bookmark this page",null,()->{d[0].dismiss();if(privateMode()){toast("Bookmarks are not saved in private browsing.");return;}store.add("bookmarks",current.title,current.url,300);toast("Bookmark saved");});
                action(list,"search","Find in page",null,()->{d[0].dismiss();showFind();});
                action(list,"reader",current.reader?"Exit reading view":"Reading view","Simplify article pages",()->{d[0].dismiss();readerMode();});
                action(list,"globe",current.desktop?"Switch to mobile site":"Request desktop site",null,()->{d[0].dismiss();current.desktop=!current.desktop;setDesktop(current);current.web.reload();});
                action(list,"share","Share page",null,()->{d[0].dismiss();startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,current.url),"Share page"));});
            }
            action(list,"code","Developer tools","Network · headers · bodies · decode",()->{d[0].dismiss();showDevTools();});
            action(list,"code","Space AI","Ask a question or analyze selected network traffic",()->{d[0].dismiss();new SpaceAi(this).show(Collections.emptyList(),false);});
            action(list,"settings","Settings","Appearance, search and privacy",()->{d[0].dismiss();showSettings();});
        });
    }
    private void showEntries(String key){
        boolean bookmarks=key.equals("bookmarks");List<BrowserStore.Entry> entries=store.entries(key);final Dialog[] d=new Dialog[1];
        d[0]=sheet(bookmarks?"Bookmarks":"Browsing history",bookmarks?"Good places, kept close":"Saved on this device · latest 500 pages",list->{
            if(entries.isEmpty()){gap(list,24);list.addView(text(bookmarks?"Your next favorite is out there.":"A fresh start. No history yet.",18,ink,true));gap(list,12);list.addView(text(bookmarks?"Open a page, then choose Bookmark this page from the menu.":"Pages you visit outside private browsing will appear here.",13,muted,false));gap(list,24);}
            for(BrowserStore.Entry e:entries){LinearLayout r=row();LinearLayout labels=column();labels.setPadding(0,dp(12),dp(8),dp(12));TextView title=text(e.title,14,ink,true);title.setMaxLines(2);labels.addView(title);TextView url=text(BrowserLogic.host(e.url),11,muted,false);labels.addView(url);labels.setOnClickListener(v->{d[0].dismiss();navigate(e.url);});r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));if(!privateMode())r.addView(button("close","Remove "+e.title,()->{store.remove(key,e.url);d[0].dismiss();showEntries(key);}),new LinearLayout.LayoutParams(dp(48),dp(48)));list.addView(r);}
        });
    }
    private void showShields(){
        String host=current.site;sheet("Space shields",host.isEmpty()?"A quieter web starts here":host,list->{
            LinearLayout stats=row();LinearLayout first=column();first.addView(text(String.valueOf(current.blocked.get()),32,accent,true));first.addView(text("Blocked in this tab",12,muted,false));stats.addView(first,new LinearLayout.LayoutParams(0,-2,1));LinearLayout second=column();second.addView(text(String.format(Locale.US,"%,d",shields.size()),25,ink,true));second.addView(text("Domain rules",12,muted,false));stats.addView(second);list.addView(stats);gap(list,16);
            toggle(list,"Block ads & trackers","Domain filters for network requests",shieldsOn,on->{shieldsOn=on;if(!privateMode())store.prefs.edit().putBoolean("shields",on).apply();if(current.web!=null)current.web.reload();});
            if(!host.isEmpty())toggle(list,"Allow ads on this site","Use if a site does not work correctly",allowedSites.contains(host),on->{Set<String> copy=new HashSet<>(allowedSites);if(on)copy.add(host);else copy.remove(host);allowedSites=copy;if(!privateMode())store.prefs.edit().putStringSet("allowSites",copy).apply();if(current.web!=null)current.web.reload();});
            gap(list,10);list.addView(text("Third-party cookies are blocked. Invalid certificates and mixed content are rejected. Domain filtering cannot remove every ad, including many video and first-party ads.",12,muted,false));
        });
    }
    private void showSettings(){
        final Dialog[] d=new Dialog[1];d[0]=sheet("Your preferences","Small details. Better browsing.",list->{
            toggle(list,"Dark appearance","A softer space after sunset",dark,on->{dark=on;if(!privateMode())store.prefs.edit().putBoolean("dark",on).apply();d[0].dismiss();if(current.web!=null&&current.web.getParent()!=null)((ViewGroup)current.web.getParent()).removeView(current.web);buildShell();switchTab(current);});
            action(list,"search","Search engine",store.prefs.getString("engine","DuckDuckGo"),()->{
                if(privateMode()){toast("Change your search engine in regular browsing.");return;}
                String[] engines={"DuckDuckGo","Brave","Google"};new AlertDialog.Builder(this).setTitle("Search with").setItems(engines,(dialog,which)->{store.prefs.edit().putString("engine",engines[which]).apply();d[0].dismiss();showSettings();}).show();
            });
            toggle(list,"JavaScript","Some websites need scripts to work",store.prefs.getBoolean("javascript",true),on->{if(!privateMode())store.prefs.edit().putBoolean("javascript",on).apply();for(Tab t:tabs)if(t.web!=null)t.web.getSettings().setJavaScriptEnabled(on);if(current.web!=null)current.web.reload();});
            toggle(list,"Network capture","Record traffic for developer tools; allows WebView debugging",captureEnabled,this::setCapture);
            action(list,"globe","Set as default browser","Open links in Space",()->{RoleManagerCompat.requestBrowser(this);});
            if(!privateMode())action(list,"shield","Clear browsing data","History, cookies, cache, open tabs and site storage",()->new AlertDialog.Builder(this).setTitle("Clear browsing data?").setMessage("This closes regular tabs and signs you out of websites. Bookmarks and downloaded files are kept. Close any private session separately.").setNegativeButton("Cancel",null).setPositiveButton("Clear data",(a,b)->{d[0].dismiss();clearBrowsingData();}).show());
            action(list,"code","About Space","Version 1.1.0 · native Android",()->new AlertDialog.Builder(this).setTitle("Space Browser 1.1.0").setMessage("Built with native Android views and your device's Android System WebView. No analytics SDKs, account or cloud sync.\n\nAndroid 10 or newer. Keep Android System WebView updated.\n\nFilters: StevenBlack unified hosts with bundled source attribution.\n\nNetwork tools capture real WebView events and available bodies. Body limits and unavailable data are labeled. AI sends messages to your configured gateway only when you press Send.").setPositiveButton("Got it",null).show());
        });
    }
    private void destroyTabs(){stage.removeAllViews();for(Tab t:tabs){if(t.inspector!=null)t.inspector.dispose();if(t.network!=null)t.network.dispose();if(t.web!=null){t.web.stopLoading();t.web.destroy();}}tabs.clear();current=null;}
    private void clearBrowsingData(){
        clearing=true;destroyTabs();importedCaptures.clear();CaptureStorage.clearSession();WebView cleaner=new WebView(this);cleaner.clearCache(true);cleaner.clearHistory();cleaner.clearFormData();cleaner.destroy();WebStorage.getInstance().deleteAllData();WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword();
        CookieManager.getInstance().removeAllCookies(done->{CookieManager.getInstance().flush();store.prefs.edit().remove("history").remove("session").apply();clearing=false;newTab("");toast("Browsing data cleared");});
    }
    private void showFind(){
        if(current.web==null){toast("Open a page first");return;}Tab target=current;
        Dialog d=sheet("Find in page",null,list->{EditText input=new EditText(this);input.setTextColor(ink);input.setSingleLine(true);input.setHint("Text to find");input.setHintTextColor(muted);list.addView(input);TextView result=text("Enter text to search this page",12,muted,false);list.addView(result);
            target.web.setFindListener((index,total,done)->{if(done)result.setText(total==0?"No matches":(index+1)+" of "+total+" matches");});
            input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(target.web!=null)target.web.findAllAsync(s.toString());}public void afterTextChanged(android.text.Editable s){}});
            LinearLayout controls=row();controls.addView(button("back","Previous match",()->{if(target.web!=null)target.web.findNext(false);}),new LinearLayout.LayoutParams(dp(52),dp(52)));controls.addView(button("next","Next match",()->{if(target.web!=null)target.web.findNext(true);}),new LinearLayout.LayoutParams(dp(52),dp(52)));list.addView(controls);
        });d.setOnDismissListener(v->{if(target.web!=null){target.web.clearMatches();target.web.setFindListener(null);}});
    }
    private void readerMode(){
        if(current.web==null)return;if(current.reader){current.web.reload();return;}
        current.web.evaluateJavascript("(()=>{const a=document.querySelector('article')||document.querySelector('main');if(!a||a.innerText.length<300)return 'No article found';const text=a.innerText;document.body.replaceChildren();const main=document.createElement('main');main.textContent=text;main.style.cssText='max-width:720px;margin:auto;padding:32px 24px;white-space:pre-wrap;font:20px/1.8 Georgia,serif';document.body.style.cssText='background:#f7f2e8;color:#262130;margin:0';document.body.appendChild(main);return 'Reading view enabled';})()",value->{current.reader=value.contains("enabled");toast(decodeJs(value));});
    }
    private String decodeJs(String value){try{Object obj=new JSONTokener(value).nextValue();return obj==JSONObject.NULL?"null":String.valueOf(obj);}catch(Exception e){return value;}}
    private List<NetworkRecorder> networkSources(){ArrayList<NetworkRecorder> result=new ArrayList<>(importedCaptures);for(Tab t:tabs)if(t.network!=null)result.add(t.network);return result;}
    private void showDevTools(){
        if(current.inspector!=null)return;
        if(current.web==null){toast("Open a website to use developer tools.");return;}
        if(current.network==null)current.network=new NetworkRecorder();
        showInspector(current.network,current);
    }
    private void showInspector(NetworkRecorder recorder,Tab target){
        if(store.prefs.getBoolean("dev_popup",false)){
            Dialog dialog=new Dialog(this);NetworkInspector inspector=new NetworkInspector(this,recorder,this::networkSources,dialog::dismiss);dialog.setContentView(inspector);dialog.setOnDismissListener(d->inspector.dispose());dialog.show();Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.88));w.setGravity(Gravity.BOTTOM);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(privateMode())w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
        }else{if(tabs.size()>=MAX_TABS){toast("Close a tab first");return;}Tab tools=new Tab();tools.title="Developer tools";tools.url="space://devtools";tools.inspected=target;tools.inspector=new NetworkInspector(this,recorder,this::networkSources,()->{closeTab(tools);if(target!=null&&tabs.contains(target))switchTab(target);});tabs.add(tools);switchTab(tools);}
    }
    android.content.SharedPreferences devPrefs(){return store.prefs;}
    void devToast(String message){toast(message);}
    void copyText(String text){copy(text);}
    void setCapture(boolean enabled){
        if(captureEnabled==enabled)return;captureEnabled=enabled;store.prefs.edit().putBoolean("dev_capture",enabled).apply();WebView.setWebContentsDebuggingEnabled(enabled);
        for(Tab t:tabs)if(t.web!=null){if(enabled)attachCapture(t,t.web.getUrl()==null?t.url:t.web.getUrl(),false);else if(t.network!=null)t.network.close();}
        toast(enabled?"Capture enabled. Reload the page for a complete trace.":"Capture disabled");
    }
    void saveDevFile(byte[] data,String name,String mime){pendingExport=data;try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name),201);}catch(Exception e){pendingExport=null;toast("No file picker available");}}
    void importDevHar(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),202);}catch(Exception e){toast("No file picker available");}}
    void replayDevRequest(NetworkRecord r,NetworkRecorder destination){
        if(!BrowserLogic.webUrl(r.url)){toast("Replay requires an HTTP or HTTPS URL");return;}
        new Thread(()->{java.net.HttpURLConnection c=null;try{r.type="Replay";r.started=android.os.SystemClock.elapsedRealtime()/1000d;c=(java.net.HttpURLConnection)new java.net.URL(r.url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod(r.method);for(Map.Entry<String,String> h:r.requestHeaders.entrySet())if(!h.getKey().startsWith(":")&&!h.getKey().equalsIgnoreCase("content-length")&&!h.getKey().equalsIgnoreCase("host")&&!h.getKey().equalsIgnoreCase("connection"))c.setRequestProperty(h.getKey(),h.getValue());if(r.requestBody.length>0){c.setDoOutput(true);c.setFixedLengthStreamingMode(r.requestBody.length);try(java.io.OutputStream out=c.getOutputStream()){out.write(r.requestBody);}}r.status=c.getResponseCode();r.statusText=c.getResponseMessage();r.finalUrl=c.getURL().toString();for(Map.Entry<String,List<String>> h:c.getHeaderFields().entrySet())if(h.getKey()!=null)r.responseHeaders.put(h.getKey(),String.join("\n",h.getValue()));r.mime=c.getContentType()==null?"":c.getContentType();try(java.io.InputStream in=r.status>=400?c.getErrorStream():c.getInputStream()){if(in!=null)r.responseBody=NetworkFormats.read(in,NetworkRecorder.BODY_LIMIT);}r.transferBytes=r.responseBody.length;r.responseBodyNote="Explicit replay response; redirects were not followed";}catch(Exception e){r.error=e.getMessage();r.responseBodyNote="Replay failed: "+e.getMessage();}finally{if(c!=null)c.disconnect();r.complete=true;r.finished=android.os.SystemClock.elapsedRealtime()/1000d;destination.addImported(r);handler.post(()->toast("Replay captured: "+r.status));}},"Space-replay").start();
    }
    private void copy(String value){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Space",value));toast("Copied");}
    private void showLinkActions(String url){final Dialog[] d=new Dialog[1];d[0]=sheet("Link options",url,list->{action(list,"plus","Open in new tab",null,()->{d[0].dismiss();newTab(url);});action(list,"share","Copy link",null,()->{d[0].dismiss();copy(url);});action(list,"download","Download link",null,()->{d[0].dismiss();confirmDownload(url,current.web.getSettings().getUserAgentString(),null,null);});});}
    private void openExternal(String value){
        Uri uri=Uri.parse(value);String scheme=uri.getScheme();if(!Arrays.asList("mailto","tel","sms","geo","market").contains(scheme)){toast("This link type is not supported.");return;}
        new AlertDialog.Builder(this).setTitle("Open another app?").setMessage(value).setNegativeButton("Cancel",null).setPositiveButton("Open",(d,w)->{try{Intent i=new Intent(scheme.equals("tel")?Intent.ACTION_DIAL:Intent.ACTION_VIEW,uri);i.addCategory(Intent.CATEGORY_BROWSABLE);startActivity(i);}catch(Exception e){toast("No app can open this link.");}}).show();
    }
    private void confirmDownload(String url,String ua,String disposition,String mime){
        if(!BrowserLogic.webUrl(url)){toast("Only HTTP and HTTPS downloads are supported. Blob downloads are not available.");return;}
        String name=URLUtil.guessFileName(url,disposition,mime).replaceAll("[\\\\/\\p{Cntrl}]","_");
        new AlertDialog.Builder(this).setTitle("Download file?").setMessage(name+"\n\nFrom "+BrowserLogic.host(url)+(privateMode()?"\n\nDownloads remain on your device after private browsing ends.":"")).setNegativeButton("Cancel",null).setPositiveButton("Download",(d,which)->{
            try{DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));request.setTitle(name);if(mime!=null)request.setMimeType(mime);request.addRequestHeader("User-Agent",ua);request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);toast("Download started");}catch(Exception e){toast("Download could not start: "+e.getMessage());}
        }).show();
    }
    private void askSitePermission(PermissionRequest request){
        if(pendingPermission!=null||current==null||!BrowserLogic.origin(request.getOrigin().toString()).equals(BrowserLogic.origin(current.url))){request.deny();return;}
        ArrayList<String> resources=new ArrayList<>();for(String r:request.getResources())if(r.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)||r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE))resources.add(r);
        if(resources.isEmpty()){request.deny();return;}
        pendingPermission=request;pendingResources=resources.toArray(new String[0]);String label=resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)?"camera":"";if(resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE))label+=(label.isEmpty()?"":" and ")+"microphone";
        new AlertDialog.Builder(this).setTitle("Allow "+label+"?").setMessage(request.getOrigin()+" requests access for this session.").setNegativeButton("Deny",(d,w)->denyPending()).setOnCancelListener(d->denyPending()).setPositiveButton("Allow",(d,w)->{
            if(pendingPermission!=request)return;ArrayList<String> permissions=new ArrayList<>();for(String r:pendingResources){String permission=r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)?Manifest.permission.CAMERA:Manifest.permission.RECORD_AUDIO;if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)permissions.add(permission);}
            if(permissions.isEmpty())grantPending();else requestPermissions(permissions.toArray(new String[0]),SITE_PERMISSIONS);
        }).show();
    }
    private void denyPending(){if(pendingPermission!=null)pendingPermission.deny();pendingPermission=null;pendingResources=null;}
    private void grantPending(){if(pendingPermission!=null){if(current!=null&&BrowserLogic.origin(pendingPermission.getOrigin().toString()).equals(BrowserLogic.origin(current.url)))pendingPermission.grant(pendingResources);else pendingPermission.deny();}pendingPermission=null;pendingResources=null;}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==SITE_PERMISSIONS){boolean all=results.length>0;for(int r:results)all&=r==PackageManager.PERMISSION_GRANTED;if(all)grantPending();else denyPending();}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==201){byte[] bytes=pendingExport;pendingExport=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){Uri uri=data.getData();new Thread(()->{try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException("Cannot open destination");out.write(bytes);handler.post(()->toast("Saved"));}catch(Exception e){handler.post(()->toast("Save failed: "+e.getMessage()));}},"Space-export").start();}return;}
        if(request==202){if(result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();new Thread(()->{try(java.io.InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException("Cannot open file");List<NetworkRecord> imported=NetworkFormats.importHar(new String(NetworkFormats.read(in,32*1024*1024),StandardCharsets.UTF_8));handler.post(()->{NetworkRecorder recorder=new NetworkRecorder();for(NetworkRecord r:imported)recorder.addImported(r);recorder.pause(true);importedCaptures.add(recorder);showInspector(recorder,null);});}catch(Exception e){handler.post(()->toast("HAR import failed: "+e.getMessage()));}},"Space-import").start();}return;}
        if(request==UPLOAD&&uploadCallback!=null){ArrayList<Uri> uris=new ArrayList<>();if(result==RESULT_OK&&data!=null){if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());}uploadCallback.onReceiveValue(uris.isEmpty()?null:uris.toArray(new Uri[0]));uploadCallback=null;}
    }
}
