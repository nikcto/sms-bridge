package com.example.smsbridge

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EventLog {
    private const val PREFS = "event_log"
    private const val KEY = "entries"
    private const val MAX = 150

    @Synchronized
    fun add(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamp = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date())
        val old = prefs.getString(KEY, "")!!.split("\n").filter { it.isNotBlank() }.takeLast(MAX - 1)
        prefs.edit().putString(KEY, (old + "$stamp  $message").joinToString("\n")).apply()
    }

    fun read(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply() }
}
