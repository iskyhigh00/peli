package com.peli.app.data

/** Cómo se nombra a la pareja en los textos: «tu pareja», «mi pareja»… o el apodo que se elija («tu princesa», «mi Ri»). */
object PartnerText {
    const val DEFAULT = "pareja"
    const val MAX = 20

    /** Lo que se escribe como apodo, limpio: sin «mi»/«tu» delante y de largo razonable; vacío = «pareja». */
    fun clean(raw: String): String {
        var t = raw.trim().replace(Regex("\\s+"), " ")
        t = t.replace(Regex("^(mi|tu|su)(\\s+|$)", RegexOption.IGNORE_CASE), "").trim().take(MAX).trim()
        return t.ifEmpty { DEFAULT }
    }

    fun tu(base: String) = "tu $base"
    fun mi(base: String) = "mi $base"
    fun cap(text: String) = text.replaceFirstChar { it.uppercase() }
}
