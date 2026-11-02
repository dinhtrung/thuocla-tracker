package vn.dinhtrung.thuocla

import android.app.Application
import android.content.Context
import java.time.Clock
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase

/**
 * Manual dependency container (design decision #1: no Hilt — one database and one repository).
 *
 * [clock] is injectable so date/time dependent behaviour can be tested without waiting for the wall
 * clock; production always uses the device zone.
 */
class AppContainer(
    context: Context,
    val clock: Clock = Clock.systemDefaultZone(),
) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val repository: TrackerRepository by lazy { TrackerRepository(database) }
}

class ThuoclaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
