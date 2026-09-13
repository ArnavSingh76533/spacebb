package com.space.browser;

public final class PrivateActivity extends MainActivity {
    @Override protected boolean privateMode() { return true; }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            SpaceApplication.erase(new java.io.File(getDataDir(), "app_webview_private"));
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
