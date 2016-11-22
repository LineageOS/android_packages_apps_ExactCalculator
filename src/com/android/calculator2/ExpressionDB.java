/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// FIXME: We need to rethink the error handling here. Do we want to revert to history-less
// operation if something goes wrong with the database?
// TODO: This tries to ensure strong thread-safety, i.e. each call looks atomic, both to
// other threads and other database users. Is this useful?
// TODO: Especially if we notice serious performance issues on rotation in the history
// view, we may need to use a CursorLoader or some other scheme to preserve the database
// across rotations.

package com.android.calculator2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.util.Log;

public class ExpressionDB {
    /* Table contents */
    public static class ExpressionEntry implements BaseColumns {
        public static final String TABLE_NAME = "expressions";
        public static final String COLUMN_NAME_EXPRESSION = "expression";
        public static final String COLUMN_NAME_FLAGS = "flags";
        // Time stamp as returned by currentTimeMillis().
        public static final String COLUMN_NAME_TIMESTAMP = "timeStamp";
    }

    /* Data to be written to or read from a row in the table */
    public static class RowData {
        private static final int DEGREE_MODE = 2;
        private static final int LONG_TIMEOUT = 1;
        public final byte[] mExpression;
        public final int mFlags;
        public long mTimeStamp;  // 0 ==> this and next field to be filled in when written.
        private static int flagsFromDegreeAndTimeout(Boolean DegreeMode, Boolean LongTimeout) {
            return (DegreeMode ? DEGREE_MODE : 0) | (LongTimeout ? LONG_TIMEOUT : 0);
        }
        private boolean degreeModeFromFlags(int flags) {
            return (flags & DEGREE_MODE) != 0;
        }
        private boolean longTimeoutFromFlags(int flags) {
            return (flags & LONG_TIMEOUT) != 0;
        }
        private static final int MILLIS_IN_15_MINS = 15 * 60 * 1000;
        private RowData(byte[] expr, int flags, long timeStamp) {
            mExpression = expr;
            mFlags = flags;
            mTimeStamp = timeStamp;
        }
        /**
         * More client-friendly constructor that hides implementation ugliness.
         * utcOffset here is uncompressed, in milliseconds.
         * A zero timestamp will cause it to be automatically filled in.
         */
        public RowData(byte[] expr, boolean degreeMode, boolean longTimeout, long timeStamp) {
            this(expr, flagsFromDegreeAndTimeout(degreeMode, longTimeout), timeStamp);
        }
        public boolean degreeMode() {
            return degreeModeFromFlags(mFlags);
        }
        public boolean longTimeout() {
            return longTimeoutFromFlags(mFlags);
        }
        /**
         * Return a ContentValues object representing the current data.
         */
        public ContentValues toContentValues() {
            ContentValues cvs = new ContentValues();
            cvs.put(ExpressionEntry.COLUMN_NAME_EXPRESSION, mExpression);
            cvs.put(ExpressionEntry.COLUMN_NAME_FLAGS, mFlags);
            if (mTimeStamp == 0) {
                mTimeStamp = System.currentTimeMillis();
            }
            cvs.put(ExpressionEntry.COLUMN_NAME_TIMESTAMP, mTimeStamp);
            return cvs;
        }
    }

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + ExpressionEntry.TABLE_NAME + " (" +
            ExpressionEntry._ID + " INTEGER PRIMARY KEY," +
            ExpressionEntry.COLUMN_NAME_EXPRESSION + " BLOB," +
            ExpressionEntry.COLUMN_NAME_FLAGS + " INTEGER," +
            ExpressionEntry.COLUMN_NAME_TIMESTAMP + " INTEGER)";
    private static final String SQL_DROP_TABLE =
            "DROP TABLE IF EXISTS " + ExpressionEntry.TABLE_NAME;
    private static final String SQL_GET_MIN = "SELECT MIN(" + ExpressionEntry._ID +
            ") FROM " + ExpressionEntry.TABLE_NAME;
    private static final String SQL_GET_MAX = "SELECT MAX(" + ExpressionEntry._ID +
            ") FROM " + ExpressionEntry.TABLE_NAME;
    private static final String SQL_GET_ROW = "SELECT * FROM " + ExpressionEntry.TABLE_NAME +
            " WHERE " + ExpressionEntry._ID + " = ?";
    // We may eventually need an index by timestamp. We don't use it yet.
    private static final String SQL_CREATE_TIMESTAMP_INDEX =
            "CREATE INDEX timestamp_index ON " + ExpressionEntry.TABLE_NAME + "(" +
            ExpressionEntry.COLUMN_NAME_TIMESTAMP + ")";
    private static final String SQL_DROP_TIMESTAMP_INDEX = "DROP INDEX IF EXISTS timestamp_index";

    private class ExpressionDBHelper extends SQLiteOpenHelper {
        // If you change the database schema, you must increment the database version.
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "Expressions.db";

        public ExpressionDBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_ENTRIES);
            db.execSQL(SQL_CREATE_TIMESTAMP_INDEX);
        }
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // For now just throw away history on database version upgrade/downgrade.
            db.execSQL(SQL_DROP_TIMESTAMP_INDEX);
            db.execSQL(SQL_DROP_TABLE);
            onCreate(db);
        }
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }

    private ExpressionDBHelper mExpressionDBHelper;

    private SQLiteDatabase mExpressionDB;  // Constant after initialization.

    private boolean mBadDB = false;      // Database initialization failed.

    // Never allocate new negative indicees (row ids) >= MAXIMUM_MIN_INDEX.
    public static final long MAXIMUM_MIN_INDEX = -10;

    // Minimum index value in DB.
    private long mMinIndex;
    // Maximum index value in DB.
    private long mMaxIndex;
    // mMinIndex and mMaxIndex are correct.
    private boolean mMinMaxValid;

    // mLock protects mExpressionDB and mBadDB, though we access mExpressionDB without
    // synchronization after it's known to be initialized.  Used to wait for database
    // initialization. Also protects mMinIndex, mMaxIndex, and mMinMaxValid.
    private Object mLock = new Object();

    public ExpressionDB(Context context) {
        mExpressionDBHelper = new ExpressionDBHelper(context);
        AsyncInitializer initializer = new AsyncInitializer();
        initializer.execute(mExpressionDBHelper);
    }

    private boolean getBadDB() {
        synchronized(mLock) {
            return mBadDB;
        }
    }

    private void setBadDB() {
        synchronized(mLock) {
            mBadDB = true;
        }
    }

    /**
     * Set mExpressionDB and compute minimum and maximum indices in the background.
     */
    private class AsyncInitializer extends AsyncTask<ExpressionDBHelper, Void, SQLiteDatabase> {
        @Override
        protected SQLiteDatabase doInBackground(ExpressionDBHelper... helper) {
            SQLiteDatabase result;
            try {
                result = helper[0].getWritableDatabase();
                // We notify here, since there are unlikely cases in which the UI thread
                // may be blocked on us, preventing onPostExecute from running.
                synchronized(mLock) {
                    mExpressionDB = result;
                    mLock.notifyAll();
                }
                long min, max;
                try (Cursor minResult = result.rawQuery(SQL_GET_MIN, null)) {
                    if (!minResult.moveToFirst()) {
                        // Empty database.
                        min = MAXIMUM_MIN_INDEX;
                    } else {
                        min = Math.min(minResult.getLong(0), MAXIMUM_MIN_INDEX);
                    }
                }
                try (Cursor maxResult = result.rawQuery(SQL_GET_MAX, null)) {
                    if (!maxResult.moveToFirst()) {
                        // Empty database.
                        max = 0L;
                    } else {
                        max = Math.max(maxResult.getLong(0), 0L);
                    }
                }
                synchronized(mLock) {
                    mMinIndex = min;
                    mMaxIndex = max;
                    mMinMaxValid = true;
                    mLock.notifyAll();
                }
            } catch(SQLiteException e) {
                Log.e("Calculator", "Database initialization failed.\n", e);
                synchronized(mLock) {
                    mBadDB = true;
                    mLock.notifyAll();
                }
                return null;
            }
            return result;
        }

        @Override
        protected void onPostExecute(SQLiteDatabase result) {
            if (result == null) {
                throw new AssertionError("Failed to open history DB");
                // TODO: Should we try to run without persistent history instead?
            } // else doInBackground already set expressionDB.
        }
        // On cancellation we do nothing;
    }

    /**
     * Wait until expression DB is ready.
     * This should usually be a no-op, since we set up the DB on creation. But there are a few
     * cases, such as restarting the calculator in history mode, when we currently can't do
     * anything but wait, possibly even in the UI thread.
     */
    private void waitForExpressionDB() {
        synchronized(mLock) {
            while (mExpressionDB == null && !mBadDB) {
                try {
                    mLock.wait();
                } catch(InterruptedException e) {
                    mBadDB = true;
                }
            }
            if (mBadDB) {
                throw new AssertionError("Failed to open history DB");
            }
        }
    }

    /**
     * Wait until the minimum key has been computed.
     */
    private void waitForMinMaxValid() {
        synchronized(mLock) {
            while (!mMinMaxValid && !mBadDB) {
                try {
                    mLock.wait();
                } catch(InterruptedException e) {
                    mBadDB = true;
                }
            }
            if (mBadDB) {
                throw new AssertionError("Failed to compute minimum key");
            }
        }
    }

    /**
     * Erase ALL database entries.
     * This is currently only safe if expressions that may refer to them are also erased.
     * Should only be called when concurrent references to the database are impossible.
     * TODO: Look at ways to more selectively clear the database.
     */
    public void eraseAll() {
        waitForExpressionDB();
        mExpressionDB.execSQL(SQL_DROP_TIMESTAMP_INDEX);
        mExpressionDB.execSQL(SQL_DROP_TABLE);
        try {
            mExpressionDB.execSQL("VACUUM");
        } catch(Exception e) {
            Log.v("Calculator", "Database VACUUM failed\n", e);
            // Should only happen with concurrent execution, which should be impossible.
        }
        mExpressionDB.execSQL(SQL_CREATE_ENTRIES);
        mExpressionDB.execSQL(SQL_CREATE_TIMESTAMP_INDEX);
        synchronized(mLock) {
            mMinIndex = MAXIMUM_MIN_INDEX;
            mMaxIndex = 0L;
        }
    }

    /**
     * Add a row with index outside existing range.
     * The returned index will be larger than any existing index unless negative_index is true.
     * In that case it will be smaller than any existing index and smaller than MAXIMUM_MIN_INDEX.
     */
    public long addRow(boolean negative_index, RowData data) {
        long result;
        long newIndex;
        waitForMinMaxValid();
        synchronized(mLock) {
            if (negative_index) {
                newIndex = mMinIndex - 1;
                mMinIndex = newIndex;
            } else {
                newIndex = mMaxIndex + 1;
                mMaxIndex = newIndex;
            }
            ContentValues cvs = data.toContentValues();
            cvs.put(ExpressionEntry._ID, newIndex);
            result = mExpressionDB.insert(ExpressionEntry.TABLE_NAME, null, cvs);
        }
        if (result != newIndex) {
            throw new AssertionError("Expected row id " + newIndex + ", got " + result);
        }
        return result;
    }

    /**
     * Retrieve the row with the given index.
     * Such a row must exist.
     */
    public RowData getRow(long index) {
        RowData result;
        waitForExpressionDB();
        String args[] = new String[] { Long.toString(index) };
        Cursor resultC = mExpressionDB.rawQuery(SQL_GET_ROW, args);
        if (!resultC.moveToFirst()) {
            throw new AssertionError("Missing Row");
        } else {
            result = new RowData(resultC.getBlob(1), resultC.getInt(2) /* flags */,
                    resultC.getLong(3) /* timestamp */);
        }
        return result;
    }

    public long getMinIndex() {
        waitForMinMaxValid();
        synchronized(mLock) {
            return mMinIndex;
        }
    }

    public long getMaxIndex() {
        waitForMinMaxValid();
        synchronized(mLock) {
            return mMaxIndex;
        }
    }

    public void close() {
        mExpressionDBHelper.close();
    }

}
