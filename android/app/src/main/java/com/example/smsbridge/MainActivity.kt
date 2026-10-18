package com.example.smsbridge

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    private lateinit var logView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable { override fun run() { refreshLog(); handler.postDelayed(this, 1000) } }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = getSharedPreferences("cfg", 0)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12) }
        fun field(hint: String) = EditText(this).apply { this.hint = hint }
        val url = field("Server HTTPS URL")
        val token = field("Device token")
        val phone = field("Main phone E.164")
        val allowed = field("Allowed numbers, comma-separated")
        url.setText(prefs.getString("url", "")); token.setText(prefs.getString("token", ""))
        phone.setText(prefs.getString("phone", "")); allowed.setText(prefs.getString("allow", ""))
        val status = TextView(this).apply { text = "Status: not connected"; textSize = 16f }
        val connect = Button(this).apply { text = "CONNECT" }
        val stop = Button(this).apply { text = "STOP" }
        val check = Button(this).apply { text = "CHECK CONNECTION" }
        val clear = Button(this).apply { text = "CLEAR LOG" }
        logView = TextView(this).apply { textSize = 12f; setTextIsSelectable(true); text = "No events yet" }
        val logScroll = ScrollView(this).apply { addView(logView) }
        layout.addView(url); layout.addView(token); layout.addView(phone); layout.addView(allowed)
        layout.addView(status); layout.addView(connect); layout.addView(stop); layout.addView(check); layout.addView(clear)
        layout.addView(TextView(this).apply { text = "Events (message text is not stored):"; textSize = 15f })
        layout.addView(logScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS), 9)
        EventLog.add(this, "Application opened")
        connect.setOnClickListener {
            prefs.edit().putString("url", url.text.toString().trimEnd('/')).putString("token", token.text.toString()).putString("phone", phone.text.toString()).putString("allow", allowed.text.toString()).apply()
            Worker.schedule(this); status.text = "Status: connected"; EventLog.add(this, "Polling scheduled")
        }
        stop.setOnClickListener { Worker.cancel(this); status.text = "Status: stopped"; EventLog.add(this, "Polling stopped") }
        check.setOnClickListener { Worker.runNow(this); status.text = "Status: checking"; EventLog.add(this, "Manual connection check started") }
        clear.setOnClickListener { EventLog.clear(this); refreshLog() }
        refreshLog()
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    private fun refreshLog() { if (::logView.isInitialized) { logView.text = EventLog.read(this).ifBlank { "No events yet" }; logView.post { logView.parent?.let { (it as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN) } } } }
}
