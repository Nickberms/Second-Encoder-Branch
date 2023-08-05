package om.sstvencoder;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MyDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "encoder_database.db";
    private static final int DATABASE_VERSION = 1;

    public static class MyEntry implements BaseColumns {
        public static final String TABLE_NAME = "encoded_data";
        public static final String COLUMN_IMAGE = "image_data";
        public static final String COLUMN_TEXT = "encoded_text";
        public static final String COLUMN_TIMESTAMP = "timestamp";
    }

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + MyEntry.TABLE_NAME + " (" +
                    MyEntry._ID + " INTEGER PRIMARY KEY," +
                    MyEntry.COLUMN_IMAGE + " BLOB," +
                    MyEntry.COLUMN_TEXT + " TEXT," +
                    MyEntry.COLUMN_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")";
    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + MyEntry.TABLE_NAME;

    public MyDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d("MyDatabaseHelper", "Database onCreate() called");
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d("MyDatabaseHelper", "Database onUpgrade() called");
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public List<Entry> getAllEntries() {
        List<Entry> entryList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String[] projection = {
                MyEntry._ID,
                MyEntry.COLUMN_IMAGE,
                MyEntry.COLUMN_TEXT,
                MyEntry.COLUMN_TIMESTAMP
        };
        Cursor cursor = db.query(
                MyEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null
        );
        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MyEntry._ID));
                byte[] imageBytes = cursor.getBlob(cursor.getColumnIndexOrThrow(MyEntry.COLUMN_IMAGE));
                String text = cursor.getString(cursor.getColumnIndexOrThrow(MyEntry.COLUMN_TEXT));
                String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(MyEntry.COLUMN_TIMESTAMP));
                Bitmap imageBitmap = null;
                if (imageBytes != null && imageBytes.length > 0) {
                    imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                }
                Entry entry = new Entry(id, imageBitmap, text, timestamp);
                entryList.add(entry);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return entryList;
    }
}