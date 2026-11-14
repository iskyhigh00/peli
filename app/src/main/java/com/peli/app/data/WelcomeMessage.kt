package com.peli.app.data

/** Mensaje corto que aparece al abrir la app. Aquí está la parte pura: las palabras y su ritmo de aparición. */
object WelcomeMessage {
    const val MAX_CHARS = 140
    const val HOLD_MS = 1_500L
    private const val PER_WORD_MS = 280
    private const val MAX_REVEAL_MS = 3_000
    private const val MIN_REVEAL_MS = 700

    fun words(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Cuánto tarda en aparecer todo el texto (palabra por palabra), con tope para que siempre sean pocos segundos. */
    fun revealMs(wordCount: Int): Int = (wordCount * PER_WORD_MS).coerceIn(MIN_REVEAL_MS, MAX_REVEAL_MS)

    /** Opacidad de la palabra [index] cuando el avance va por [progress] (0..cantidad de palabras): aparece una tras otra. */
    fun alpha(index: Int, progress: Float): Float = (progress - index).coerceIn(0f, 1f)
}
