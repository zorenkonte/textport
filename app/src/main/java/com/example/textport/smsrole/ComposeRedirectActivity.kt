package com.example.textport.smsrole

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Minimal "compose message" target, declared only so Android will offer Textport
 * as a default SMS app. Textport is a backup tool, not a messenger, so this just
 * tells the user and closes.
 */
class ComposeRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(
            this,
            "Textport is a backup tool, not a messenger. Use your SMS app to send messages.",
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }
}
