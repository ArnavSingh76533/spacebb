package com.space.browser;

import android.app.AlertDialog;
import android.view.WindowManager;

/** Private inspection dialogs must protect their own window as well as the activity. */
final class SpaceDialogBuilder extends AlertDialog.Builder {
    private final MainActivity activity;
    SpaceDialogBuilder(MainActivity activity){super(activity);this.activity=activity;}
    @Override public AlertDialog create(){AlertDialog dialog=super.create();if(activity.privateMode()&&dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);return dialog;}
}
