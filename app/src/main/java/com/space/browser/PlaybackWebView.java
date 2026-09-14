package com.space.browser;
import android.content.Context;
import android.webkit.WebView;
import android.view.View;

/** Keep an actively playing renderer visible to Chromium while the activity is minimized. */
final class PlaybackWebView extends WebView {
    private boolean playing;
    private int actualVisibility=View.VISIBLE;
    PlaybackWebView(Context context){super(context);}
    void keepPlaying(boolean value){playing=value;super.onWindowVisibilityChanged(value?View.VISIBLE:actualVisibility);}
    @Override protected void onWindowVisibilityChanged(int visibility){actualVisibility=visibility;super.onWindowVisibilityChanged(playing?View.VISIBLE:visibility);}
}
