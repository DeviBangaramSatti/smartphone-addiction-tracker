package com.smarttracker.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.smarttracker.data.local.entity.UsageLog
import com.smarttracker.data.local.entity.WebVisit
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FirestoreService — all Firebase Firestore read/write operations.
 *
 * ╔═══════════════════════════════════════════════════════════════════╗
 * ║  FIREBASE CLOUD STORAGE — COLLECTION STRUCTURE                  ║
 * ╠═══════════════════════════════════════════════════════════════════╣
 * ║  users/                                                           ║
 * ║  └── {uid}/                                                       ║
 * ║      ├── settings/preferences    ← cloud toggle, alert hours     ║
 * ║      ├── usage_logs/             ← app usage + web visit records ║
 * ║      │   ├── {date_pkgName}      ← "2024-01-15_com_instagram"    ║
 * ║      │   └── web_{date}_{hash}   ← web visit records             ║
 * ║      └── predictions/            ← ML addiction level results    ║
 * ║          └── {date}              ← "2024-01-15"                  ║
 * ╚═══════════════════════════════════════════════════════════════════╝
 *
 * SETUP (see README for full steps):
 *  1. Add google-services.json to android_app/app/
 *  2. Enable Email/Google sign-in in Firebase Console → Authentication
 *  3. Create Firestore DB in Firebase Console → Firestore Database
 *  4. Deploy security rules: firebase deploy --only firestore:rules
 */
@Singleton
class FirestoreService @Inject constructor() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val uid: String? get() = auth.currentUser?.uid

    // ── App Usage Logs ────────────────────────────────────────────────────────

    /**
     * Batch-pushes UsageLog records to Firestore.
     * Doc ID: "{date}_{pkg_with_underscores}" e.g. "2024-01-15_com_instagram_android"
     *
     * Called from: UsageRepository.syncToFirestore() ← FirebaseSyncWorker
     */
    suspend fun pushUsageLogs(logs: List<UsageLog>) {
        val userId = uid ?: return
        val batch  = db.batch()
        logs.forEach { log ->
            val docId = "${log.date}_${log.packageName.replace(".", "_")}"
            batch.set(
                userCollection(userId, "usage_logs").document(docId),
                log.toMap(),
                SetOptions.merge()
            )
        }
        batch.commit().await()
    }

    // ── Web Visits ────────────────────────────────────────────────────────────

    /**
     * Batch-pushes WebVisit records to Firestore.
     * Doc ID: "web_{date}_{urlHash}"
     *
     * Called from: UsageRepository.syncToFirestore() ← FirebaseSyncWorker
     */
    suspend fun pushWebVisits(visits: List<WebVisit>) {
        val userId = uid ?: return
        val batch  = db.batch()
        visits.forEach { visit ->
            val hash  = visit.url.hashCode().let { if (it < 0) "n${-it}" else "p$it" }
            val docId = "web_${visit.date}_$hash"
            batch.set(
                userCollection(userId, "usage_logs").document(docId),
                visit.toMap(),
                SetOptions.merge()
            )
        }
        batch.commit().await()
    }

    // ── ML Predictions ────────────────────────────────────────────────────────

    /**
     * Saves ML prediction to Firestore.
     * Path: users/{uid}/predictions/{date}
     *
     * Called from: UsageRepository.getPrediction() after FastAPI response
     */
    suspend fun savePrediction(date: String, level: String, confidence: Float) {
        val userId = uid ?: return
        userCollection(userId, "predictions")
            .document(date)
            .set(mapOf(
                "date"           to date,
                "addictionLevel" to level,
                "confidence"     to confidence,
                "timestamp"      to System.currentTimeMillis()
            ), SetOptions.merge())
            .await()
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    /**
     * Persists user preferences to Firestore.
     * Path: users/{uid}/settings/preferences
     *
     * Called from: SettingsViewModel on toggle/slider changes
     */
    suspend fun saveSettings(cloudEnabled: Boolean, alertThresholdHours: Int) {
        val userId = uid ?: return
        userCollection(userId, "settings")
            .document("preferences")
            .set(mapOf(
                "cloudSyncEnabled"    to cloudEnabled,
                "alertThresholdHours" to alertThresholdHours,
                "lastUpdated"         to System.currentTimeMillis()
            ), SetOptions.merge())
            .await()
    }

    /**
     * Fetches settings after sign-in to restore user preferences.
     * Called from: SettingsViewModel.signIn()
     */
    suspend fun fetchSettings(): Map<String, Any>? {
        val userId = uid ?: return null
        return userCollection(userId, "settings")
            .document("preferences")
            .get()
            .await()
            .data
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun isLoggedIn()     = auth.currentUser != null
    fun getCurrentUser() = auth.currentUser

    suspend fun signInWithEmail(email: String, password: String) =
        auth.signInWithEmailAndPassword(email, password).await()

    suspend fun signUp(email: String, password: String) =
        auth.createUserWithEmailAndPassword(email, password).await()

    fun signOut() = auth.signOut()

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun userCollection(uid: String, collection: String) =
        db.collection("users").document(uid).collection(collection)
}

// ── Firestore mappers (extension functions) ───────────────────────────────────

private fun UsageLog.toMap() = mapOf(
    "packageName"      to packageName,
    "appName"          to appName,
    "usageDurationMs"  to usageDurationMs,
    "launchCount"      to launchCount,
    "lastForegroundMs" to lastForegroundMs,
    "date"             to date,
    "type"             to "app"
)

private fun WebVisit.toMap() = mapOf(
    "url"         to url,
    "title"       to title,
    "timeSpentMs" to timeSpentMs,
    "visitedAt"   to visitedAt,
    "date"        to date,
    "type"        to "web"
)
