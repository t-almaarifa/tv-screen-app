package sa.org.talmaarifa.tvsignage.network

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import sa.org.talmaarifa.tvsignage.BuildConfig
import sa.org.talmaarifa.tvsignage.data.credentials.CredentialStore
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit/OkHttp stack against the configured portal base URL (see [ServerConfig] — the
 * documented API contract has no device-side discovery mechanism for this, see README "Gaps vs. the
 * documented contract" §1, so it is a local, operator-set value).
 */
class NetworkModule(serverConfig: ServerConfig, credentialStore: CredentialStore) {

    // DTOs are annotated with @JsonClass(generateAdapter = true) (moshi-kotlin-codegen via KSP), so
    // Moshi discovers their generated adapters automatically — no reflection-based factory needed.
    val moshi: Moshi = Moshi.Builder().build()

    private val baseUrl: String = serverConfig.baseUrl().let { if (it.endsWith("/")) it else "$it/" }

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }

    private val pairingClient: OkHttpClient = baseClientBuilder().build()
    private val deviceClient: OkHttpClient = baseClientBuilder()
        .addInterceptor(AuthInterceptor(credentialStore))
        .build()

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val pairingApi: PairingApi = retrofit(pairingClient).create(PairingApi::class.java)
    val deviceApi: DeviceApi = retrofit(deviceClient).create(DeviceApi::class.java)
    val apiCaller: ApiCaller = ApiCaller(moshi)
}
