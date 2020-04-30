package com.programmazionemobile.progettoLucaBenzi

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

@Suppress("UNREACHABLE_CODE")
class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("Create Table $TABLE_NAME(ID INTEGER PRIMARY KEY AUTOINCREMENT,Category_img BLOB)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }
    fun insertData(Category_img: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put(CLASS_IMAGE, Category_img)
        val result = db.insert(TABLE_NAME, null, cv)

        return result.equals( -1).not()


    }
    @SuppressLint("Recycle")
    fun getData(): ByteArray {
        val db = writableDatabase
        val res = db.rawQuery("select * from $TABLE_NAME", null)

        if (res.moveToFirst()) {
            do {
                return res.getBlob(0)
            } while (res.moveToNext())
        }
        return byteArrayOf()
    }

    companion object {
        private const val DATABASE_NAME = "detection.db"
        private const val TABLE_NAME = "tbl_user"
        private const val CLASS_IMAGE = "Category_img"
    }

}