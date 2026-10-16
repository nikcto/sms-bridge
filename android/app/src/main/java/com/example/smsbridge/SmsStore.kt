package com.example.smsbridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

data class Pending(val id: String, val json: String)

class SmsStore(context: Context) : SQLiteOpenHelper(context, "sms_queue.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE incoming(id TEXT PRIMARY KEY, sender TEXT, text TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun add(sender: String, text: String, id: String) {
        val values = ContentValues().apply { put("id", id); put("sender", sender); put("text", text) }
        writableDatabase.insertWithOnConflict("incoming", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun all(): List<Pending> {
        val result = mutableListOf<Pending>()
        readableDatabase.rawQuery("SELECT id,sender,text FROM incoming", null).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Pending(cursor.getString(0), JSONObject().put("sender", cursor.getString(1)).put("text", cursor.getString(2)).put("external_id", cursor.getString(0)).toString()))
            }
        }
        return result
    }
    fun delete(id: String) { writableDatabase.delete("incoming", "id=?", arrayOf(id)) }
}
