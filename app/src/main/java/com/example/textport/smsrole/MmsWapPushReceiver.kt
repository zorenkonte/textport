package com.example.textport.smsrole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Declared only so Textport is eligible as a default SMS app. Downloading an
 * incoming MMS requires a full messaging stack (carrier download over the data
 * network), which is out of scope for a backup tool. So this is intentionally a
 * no-op: MMS received while Textport is temporarily the default app is not
 * saved. This is documented for the user, who is expected to hold the role only
 * briefly and switch back.
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Incoming MMS ignored while Textport is default (backup tool, not a messenger).")
    }

    private companion object {
        private const val TAG = "MmsWapPushReceiver"
    }
}
