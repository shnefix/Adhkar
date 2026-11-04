package com.azkar.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AzkarRoomMigrations {
    val V1_TO_V2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE zikrs ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE schedules ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Optional zikr title, shown above (or below) the zikr in the reader. */
    val V2_TO_V3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE zikrs ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE zikrs ADD COLUMN titleBelow INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Optional zikr source (hadith reference), shown on the back of the reader card. */
    val V3_TO_V4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE zikrs ADD COLUMN source TEXT NOT NULL DEFAULT ''")
        }
    }
}
