package pt.estante.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pt.estante.app.data.AppDatabase
import pt.estante.app.data.BookRepository
import pt.estante.app.data.SettingsStore
import pt.estante.app.net.SheetApi

class EstanteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    /** For work that must finish even if the user leaves the screen that started it. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)
    val sheetApi = SheetApi()
    val repository = BookRepository(context, AppDatabase.build(context), settings, sheetApi)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as EstanteApp).container
