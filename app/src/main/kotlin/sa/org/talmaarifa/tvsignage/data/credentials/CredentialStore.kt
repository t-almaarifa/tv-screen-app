package sa.org.talmaarifa.tvsignage.data.credentials

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the device's permanent, revocable credential (`tv_<device_id>.<secret>`,
 * docs/tv-device-api.md §2) in an [EncryptedSharedPreferences] file — the secret must never sit in
 * plain SharedPreferences, a log line, or a crash report.
 */
class CredentialStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "tv_device_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _isPaired = MutableStateFlow(prefs.getString(KEY_CREDENTIAL, null) != null)
    /** Lets the UI (MainActivity) react immediately to a mid-session revocation without polling. */
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    fun credential(): String? = prefs.getString(KEY_CREDENTIAL, null)

    fun deviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun isPaired(): Boolean = credential() != null

    fun save(deviceId: String, credential: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_CREDENTIAL, credential)
            .apply()
        _isPaired.value = true
    }

    /** Called after a 401 UNAUTHENTICATED (revoked or invalid) — the device must re-pair from scratch. */
    fun clear() {
        prefs.edit().clear().apply()
        _isPaired.value = false
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CREDENTIAL = "credential"
    }
}
