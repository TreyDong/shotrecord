package com.banana.shotrecord.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 录音记录的本地存储。之后接入云同步时,可在此增加同步状态字段。 */
class RecordStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "shotrecord.db", null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE records(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                transcript TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(filePath: String, durationMs: Long, createdAt: Long): Record {
        val values = ContentValues().apply {
            put("file_path", filePath)
            put("duration_ms", durationMs)
            put("created_at", createdAt)
        }
        val id = writableDatabase.insert("records", null, values)
        return Record(id, filePath, durationMs, createdAt, null)
    }

    fun getAll(): List<Record> {
        val out = mutableListOf<Record>()
        readableDatabase.rawQuery(
            "SELECT id, file_path, duration_ms, created_at, transcript FROM records ORDER BY created_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += Record(
                    id = c.getLong(0),
                    filePath = c.getString(1),
                    durationMs = c.getLong(2),
                    createdAt = c.getLong(3),
                    transcript = if (c.isNull(4)) null else c.getString(4),
                )
            }
        }
        return out
    }

    fun delete(id: Long) {
        writableDatabase.delete("records", "id = ?", arrayOf(id.toString()))
    }

    /** 供后续转写功能回填文字 */
    fun updateTranscript(id: Long, text: String) {
        val values = ContentValues().apply { put("transcript", text) }
        writableDatabase.update("records", values, "id = ?", arrayOf(id.toString()))
    }

    companion object {
        private const val DB_VERSION = 1
    }
}
