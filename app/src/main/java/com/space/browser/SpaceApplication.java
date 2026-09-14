package com.space.browser;

import android.app.Application;
import android.webkit.WebView;
import java.io.File;

public final class SpaceApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        if (Application.getProcessName().endsWith(":private")) {
            // Runs before any WebView API in this separate process, including after a crash.
            erase(new File(getDataDir(), "app_webview_private"));
            WebView.setDataDirectorySuffix("private");
        }
    }
    static void erase(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) erase(child);
        if (file.exists() && !file.delete()) android.util.Log.w("Space", "Private data cleanup will be retried next session");
    }
}
