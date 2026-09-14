package com.space.browser;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.provider.Settings;
final class RoleManagerCompat {
    static void requestBrowser(Activity activity) {
        RoleManager roles=activity.getSystemService(RoleManager.class);
        if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_BROWSER)&&!roles.isRoleHeld(RoleManager.ROLE_BROWSER))activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER),201);
        else activity.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
    }
}
