package com.example.smsbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("cfg", 0)
        val allowed = prefs.getString("allow", "")!!.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        var accepted = 0
        for (message in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val sender = message.originatingAddress ?: continue
            if (allowed.none { sender == it || sender.replace(" ", "") == it.replace(" ", "") }) continue
            SmsStore(context).use { it.add(sender, message.messageBody, "${sender}_${message.timestampMillis}") }
            accepted++
        }
        if (accepted > 0) {
            EventLog.add(context, "Incoming SMS accepted: $accepted part(s)")
            Worker.runNow(context)
        } else {
            EventLog.add(context, "Incoming SMS ignored by number filter")
        }
    }
}
