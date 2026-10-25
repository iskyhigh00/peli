package com.peli.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.peli.app.APP_VERSION
import com.peli.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Auto-actualización desde un repo PRIVADO de GitHub que solo contiene el APK.
 *
 *  - version.txt -> versionName de la última build (lo publica CI).
 *  - Peli.apk    -> el instalador.
 *
 * Si CI inyecta un token de SOLO LECTURA (BuildConfig.UPDATE_TOKEN, no está en el código fuente)
 * se usa ese repo privado; sin token se usa el último release público de este repo.
 *
 * Con la MISMA firma en todas las versiones, el APK nuevo se instala encima del anterior
 * conservando la base de datos; Android pide confirmar la instalación.
 */
object Updater {
    // Sin token (repo público): último release de este repo. Con token (repo privado): release fijo de peli-apk.
    private val publicMode get() = BuildConfig.UPDATE_TOKEN.isEmpty()
    private val REPO get() = if (publicMode) "iskyhigh00/peli" else "iskyhigh00/peli-apk"
    private val RELEASE_API
        get() = "https://api.github.com/repos/$REPO/releases/" + if (publicMode) "latest" else "tags/peli-apk"
    private const val APK_NAME = "Peli.apk"
    private const val VERSION_NAME = "version.txt"

    /** Resultado de consultar la última versión, con detalle para diagnóstico. */
    data class CheckResult(val version: String?, val error: String?)

    fun checkLatest(): CheckResult {
        return try {
            val id = assetMap()[VERSION_NAME]?.id
                ?: return CheckResult(null, "el release no tiene $VERSION_NAME")
            val txt = readAssetText(id).trim().removePrefix("v").trim()
            if (txt.isEmpty()) CheckResult(null, "version.txt vacío") else CheckResult(txt, null)
        } catch (e: Exception) {
            CheckResult(null, e.message ?: e.javaClass.simpleName)
        }
    }

    /** true si remote (p.ej. "1.0.9") es más nueva que local, comparando número por número. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(s: String) = s.split(".").mapNotNull { it.trim().toIntOrNull() }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * Descarga el APK a la caché y lanza el instalador del sistema. Si ya hay una descarga
     * COMPLETA de esa versión, se reutiliza. onProgress recibe 0..100 (o -1 si no se conoce
     * el tamaño). Devuelve true si se reutilizó la descarga guardada.
     */
    fun downloadAndInstall(context: Context, version: String, onProgress: (Int) -> Unit): Boolean {
        val asset = assetMap()[APK_NAME] ?: throw IOException("el release no tiene $APK_NAME")
        val apk = File(context.cacheDir, "update-$version.apk")
        if (apk.exists() && asset.size > 0 && apk.length() == asset.size) {
            onProgress(100)
            installApk(context, apk)
            return true
        }

        // Se baja a un .part y solo se renombra si llegó completo.
        val part = File(context.cacheDir, "update-$version.apk.part")
        val conn = openAsset(asset.id)
        try {
            val total = conn.contentLength
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(16384)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(if (total > 0) ((read * 100) / total).toInt() else -1)
                    }
                }
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        if (asset.size > 0 && part.length() != asset.size) {
            part.delete()
            throw IOException("descarga incompleta")
        }
        apk.delete()
        if (!part.renameTo(apk)) throw IOException("no se pudo guardar la descarga")
        installApk(context, apk)
        return false
    }

    /** Borra descargas de la versión instalada o anteriores y las incompletas. Se llama al abrir la app. */
    fun cleanupDownloads(context: Context) {
        val files = context.cacheDir.listFiles() ?: return
        for (f in files) {
            val n = f.name
            if (n.endsWith(".apk.part")) { f.delete(); continue }
            if (n.startsWith("update-") && n.endsWith(".apk")) {
                val v = n.removePrefix("update-").removeSuffix(".apk")
                if (!isNewer(v, APP_VERSION)) f.delete()
            }
        }
    }

    private fun installApk(context: Context, apk: File) {
        // Android O+: requiere el permiso "instalar apps desconocidas".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ---- API de GitHub ----------------------------------------------------

    private data class Asset(val id: Long, val size: Long)

    private fun assetMap(): Map<String, Asset> {
        val conn = open(RELEASE_API, "application/vnd.github+json", withAuth = true)
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val arr = json.getJSONArray("assets")
            val out = HashMap<String, Asset>()
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                out[a.getString("name")] = Asset(a.getLong("id"), a.optLong("size", -1))
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun readAssetText(id: Long): String {
        val conn = openAsset(id)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * GitHub responde 302 a una URL firmada de otro host; esa NO debe llevar el header
     * Authorization (el almacenamiento la rechaza), así que la redirección se sigue a mano.
     */
    private fun openAsset(id: Long): HttpURLConnection {
        var url = "https://api.github.com/repos/$REPO/releases/assets/$id"
        var auth = true
        repeat(5) {
            val c = open(url, "application/octet-stream", withAuth = auth, checkOk = false)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrEmpty()) throw IOException("redirección sin destino")
                url = loc
                auth = false
            } else {
                if (code !in 200..299) { c.disconnect(); throw IOException("HTTP $code") }
                return c
            }
        }
        throw IOException("demasiadas redirecciones")
    }

    private fun open(urlStr: String, accept: String, withAuth: Boolean, checkOk: Boolean = true): HttpURLConnection {
        val c = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Peli")
            setRequestProperty("Accept", accept)
            if (withAuth && BuildConfig.UPDATE_TOKEN.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer ${BuildConfig.UPDATE_TOKEN}")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        c.connect()
        if (checkOk) {
            val code = c.responseCode
            if (code !in 200..299) { c.disconnect(); throw IOException("HTTP $code") }
        }
        return c
    }
}
