package com.example.smsbridge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Worker(app: Context, params: WorkerParameters) : CoroutineWorker(app, params) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()


    private suspend fun call(method: String, url: String, token: String, body: String? = null) = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).header("X-Token", token)
        if (body != null) b.post(body.toRequestBody("application/json".toMediaType()))
        client.newCall(b.build()).execute().use {
            Log.d(TAG, "$method ${url.substringAfterLast('/')} -> HTTP ${it.code}")
            it.isSuccessful
        }
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val p = applicationContext.getSharedPreferences("cfg", 0); val base = p.getString("url", "") ?: ""; val token = p.getString("token", "") ?: ""
        if (base.isBlank() || token.isBlank()) {
            Log.e(TAG, "Configuration is incomplete: URL or token is empty")
            EventLog.add(applicationContext, "Configuration incomplete")
            return@withContext Result.failure()
        }
        Log.d(TAG, "Polling $base/device/queue")
        EventLog.add(applicationContext, "Checking server")
        try {
            val request = Request.Builder().url("$base/device/queue").header("X-Token", token).get().build()
            val messages = client.newCall(request).execute().use {
                Log.d(TAG, "GET device/queue -> HTTP ${it.code}")
                EventLog.add(applicationContext, "Server queue HTTP ${it.code}")
                if (!it.isSuccessful) return@withContext Result.retry()
                JSONObject(it.body!!.string()).getJSONArray("messages")
            }
            Log.d(TAG, "Server returned ${messages.length()} outgoing message(s)")
            EventLog.add(applicationContext, "Queue received: ${messages.length()} item(s)")
            val sms = SmsManager.getDefault()
            for (i in 0 until messages.length()) {
                val m = messages.getJSONObject(i)
                val id = m.getString("id")
                val parts = sms.divideMessage(m.getString("text"))
                try {
                    val sentIntents = ArrayList<PendingIntent>(parts.size)
                    parts.indices.forEach { part ->
                        val intent = Intent(applicationContext, SmsSentReceiver::class.java)
                            .putExtra(SmsSentReceiver.EXTRA_MESSAGE_ID, id)
                            .putExtra(SmsSentReceiver.EXTRA_PART_INDEX, part)
                            .putExtra(SmsSentReceiver.EXTRA_PART_COUNT, parts.size)
                            .putExtra(SmsSentReceiver.EXTRA_BASE_URL, base)
                            .putExtra(SmsSentReceiver.EXTRA_TOKEN, token)
                        sentIntents += PendingIntent.getBroadcast(
                            applicationContext,
                            (id.hashCode() * 31) + part,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                    Log.i(TAG, "Starting SMS id=$id, target=${m.getString("target")}, parts=${parts.size}")
                    EventLog.add(applicationContext, "Starting outgoing SMS (${parts.size} part(s))")
                    sms.sendMultipartTextMessage(m.getString("target"), null, parts, sentIntents, null)
                    Log.i(TAG, "SmsManager accepted SMS id=$id; waiting for SMS_SENT result")
                } catch (e: Exception) {
                    Log.e(TAG, "Could not start SMS id=$id", e)
                    EventLog.add(applicationContext, "SMS start failed")
                    call("POST", "$base/device/ack", token, JSONObject().put("message_id", id).put("success", false).put("error", "sms_failed").toString())
                }
            }
            val db = SmsStore(applicationContext)
            for (item in db.all()) {
                Log.d(TAG, "Sending incoming SMS id=${item.id} to server")
                EventLog.add(applicationContext, "Uploading incoming SMS")
                if (call("POST", "$base/device/incoming", token, item.json)) db.delete(item.id)
            }
            db.close()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Polling failed; WorkManager will retry", e)
            EventLog.add(applicationContext, "Network/poll error; retry scheduled")
            Result.retry()
        }
    }
    companion object {
        private const val TAG = "SmsBridge.Worker"
        fun schedule(c: Context) {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                "poll", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<Worker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()
            )
        }
        fun runNow(c: Context) { WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<Worker>().build()) }
        fun cancel(c: Context) { WorkManager.getInstance(c).cancelUniqueWork("poll") }
    }
}
