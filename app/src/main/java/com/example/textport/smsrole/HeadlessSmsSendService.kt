package com.example.textport.smsrole

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Required for default-SMS-app eligibility (the "respond via message" /
 * quick-reply entry point). Textport doesn't send messages, so this is a no-op.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
