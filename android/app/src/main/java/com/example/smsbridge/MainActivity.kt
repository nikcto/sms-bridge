package com.example.smsbridge

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = getSharedPreferences("cfg", 0)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun field(hint: String) = EditText(this).apply { this.hint = hint }
        val url = field("Server HTTPS URL")
        val token = field("Device token")
        val phone = field("Main phone E.164")
        val allowed = field("Allowed numbers, comma-separated")
        url.setText(prefs.getString("url", "")); token.setText(prefs.getString("token", ""))
        phone.setText(prefs.getString("phone", "")); allowed.setText(prefs.getString("allow", ""))
        val status = TextView(this)
        val connect = Button(this).apply { text = "Connect" }
        val stop = Button(this).apply { text = "Stop" }
        val check = Button(this).apply { text = "Check connection" }
        listOf(url, token, phone, allowed, status, connect, stop, check).forEach { layout.addView(it) }
        setContentView(layout)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS), 9)
        connect.setOnClickListener {
            prefs.edit().putString("url", url.text.toString().trimEnd('/')).putString("token", token.text.toString()).putString("phone", phone.text.toString()).putString("allow", allowed.text.toString()).apply()
            Worker.schedule(this); status.text = "Connected"
        }
        stop.setOnClickListener { Worker.cancel(this); status.text = "Stopped" }
        check.setOnClickListener { Worker.runNow(this); status.text = "Check started" }
    }
}
