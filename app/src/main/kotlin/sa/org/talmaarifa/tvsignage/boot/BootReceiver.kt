package sa.org.talmaarifa.tvsignage.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import sa.org.talmaarifa.tvsignage.MainActivity
import sa.org.talmaarifa.tvsignage.service.SignageForegroundService
import sa.org.talmaarifa.tvsignage.sync.ManifestSyncWorker

/**
 * Brings the signage display back up automatically after the device reboots (no person present to
 * tap the launcher icon) — the Android TV leanback launcher normally only shows apps the user picks.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        SignageForegroundService.start(context)
        ManifestSyncWorker.schedule(context)

        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
        )
    }
}
