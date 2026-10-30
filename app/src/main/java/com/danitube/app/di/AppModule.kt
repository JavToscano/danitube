package com.danitube.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.danitube.app.data.db.AppDatabase
import com.danitube.app.data.db.SuggestionDao
import com.danitube.app.data.db.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "danitube.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun suggestionDao(db: AppDatabase): SuggestionDao = db.suggestionDao()

    @Provides
    fun videoDao(db: AppDatabase): VideoDao = db.videoDao()

    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
