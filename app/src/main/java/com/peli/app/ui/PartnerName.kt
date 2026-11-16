package com.peli.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.peli.app.data.PartnerText

/** El nombre con que se habla de la pareja en esta pantalla (cambia según la persona activa). Es estado de Compose: los textos se actualizan solos. */
object PartnerName {
    var base by mutableStateOf(PartnerText.DEFAULT)

    val tu get() = PartnerText.tu(base)
    val mi get() = PartnerText.mi(base)
    val Tu get() = PartnerText.cap(PartnerText.tu(base))
    val Mi get() = PartnerText.cap(PartnerText.mi(base))
    /** Solo el apodo con mayúscula («Princesa», «Ri»), para filas y etiquetas. */
    val cap get() = PartnerText.cap(base)
}
