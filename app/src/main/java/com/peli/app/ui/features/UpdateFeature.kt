package com.peli.app.ui.features

import com.peli.app.APP_VERSION
import com.peli.app.log.AppLog
import com.peli.app.update.Changelog
import com.peli.app.update.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

sealed interface ManualUpdate {
    data object Idle : ManualUpdate
    data object Checking : ManualUpdate
    data object UpToDate : ManualUpdate
    data class Available(val version: String) : ManualUpdate
    data class Failed(val error: String?) : ManualUpdate
}
/** Actualizaciones de la app: versión nueva, descarga e instalación, y las novedades que se muestran tras actualizar. */
class UpdateFeature(private val env: VmEnv) {
    /** Versión nueva disponible (null = al día o sin consultar). */
    val newVersion = MutableStateFlow<String?>(null)

    /** Resultado de "Buscar actualización" en Ajustes: se muestra ahí mismo, sin tocar la franja de la pantalla principal. */
    val manualUpdate = MutableStateFlow<ManualUpdate>(ManualUpdate.Idle)

    /** Progreso de descarga: -2 inactivo, -1 sin tamaño conocido, 0..100. */
    val progress = MutableStateFlow(-2)

    /** Novedades a mostrar tras actualizar (versión -> cambios). */
    val whatsNew = MutableStateFlow(Changelog.takeUnseen(env.app))

    // ---- Actualizaciones -----------------------------------------------------

    /** Al abrir la app es silencioso; manual (botón) informa también si está al día o falló. */
    /** Al abrir la app (automática) avisa en la pantalla principal; desde Ajustes (manual) responde en Ajustes. */
    fun check(manual: Boolean) {
        if (manual) {
            if (manualUpdate.value == ManualUpdate.Checking) return
            manualUpdate.value = ManualUpdate.Checking
        }
        env.launch {
            AppLog.d("Actualizador", "consultando actualización (${if (manual) "manual" else "automática"})")
            val res = withContext(Dispatchers.IO) { Updater.checkLatest() }
            val v = res.version
            when {
                v == null -> if (manual) manualUpdate.value = ManualUpdate.Failed(res.error)
                Updater.isNewer(v, APP_VERSION) -> if (manual) manualUpdate.value = ManualUpdate.Available(v) else newVersion.value = v
                else -> if (manual) manualUpdate.value = ManualUpdate.UpToDate else newVersion.value = null
            }
        }
    }

    /** Al cerrar Ajustes se olvida el resultado de la consulta manual. */
    fun clearManual() { if (progress.value == -2) manualUpdate.value = ManualUpdate.Idle }

    fun install(version: String? = null) {
        if (progress.value != -2) return   // ya se está descargando
        val v = version ?: newVersion.value ?: return
        AppLog.i("Actualizador", "el usuario pidió instalar v$v")
        env.launch {
            progress.value = -1
            try {
                withContext(Dispatchers.IO) {
                    Updater.downloadAndInstall(env.app, v) { p -> progress.value = p }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Actualizador", "no se pudo instalar v$v", e)
                env.message.value = "No se pudo actualizar: ${e.message}"
            } finally {
                progress.value = -2
            }
        }
    }

    fun dismissWhatsNew() { whatsNew.value = emptyList() }
}
