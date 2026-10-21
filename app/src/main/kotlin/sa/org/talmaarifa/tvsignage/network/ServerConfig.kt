package sa.org.talmaarifa.tvsignage.network

import android.content.Context
import sa.org.talmaarifa.tvsignage.BuildConfig

/**
 * The association portal's base URL (e.g. "https://portal.t-almaarifa.org.sa"). The documented API
 * contract (docs/tv-device-api.md) has no device-side discovery endpoint for this — a brand-new
 * device has no way to learn which server to even send `pair/request` to. This is a real gap against
 * the brief (see README "Gaps vs. the documented contract" §1): until the contract defines one (e.g.
 * a QR/NFC provisioning payload, or an MDM-pushed config), this app ships a compiled-in default
 * (`-PsignageBaseUrl=...` at build time) that an installer can override once, locally, before first
 * pairing — not a value the server ever sends the device.
 */
class ServerConfig(context: Context) {
    private val prefs = context.getSharedPreferences("server_config", Context.MODE_PRIVATE)

    fun baseUrl(): String = prefs.getString(KEY_BASE_URL, null) ?: BuildConfig.DEFAULT_BASE_URL

    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url.trim()).apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
    }
}
