package com.gathin.tvhome;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * The tile on the stock launcher's app row. Google TV hides a second HOME activity, so this one is only
 * a leanback app: open it there and this launcher comes forward, and asks to be the default home when it
 * is not.
 */
public class SwitchHomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME));
                } catch (RuntimeException ignored) {
                    // This TV has no home-role screen; opening the launcher below is the switch.
                }
            }
        }
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
