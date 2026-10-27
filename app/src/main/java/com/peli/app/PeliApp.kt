package com.peli.app

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import com.peli.app.data.AccountStore
import com.peli.app.data.AppDb
import com.peli.app.data.PairingStore
import com.peli.app.data.Repository
import com.peli.app.data.SourcePrefs
import com.peli.app.data.Social
import com.peli.app.data.TmdbApi
import com.peli.app.log.AppLog

class PeliApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        CrashReporter.install(this)
        Social.store = account
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private fun name(a: Activity) = a.javaClass.simpleName
            override fun onActivityCreated(a: Activity, state: Bundle?) =
                AppLog.i("Ciclo", "${name(a)} creada (${if (state != null) "restaurada" else "nueva"}) · ${AppLog.memory()}")
            override fun onActivityStarted(a: Activity) = AppLog.d("Ciclo", "${name(a)} visible")
            override fun onActivityResumed(a: Activity) = AppLog.d("Ciclo", "${name(a)} en primer plano")
            override fun onActivityPaused(a: Activity) = AppLog.d("Ciclo", "${name(a)} en pausa")
            override fun onActivityStopped(a: Activity) = AppLog.d("Ciclo", "${name(a)} detenida · ${AppLog.memory()}")
            override fun onActivitySaveInstanceState(a: Activity, out: Bundle) = AppLog.d("Ciclo", "${name(a)} guarda estado")
            override fun onActivityDestroyed(a: Activity) = AppLog.i(
                "Ciclo",
                "${name(a)} destruida (${if (a.isChangingConfigurations) "cambio de configuración, p. ej. rotación" else "cierre"})",
            )
        })
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                val name = when (level) {
                    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "app oculta"
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "en uso, memoria moderada"
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "en uso, memoria baja"
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "en uso, memoria CRÍTICA"
                    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "segundo plano"
                    ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "segundo plano, memoria moderada"
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "segundo plano, a punto de cerrarse"
                    else -> "nivel $level"
                }
                val serious = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                AppLog.log(if (serious) 'W' else 'D', "Memoria", "el sistema pide liberar memoria: $name · ${AppLog.memory()}", null)
            }

            override fun onConfigurationChanged(c: Configuration) = AppLog.i(
                "Configuración",
                "orientación=${if (c.orientation == Configuration.ORIENTATION_LANDSCAPE) "horizontal" else "vertical"}, " +
                    "fuente=${c.fontScale}x, modo noche=${(c.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES}",
            )

            override fun onLowMemory() = AppLog.w("Memoria", "memoria baja en el sistema · ${AppLog.memory()}")
        })
    }

    val account by lazy { AccountStore(this) }
    val pairing by lazy { PairingStore(this) }
    val sourcePrefs by lazy { SourcePrefs(this) }
    val repository by lazy { Repository(AppDb.create(this), TmdbApi.create()) }
}
