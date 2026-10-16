package com.example.smsbridge
import android.content.*
class BootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){Worker.schedule(c)}}
