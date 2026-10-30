package com.danitube.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VideoEntity::class, SuggestionEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun suggestionDao(): SuggestionDao

    companion object {
        /** Tags and channel per video, and the table of suggested YouTube videos. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN channel TEXT")
                db.execSQL("ALTER TABLE videos ADD COLUMN tags TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suggestions` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`channel` TEXT, `durationSec` INTEGER NOT NULL, `tags` TEXT NOT NULL, " +
                        "`score` REAL NOT NULL, `addedAt` INTEGER NOT NULL, `status` INTEGER NOT NULL, " +
                        "`playCount` INTEGER NOT NULL DEFAULT 0, `lastPlayedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /** Adds the play counters behind the Favoritos tab; existing videos start at 0. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE videos ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
