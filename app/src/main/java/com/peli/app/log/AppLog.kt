package com.peli.app.log

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.peli.app.APP_VERSION
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Registro de todo lo que ocurre en la app (acciones, navegación, red, errores, ciclo de vida) para
 * diagnosticar fallos de causa desconocida. Se guarda en un archivo con rotación y se puede ver, copiar
 * y compartir desde Ajustes. Nunca registra claves ni códigos de sala (ver [LogFormat.redact]).
 * Las escrituras van a un hilo propio para no frenar la interfaz.
 */
object AppLog {
    @Volatile private var store: LogStore? = null
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "peli-log").apply { isDaemon = true } }

    /** Con el registro detallado apagado se omiten las líneas de nivel D (red correcta, detalles de navegación). */
    @Volatile var verbose: Boolean = true
        private set

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val app = context.applicationContext
        store = LogStore(File(app.filesDir, "logs"))
        prefs = app.getSharedPreferences("peli_log", Context.MODE_PRIVATE).also { p ->
            verbose = p.getBoolean("verbose", true)
            p.edit().putInt("session", p.getInt("session", 0) + 1).apply()
        }
        val session = prefs?.getInt("session", 1) ?: 1
        val memoryClass = (app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass
        i(
            "Sesión",
            "===== Sesión #$session · Peli v$APP_VERSION · ${Build.MANUFACTURER} ${Build.MODEL} · " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Locale.getDefault()} · " +
                "fuente ${app.resources.configuration.fontScale}x · memoria app ${memoryClass ?: "?"} MB · " +
                "heap máx ${Runtime.getRuntime().maxMemory() / 1_048_576} MB =====",
        )
    }

    fun setVerbose(value: Boolean) {
        verbose = value
        prefs?.edit()?.putBoolean("verbose", value)?.apply()
        i("Registro", "registro detallado ${if (value) "activado" else "desactivado"}")
    }

    fun d(tag: String, message: String) = log('D', tag, message, null)
    fun i(tag: String, message: String) = log('I', tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = log('W', tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = log('E', tag, message, error)

    /** Memoria en uso, para ver si un fallo coincide con poca memoria. */
    fun memory(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        return "memoria ${used} MB / ${rt.maxMemory() / 1_048_576} MB"
    }

    fun log(level: Char, tag: String, message: String, error: Throwable?) {
        if (level == 'D' && !verbose) return
        val target = store ?: return
        val time = System.currentTimeMillis()
        val thread = Thread.currentThread().name
        val text = if (error == null) message else message + "\n" + error.stackTraceToString().take(8000)
        Log.println(priority(level), "Peli/$tag", LogFormat.redact(message))
        executor.execute { runCatching { target.append(LogFormat.line(time, level, tag, text, thread)) } }
    }

    /**
     * Para el instante de un cierre fatal: espera a que se escriba lo pendiente y registra el error
     * de forma síncrona (el proceso está por morir y un hilo en segundo plano no alcanzaría).
     */
    fun fatal(tag: String, message: String, error: Throwable) {
        val target = store ?: return
        runCatching { executor.submit {}.get(400, TimeUnit.MILLISECONDS) }
        runCatching {
            target.append(
                LogFormat.line(
                    System.currentTimeMillis(), 'E', tag,
                    "$message · ${memory()}\n" + error.stackTraceToString().take(8000),
                    Thread.currentThread().name,
                ),
            )
        }
    }

    /** Todo el registro (no llamar desde el hilo principal). */
    fun readAll(): String {
        runCatching { executor.submit {}.get(400, TimeUnit.MILLISECONDS) }
        return store?.readAll().orEmpty()
    }

    fun sizeBytes(): Long = store?.sizeBytes() ?: 0L

    fun clear() {
        store?.clear()
        i("Registro", "registro borrado por el usuario")
    }

    /** Encabezado + registro completo en un archivo de la caché listo para compartir. */
    fun exportFile(context: Context): File {
        val file = File(context.cacheDir, "peli-log.txt")
        file.writeText(LogFormat.redact(readAll()))
        return file
    }

    private fun priority(level: Char) = when (level) {
        'D' -> Log.DEBUG
        'I' -> Log.INFO
        'W' -> Log.WARN
        else -> Log.ERROR
    }
}
