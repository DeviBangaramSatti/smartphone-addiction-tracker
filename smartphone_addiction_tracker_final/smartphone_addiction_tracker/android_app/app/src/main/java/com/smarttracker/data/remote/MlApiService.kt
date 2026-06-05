package com.smarttracker.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * MlApiService — Retrofit interface for the FastAPI ML backend.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  INTEGRATION POINT                                      │
 * │  Base URL set in AppModule.kt → "https://YOUR_URL/"     │
 * │  Endpoint: POST /predict                                │
 * │  Called from: DashboardViewModel.fetchPrediction()      │
 * └─────────────────────────────────────────────────────────┘
 */
interface MlApiService {

    @POST("predict")
    suspend fun predict(@Body features: PredictRequest): PredictResponse
}

data class PredictRequest(
    val daily_usage_hours: Float,       // total screen time today
    val night_usage_hours: Float,       // usage between 10PM–6AM
    val app_switching_frequency: Int,   // times user switched apps
    val social_media_percentage: Float, // % of time on social apps
    val total_sessions: Int,            // total app opens
    val avg_session_duration_mins: Float
)

data class PredictResponse(
    val addiction_level: String,   // "Low" | "Medium" | "High"
    val confidence: Float          // 0.0 – 1.0
)
