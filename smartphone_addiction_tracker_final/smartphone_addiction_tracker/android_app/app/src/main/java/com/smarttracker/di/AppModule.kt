package com.smarttracker.di

import android.content.Context
import androidx.room.Room
import com.smarttracker.data.local.AppDatabase
import com.smarttracker.data.local.dao.UsageLogDao
import com.smarttracker.data.local.dao.WebVisitDao
import com.smarttracker.data.remote.FirestoreService
import com.smarttracker.data.remote.MlApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * AppModule
 *
 * ┌──────────────────────────────────────────────────────────┐
 * │  CLOUD INTEGRATION POINTS                                │
 * │  • FirestoreService  → Firebase Firestore reads/writes   │
 * │  • MlApiService      → FastAPI /predict endpoint         │
 * └──────────────────────────────────────────────────────────┘
 *
 * Replace ML_API_BASE_URL with your deployed FastAPI URL.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Room DB ──────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "smarttracker.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideUsageLogDao(db: AppDatabase): UsageLogDao = db.usageLogDao()

    @Provides @Singleton
    fun provideWebVisitDao(db: AppDatabase): WebVisitDao = db.webVisitDao()

    // ── Firebase Firestore ───────────────────────────────────────────────────

    @Provides @Singleton
    fun provideFirestoreService(): FirestoreService = FirestoreService()

    // ── ML API (Retrofit) ────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    @Provides @Singleton
    fun provideMlApiService(client: OkHttpClient): MlApiService =
        Retrofit.Builder()
            .baseUrl("https://YOUR_FASTAPI_URL/")   // ← REPLACE with your deployed URL
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MlApiService::class.java)
}
