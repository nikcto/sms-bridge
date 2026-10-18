package com.example.smsbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("cfg", 0)
        val configured = prefs.getString("allow", "89677357000,89273484600")!!.split(',').map { PhoneNumbers.normalize(it.trim()) }.filter { it.isNotEmpty() }.toSet()
        var accepted = 0
        var ignored = 0
        var lastSender = ""
        for (message in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val rawSender = message.originatingAddress ?: ""
            val sender = PhoneNumbers.normalize(rawSender)
            lastSender = sender
            val allowed = sender.isNotEmpty() && sender in configured
            Log.i(TAG, "Incoming SMS sender=${PhoneNumbers.masked(rawSender)} normalized=${PhoneNumbers.masked(sender)} allowed=$allowed configuredCount=${configured.size}")
            if (!allowed) { ignored++; continue }
            SmsStore(context).use { it.add(sender, message.messageBody, "${sender}_${message.timestampMillis}") }
            accepted++
        }
        if (accepted > 0) {
            EventLog.add(context, "Incoming SMS accepted: $accepted part(s) from ${PhoneNumbers.masked(lastSender)}")
            Worker.runNow(context)
        }
        if (ignored > 0) EventLog.add(context, "Incoming SMS ignored by filter (sender ${PhoneNumbers.masked(lastSender)})")
    }

    companion object { private const val TAG = "SmsBridge.SmsReceiver" }
}
