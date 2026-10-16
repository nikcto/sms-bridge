package com.example.smsbridge

import android.content.Context
import android.telephony.SmsManager
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
        client.newCall(b.build()).execute().use { it.isSuccessful }
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val p = applicationContext.getSharedPreferences("cfg", 0); val base = p.getString("url", "") ?: ""; val token = p.getString("token", "") ?: ""
        if (base.isBlank() || token.isBlank()) return@withContext Result.failure()
        try {
            val request = Request.Builder().url("$base/device/queue").header("X-Token", token).get().build()
            val messages = client.newCall(request).execute().use { if (!it.isSuccessful) return@withContext Result.retry(); JSONObject(it.body!!.string()).getJSONArray("messages") }
            val sms = SmsManager.getDefault()
            for (i in 0 until messages.length()) { val m = messages.getJSONObject(i); val id=m.getString("id")
                try { sms.sendMultipartTextMessage(m.getString("target"), null, sms.divideMessage(m.getString("text")), null, null); call("POST", "$base/device/ack", token, JSONObject().put("message_id",id).put("success",true).toString()) }
                catch (e: Exception) { call("POST", "$base/device/ack", token, JSONObject().put("message_id",id).put("success",false).put("error", "sms_failed").toString()) }
            }
            val db=SmsStore(applicationContext); for (item in db.all()) { if (call("POST", "$base/device/incoming", token, item.json)) db.delete(item.id) }; db.close()
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
    companion object { fun schedule(c:Context){WorkManager.getInstance(c).enqueueUniquePeriodicWork("poll",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<Worker>(15,TimeUnit.MINUTES).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build())}; fun runNow(c:Context){WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<Worker>().build())}; fun cancel(c:Context){WorkManager.getInstance(c).cancelUniqueWork("poll")} }
}
