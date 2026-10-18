package com.example.smsbridge

import android.app.Activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL) ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val part = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val count = intent.getIntExtra(EXTRA_PART_COUNT, -1)
        val result = resultCode
        val success = result == Activity.RESULT_OK

        Log.i(TAG, "SMS_SENT result=$result success=$success id=$messageId part=${part + 1}/$count")
        if (!success) {
            Log.e(TAG, "SMS part failed id=$messageId, Android result=$result")
            EventLog.add(context, "Outgoing SMS part failed: result $result")
        }

        // ACK only after all parts have reported. A small process-local counter is
        // sufficient for diagnostics; the server lease still handles retries.
        val prefs = context.getSharedPreferences("sms_results", Context.MODE_PRIVATE)
        val key = "parts_$messageId"
        val completed = prefs.getInt(key, 0) + 1
        val failed = !success || prefs.getBoolean("failed_$messageId", false)
        prefs.edit().putInt(key, completed).putBoolean("failed_$messageId", failed).apply()
        if (completed < count) return
        EventLog.add(context, if (!failed) "Outgoing SMS sent successfully" else "Outgoing SMS failed")
        prefs.edit().remove(key).remove("failed_$messageId").apply()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject()
                    .put("message_id", messageId)
                    .put("success", !failed)
                    .put("error", if (failed) "sms_sent_failed_$result" else JSONObject.NULL)
                    .toString()
                val request = Request.Builder()
                    .url("$baseUrl/device/ack")
                    .header("X-Token", token)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
                    .newCall(request).execute().use {
                        Log.d(TAG, "ACK id=$messageId success=${!failed} -> HTTP ${it.code}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Could not send SMS result ACK id=$messageId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsBridge.SmsSent"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_PART_COUNT = "part_count"
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_TOKEN = "token"
    }
}
