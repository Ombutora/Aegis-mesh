package com.aegismesh.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.aegismesh.models.Emergency;

import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite persistence helper for managing emergency alerts that could not
 * be immediately sent to the remote backend or mesh network.
 */
public class EmergencyDbHelper extends SQLiteOpenHelper {
    private static final String TAG = "EmergencyDbHelper";
    private static final String DATABASE_NAME = "aegis_mesh.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_EMERGENCIES = "emergencies";
    public static final String COLUMN_ID = "emergency_id";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_TRIGGER_TYPE = "trigger_type";
    public static final String COLUMN_EMERGENCY_TYPE = "emergency_type";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_STATUS = "status";

    private static final String CREATE_TABLE_EMERGENCIES = "CREATE TABLE " + TABLE_EMERGENCIES + " ("
            + COLUMN_ID + " TEXT PRIMARY KEY, "
            + COLUMN_USER_ID + " TEXT, "
            + COLUMN_TRIGGER_TYPE + " TEXT, "
            + COLUMN_EMERGENCY_TYPE + " TEXT, "
            + COLUMN_LATITUDE + " REAL, "
            + COLUMN_LONGITUDE + " REAL, "
            + COLUMN_TIMESTAMP + " INTEGER, "
            + COLUMN_STATUS + " TEXT"
            + ");";

    private static EmergencyDbHelper instance;

    public static synchronized EmergencyDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new EmergencyDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private EmergencyDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_EMERGENCIES);
        Log.i(TAG, "Local emergencies table created.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EMERGENCIES);
        onCreate(db);
    }

    /**
     * Inserts a new emergency alert or updates its fields (like status) if it already exists.
     */
    public synchronized void insertOrUpdate(Emergency emergency) {
        if (emergency == null) return;
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_ID, emergency.getEmergencyId());
            values.put(COLUMN_USER_ID, emergency.getUserId());
            values.put(COLUMN_TRIGGER_TYPE, emergency.getTriggerType());
            values.put(COLUMN_EMERGENCY_TYPE, emergency.getEmergencyType());
            values.put(COLUMN_LATITUDE, emergency.getLatitude());
            values.put(COLUMN_LONGITUDE, emergency.getLongitude());
            values.put(COLUMN_TIMESTAMP, emergency.getTimestamp());
            values.put(COLUMN_STATUS, emergency.getStatus());

            long result = db.insertWithOnConflict(
                    TABLE_EMERGENCIES,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
            if (result == -1) {
                Log.e(TAG, "Failed to insert/update emergency ID: " + emergency.getEmergencyId());
            } else {
                Log.d(TAG, "Successfully persisted emergency ID: " + emergency.getEmergencyId() + " with status: " + emergency.getStatus());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception saving emergency locally: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all emergencies stored locally that are either PENDING or FAILED.
     */
    public synchronized List<Emergency> getUnsentEmergencies() {
        List<Emergency> list = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = getReadableDatabase();
            String selection = COLUMN_STATUS + " = ? OR " + COLUMN_STATUS + " = ?";
            String[] selectionArgs = new String[]{Emergency.STATUS_PENDING, Emergency.STATUS_FAILED};

            cursor = db.query(
                    TABLE_EMERGENCIES,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    COLUMN_TIMESTAMP + " ASC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Emergency em = new Emergency();
                    em.setEmergencyId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                    em.setUserId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                    em.setTriggerType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRIGGER_TYPE)));
                    em.setEmergencyType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMERGENCY_TYPE)));
                    em.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)));
                    em.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE)));
                    em.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                    em.setStatus(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS)));
                    list.add(em);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception retrieving unsent emergencies: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /**
     * Updates the status of a persisted emergency.
     */
    public synchronized void updateStatus(String emergencyId, String status) {
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_STATUS, status);

            int rows = db.update(
                    TABLE_EMERGENCIES,
                    values,
                    COLUMN_ID + " = ?",
                    new String[]{emergencyId}
            );
            Log.d(TAG, "Updated status of emergency " + emergencyId + " to " + status + ". Rows affected: " + rows);
        } catch (Exception e) {
            Log.e(TAG, "Exception updating status: " + e.getMessage(), e);
        }
    }
}
