package com.onedi.OneDiMedia;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class LocalDatabase extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "onediMediaDB.db";
    private static final int DATABASE_VERSION = 2;

    // Constructor
    public LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create your database tables here
        // Example:
        Log.d("MyDatabaseHelper", "onCreate called");
        String newsHeadlineTableQuery = "CREATE TABLE IF NOT EXISTS news_headlines (id INTEGER PRIMARY KEY AUTOINCREMENT, news TEXT)";
        String mediaPlayHistoryTableQuery = "CREATE TABLE IF NOT EXISTS media_play_history (id INTEGER PRIMARY KEY AUTOINCREMENT, media_id INTEGER, media_name TEXT, created_on TEXT)";
        String mediaStoreTableQuery = "CREATE TABLE IF NOT EXISTS media_store (id INTEGER PRIMARY KEY AUTOINCREMENT, media_id INTEGER, media_name TEXT, created_on TEXT)";
        String mediaDownloadDataTableQuery = "CREATE TABLE IF NOT EXISTS media_download_history (id INTEGER PRIMARY KEY AUTOINCREMENT, media_id INTEGER, media_name TEXT, created_on TEXT, status INTEGER DEFAULT 1)";
        String DeviceOnOffTableQuery = "CREATE TABLE IF NOT EXISTS device_on_off_time (id INTEGER PRIMARY KEY AUTOINCREMENT, on_or_off INTEGER, created_on TEXT)";
        String GeneralParameterTableQuery = "CREATE TABLE IF NOT EXISTS general_parameter (id INTEGER PRIMARY KEY AUTOINCREMENT, parameter TEXT, value TEXT)";

        db.execSQL(newsHeadlineTableQuery);
        db.execSQL(mediaPlayHistoryTableQuery);
        db.execSQL(mediaStoreTableQuery);
        db.execSQL(mediaDownloadDataTableQuery);
        db.execSQL(DeviceOnOffTableQuery);
        db.execSQL(GeneralParameterTableQuery);

        ContentValues values = new ContentValues();
        values.put("parameter", "media_update_interval");
        values.put("value", "60");
        db.insert("general_parameter", null, values);
        values.clear();
        values.put("parameter", "news_update_interval");
        values.put("value", "60");
        db.insert("general_parameter", null, values);
        values.clear();
        values.put("parameter", "screen_volume_value");
        values.put("value", "0");
        db.insert("general_parameter", null, values);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade your database schema here
        db.execSQL("DROP TABLE IF EXISTS news_headlines;");
        db.execSQL("DROP TABLE IF EXISTS media_history;");
        db.execSQL("DROP TABLE IF EXISTS media_store;");
        db.execSQL("DROP TABLE IF EXISTS media_download_history;");
        db.execSQL("DROP TABLE IF EXISTS general_parameter;");

        onCreate(db);
    }

    public List<String> getAllGeneralParameter() {
        List<String> parameterValues = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + "general_parameter";

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex("value"));
                parameterValues.add(name);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return parameterValues;
    }

    public Cursor getRowById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                "news_headlines",
                new String[]{"id", "news"},
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null,
                null
        );
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;
    }

    public boolean isTableEmpty(String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + tableName;
        Cursor cursor = db.rawQuery(query, null);
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
            cursor.close();
        }
        return count == 0;
    }

    public boolean isRowExists(String tableName, Integer ID) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + tableName + " WHERE media_id = " + ID;
        Cursor cursor = db.rawQuery(query, null);
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
            cursor.close();
        }
        return count == 0;
    }

    public void updateNewsById(int id, String news) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("news", news);
        db.update("news_headlines", values, id + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void addMedia(String media_name, Integer media_id, String created_on) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("media_id", media_id);
        values.put("media_name", media_name);
        values.put("created_on", created_on);

        db.insert("media_store", null, values);
        db.close();
    }

    public void addDownloadedMediaDB(String media_name, Integer media_id, String created_on) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("media_id", media_id);
        values.put("media_name", media_name);
        values.put("created_on", created_on);

        db.insert("media_download_history", null, values);
        db.close();
    }

    public List<String> getAllMedia() {
        List<String> mediaList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + "media_store";

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex("media_name"));
                mediaList.add(name);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return mediaList;
    }

    public void clearAllMedia() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("media_store", null, null);
        db.close();
    }


    public int getIdFromValue(String tableName, String columnName, String value) {
        SQLiteDatabase db = getReadableDatabase();
        int id = -1; // default value if the value is not found
        String query = "SELECT media_id FROM " + tableName + " WHERE " + columnName + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{value});

        if (cursor.moveToFirst()) {
            id = cursor.getInt(0);
        }

        cursor.close();
        db.close();
        return id;
    }

}

