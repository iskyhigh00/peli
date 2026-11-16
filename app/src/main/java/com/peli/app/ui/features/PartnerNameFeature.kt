package com.peli.app.ui.features

import com.peli.app.data.PartnerText
import com.peli.app.ui.PartnerName
import kotlinx.coroutines.flow.MutableStateFlow

/** El apodo de la pareja en los textos, uno por persona (amigo con cuenta o sala). */
class PartnerNameFeature(private val env: VmEnv, private val activeFriend: () -> String?) {
    /** Lo que se ve en el campo de Ajustes («» = sin apodo). */
    val nickname = MutableStateFlow("")

    private fun key() = activeFriend() ?: "sala"

    /** Carga el apodo de la persona activa (al abrir y al cambiar de amigo). */
    fun apply() {
        val base = env.peli.partnerNamePrefs.get(key())
        PartnerName.base = base
        nickname.value = if (base == PartnerText.DEFAULT) "" else base
    }

    fun set(text: String) {
        nickname.value = text.take(PartnerText.MAX)
        val base = PartnerText.clean(text)
        env.peli.partnerNamePrefs.put(key(), base)
        PartnerName.base = base
    }
}
