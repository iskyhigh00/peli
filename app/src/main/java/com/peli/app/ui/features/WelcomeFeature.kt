package com.peli.app.ui.features

import com.peli.app.data.Repository
import com.peli.app.data.WelcomeMessage
import com.peli.app.data.accountMessage
import com.peli.app.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/** Mensaje de bienvenida (se escribe en Ajustes; vacío = no se muestra), solo o compartido con el amigo activo. */
class WelcomeFeature(
    private val env: VmEnv,
    private val repo: Repository,
    private val activeFriend: () -> String?,
    private val hasSession: () -> Boolean,
    private val friendName: (String) -> String?,
) {
    val text = MutableStateFlow(env.peli.welcomePrefs.text)

    // Con un amigo activo el mensaje es compartido: se espera a pedirlo al servidor para saber si lo escribió el otro.
    val splashVisible = MutableStateFlow(env.peli.welcomePrefs.text.isNotBlank() && activeFriend() == null)
    private var loaded = false

    /** Al abrir la app: trae el mensaje compartido con el amigo activo y lo muestra si lo dejó la otra persona. */
    fun loadShared() {
        if (loaded) return
        loaded = true
        val friend = activeFriend() ?: return
        if (!hasSession()) return
        env.launch(Dispatchers.IO) {
            val row = try { repo.welcome(friend) } catch (ex: CancellationException) { throw ex } catch (ex: Exception) {
                AppLog.d("Mensaje", "sin mensaje compartido: ${ex.message}")
                return@launch
            }
            val body = row?.body.orEmpty()
            text.value = body
            env.peli.welcomePrefs.text = body
            splashVisible.value = row != null && !row.mine && body.isNotBlank()
        }
    }

    fun setText(value: String) {
        val t = value.take(WelcomeMessage.MAX_CHARS)
        text.value = t
        env.peli.welcomePrefs.text = t
    }

    /** Guarda el mensaje en este teléfono y, con un amigo activo, lo envía para que lo vea al abrir la app. */
    fun save(value: String) {
        setText(value)
        val friend = activeFriend()
        if (friend == null || !hasSession()) { env.message.value = "Mensaje guardado."; return }
        val name = friendName(friend)
        env.launch {
            try {
                repo.setWelcome(friend, text.value)
                env.message.value = if (name != null) "Listo: @$name verá el mensaje al abrir la app." else "Mensaje enviado."
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                AppLog.w("Mensaje", "no se pudo guardar el mensaje compartido", ex)
                env.message.value = ex.accountMessage()
            }
        }
    }
}
