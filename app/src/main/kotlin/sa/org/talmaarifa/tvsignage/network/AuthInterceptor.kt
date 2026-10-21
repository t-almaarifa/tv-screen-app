package sa.org.talmaarifa.tvsignage.network

import okhttp3.Interceptor
import okhttp3.Response
import sa.org.talmaarifa.tvsignage.data.credentials.CredentialStore

/**
 * Attaches `Authorization: Bearer tv_<device_id>.<secret>` (docs/tv-device-api.md "المصادقة") to
 * every request made through the authenticated [DeviceApi] client. Never applied to the pairing
 * client — those two endpoints are explicitly unauthenticated.
 */
class AuthInterceptor(private val credentialStore: CredentialStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val credential = credentialStore.credential()
            ?: return chain.proceed(chain.request()) // let it 401 naturally; callers react to that
        val authed = chain.request().newBuilder()
            .header("Authorization", "Bearer $credential")
            .build()
        return chain.proceed(authed)
    }
}
