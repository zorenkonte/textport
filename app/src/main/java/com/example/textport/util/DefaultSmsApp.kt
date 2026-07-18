package com.example.textport.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony

/**
 * Helpers for the temporary default-SMS-app flow that lets Textport read failed
 * SMS the system only exposes to the default handler.
 */
object DefaultSmsApp {

    /** True if Textport currently holds the default-SMS role. */
    fun isDefault(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /**
     * Intent that prompts the user to make Textport the default SMS app. Uses
     * [RoleManager] on API 29+, and the legacy change-default intent on 26–28.
     */
    fun requestIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            @Suppress("DEPRECATION")
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    /**
     * Intent to the system screen where the user re-selects their normal SMS app.
     * Android does not allow an app to hand the default role to another app, so
     * the switch-back is a manual step we can only deep-link to.
     */
    fun restoreDefaultsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
