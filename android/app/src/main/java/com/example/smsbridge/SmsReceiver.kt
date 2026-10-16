package com.example.smsbridge
import android.content.*
import android.provider.Telephony
class SmsReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val p=c.getSharedPreferences("cfg",0);val allowed=p.getString("allow","")!!.split(',').map{it.trim()}.filter{it.isNotEmpty()};for(m in Telephony.Sms.Intents.getMessagesFromIntent(i)){val sender=m.originatingAddress?:continue;if(allowed.none{sender==it||sender.replace(" ","")==it.replace(" ","")})continue;SmsStore(c).use{it.add(sender,m.messageBody,"${sender}_${m.timestampMillis}")};Worker.runNow(c)}}}
