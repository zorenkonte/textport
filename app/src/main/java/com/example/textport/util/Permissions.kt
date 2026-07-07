package com.example.textport.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object Permissions {
    const val READ_SMS: String = Manifest.permission.READ_SMS

    /** Returns true if the READ_SMS runtime permission is currently granted. */
    fun hasReadSms(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, READ_SMS) == PackageManager.PERMISSION_GRANTED
}
