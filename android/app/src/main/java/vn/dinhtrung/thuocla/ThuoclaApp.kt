package vn.dinhtrung.thuocla

import android.app.Application
import android.content.Context
import java.time.Clock
import vn.dinhtrung.thuocla.data.DataPortability
import vn.dinhtrung.thuocla.data.BackupSharer
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase

/**
 * Manual dependency container (design decision #1: no Hilt — one database and one repository).
 *
 * [clock] is injectable so date/time dependent behaviour can be tested without waiting for the wall
 * clock; production always uses the device zone. [DataPortability] takes the same clock so an
 * exported backup's `exportedAt` and file name agree with the rest of the app.
 */
class AppContainer(
    context: Context,
    val clock: Clock = Clock.systemDefaultZone(),
) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val repository: TrackerRepository by lazy { TrackerRepository(database) }

    val dataPortability: DataPortability by lazy {
        DataPortability(database, repository, clock)
    }

    /**
     * Writes the share copy of the JSON backup into `cacheDir/backups/`, the one root the FileProvider
     * exposes. Lives beside [dataPortability] so the share path and the SAF export path produce the
     * same file name and payload.
     */
    val backupSharer: BackupSharer by lazy {
        BackupSharer(
            portability = dataPortability,
            directory = java.io.File(appContext.cacheDir, BackupSharer.DIRECTORY_NAME),
        )
    }
}

class ThuoclaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
