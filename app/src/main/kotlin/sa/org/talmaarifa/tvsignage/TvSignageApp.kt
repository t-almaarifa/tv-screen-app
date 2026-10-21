package sa.org.talmaarifa.tvsignage

import android.app.Application
import sa.org.talmaarifa.tvsignage.data.credentials.CredentialStore
import sa.org.talmaarifa.tvsignage.data.db.AppDatabase
import sa.org.talmaarifa.tvsignage.data.repo.EventLogRepository
import sa.org.talmaarifa.tvsignage.data.repo.PairingRepository
import sa.org.talmaarifa.tvsignage.data.repo.SyncRepository
import sa.org.talmaarifa.tvsignage.network.NetworkModule
import sa.org.talmaarifa.tvsignage.network.ServerConfig
import sa.org.talmaarifa.tvsignage.sync.MediaDownloader
import sa.org.talmaarifa.tvsignage.sync.StorageLayout

/**
 * Composition root. No DI framework (Hilt/Dagger) — the dependency graph is small and static enough
 * that a hand-built container keeps the build simpler, which matters here since this project cannot
 * be build-verified end-to-end in the sandbox it was authored in (see README).
 */
class TvSignageApp : Application() {

    lateinit var credentialStore: CredentialStore; private set
    lateinit var serverConfig: ServerConfig; private set
    lateinit var database: AppDatabase; private set
    lateinit var storage: StorageLayout; private set
    lateinit var network: NetworkModule; private set
    lateinit var pairingRepository: PairingRepository; private set
    lateinit var eventLogRepository: EventLogRepository; private set
    lateinit var syncRepository: SyncRepository; private set

    override fun onCreate() {
        super.onCreate()

        credentialStore = CredentialStore(this)
        serverConfig = ServerConfig(this)
        database = AppDatabase.get(this)
        storage = StorageLayout(this)
        network = NetworkModule(serverConfig, credentialStore)

        pairingRepository = PairingRepository(network.pairingApi, network.apiCaller, credentialStore)
        eventLogRepository = EventLogRepository(database.playbackEventDao(), network.deviceApi, network.apiCaller, network.moshi)
        val mediaDownloader = MediaDownloader(network.deviceApi, storage, network.moshi)
        syncRepository = SyncRepository(
            deviceApi = network.deviceApi,
            apiCaller = network.apiCaller,
            mediaCacheDao = database.mediaCacheDao(),
            activePublicationDao = database.activePublicationDao(),
            mediaDownloader = mediaDownloader,
            storage = storage,
            eventLogRepository = eventLogRepository,
        )
    }

    companion object {
        fun from(context: android.content.Context): TvSignageApp = context.applicationContext as TvSignageApp
    }
}
