package com.peli.app.update

import android.content.Context
import com.peli.app.APP_VERSION

/** Novedades por versión (orden ascendente). Agregar una entrada por cada versión nueva. */
object Changelog {
    val entries: Map<String, List<String>> = linkedMapOf(
        "1.0.0" to listOf(
            "Rediseño completo con Material 3 Expressive.",
        ),
        "1.0.1" to listOf(
            "La app se actualiza sola desde GitHub.",
            "Versionado simple: un dígito por campo (hasta 9.9.9).",
        ),
        "1.0.2" to listOf(
            "Las películas que aún no se estrenan muestran \"Próximo estreno\" y su fecha.",
        ),
        "1.0.3" to listOf(
            "Botón \"No me gusta\" en cada película, con filtro para verlas.",
        ),
        "1.0.4" to listOf(
            "Firma fija: las actualizaciones se instalan encima sin desinstalar.",
            "Descubrir: los botones Sí / No quedan siempre visibles, sin scroll.",
        ),
        "1.0.5" to listOf(
            "Las carátulas se ven completas, sin recortes.",
        ),
        "1.0.6" to listOf(
            "Estados claros: \"Quiero verla\" (se comparte con tu pareja) y \"La vi y me gustó\" (la app aprende).",
            "Recomendaciones inteligentes según los géneros que te gustan, lo reciente y la nota de TMDB.",
        ),
        "1.0.7" to listOf(
            "Salas con tu pareja: cuando a los dos les interesa la misma película aparece un match.",
            "En Descubrir, lo que le interesa a tu pareja aparece primero.",
        ),
        "1.0.8" to listOf(
            "Arreglo: los textos de tarjetas y ventanas se veían negros sobre fondo oscuro.",
        ),
        "1.0.9" to listOf(
            "Toca una carátula para verla en grande, con zoom y arrastre.",
        ),
        "1.1.0" to listOf(
            "Desliza hacia abajo para actualizar la lista.",
            "Al volver de una película, la lista queda donde estaba.",
        ),
        "1.1.1" to listOf(
            "La lista principal ahora es un feed de sugerencias para ti: al deslizar trae otras distintas.",
            "Buscar, filtros y pestañas conservan su estado; Atrás sube a la primera pestaña antes de salir.",
            "Mis pelis se puede ordenar; confirmación al salir de la sala; deshacer en Descubrir.",
            "Mejoras de teclado, rotación y búsquedas más rápidas y sin resultados viejos.",
        ),
        "1.1.2" to listOf(
            "Descubrir ya no muestra películas que marcaste en otra pantalla, ni las que omites.",
        ),
        "1.1.3" to listOf(
            "Arreglo: al deslizar para actualizar a veces salía «Comparison method violates its general contract».",
        ),
        "1.1.4" to listOf(
            "Filtro por años en Buscar y en Mis pelis: rango, décadas, clásicas y próximos estrenos.",
        ),
        "1.1.5" to listOf(
            "Se quitó el desenfoque de las tarjetas, que en algunos teléfonos cerraba la app al marcar «Quiero verla».",
            "Si la app se cierra, al abrirla muestra el informe del error para poder corregirlo.",
            "El detalle de una película ya no se cierra solo al quitarle la marca.",
        ),
        "1.1.6" to listOf(
            "Valoraciones en el detalle: TMDB, IMDb, Rotten Tomatoes y Metacritic, más reseñas de usuarios.",
            "Tu nota de 1 a 10 (también mejora tus recomendaciones) y la nota de tu pareja.",
            "Ajustes: elige de qué fuentes quieres ver valoraciones y reseñas.",
        ),
        "1.1.7" to listOf(
            "Registro completo de la app: Ajustes → Registro (ver, copiar, compartir o borrar).",
            "Anota pantallas, marcas, búsquedas, red, memoria y errores, sin claves ni códigos de sala.",
        ),
        "1.1.8" to listOf(
            "Activada la clave de OMDb: ahora se ven las notas de IMDb, Rotten Tomatoes y Metacritic.",
        ),
        "1.1.9" to listOf(
            "Compatible con las claves nuevas de Supabase (sb_publishable_…) para sincronizar con tu pareja.",
        ),
        "1.2.0" to listOf(
            "Al marcar «La vi y me gustó» te pide al tiro una nota de 1 a 10 para afinar tus recomendaciones (puedes omitirla).",
            "Sincronización con tu pareja activada (Supabase).",
        ),
        "1.2.1" to listOf(
            "Entra con Google, elige tu nombre de usuario y recibe un ID de amigo para que te agreguen.",
            "Agrega amigos por ID o @usuario, acepta solicitudes y elige con quién comparar listas.",
        ),
        "1.2.2" to listOf(
            "Activado el inicio de sesión con Google: entra en «Juntos» y crea tu usuario e ID de amigo.",
        ),
        "1.2.3" to listOf(
            "Arreglado el inicio de sesión con Google (la dirección del servidor se armaba mal).",
        ),
        "1.2.4" to listOf(
            "El botón 10 del panel de nota ya no se corta.",
        ),
        "1.2.6" to listOf(
            "Al actualizar la lista principal salen películas nuevas: se recuerdan las que ya viste pasar (hasta ~800) y solo después de muchas actualizaciones se repiten.",
            "Más variedad en las sugerencias: se sortean tus películas y géneros favoritos y se exploran más páginas.",
        ),
        "1.2.7" to listOf(
            "Recomendador nuevo: cada película cuenta una sola vez (la nota manda), «la vi» sin nota no cambia tu gusto y un «no me interesa» ya no anula lo que te gusta.",
            "La calidad se pondera por la cantidad de votos y los próximos estrenos no pasan del 10% de la lista.",
            "Más variedad: se evita juntar películas casi iguales y siempre hay un cupo de exploración fuera de tus géneros favoritos.",
            "Memoria de lo mostrado por rondas y días: lo que viste tarda semanas en volver, lo que omites mucho más.",
            "Descubrir aprende con cada respuesta y recuerda lo omitido aunque cierres la app.",
        ),
    )

    private const val PREFS = "peli_prefs"
    private const val KEY_LAST_SEEN = "last_seen_version"

    /**
     * Novedades posteriores a la última versión vista, hasta la instalada. Guarda la versión
     * actual como vista. En la primera instalación no muestra nada.
     */
    fun takeUnseen(context: Context): List<Pair<String, List<String>>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getString(KEY_LAST_SEEN, null)
        prefs.edit().putString(KEY_LAST_SEEN, APP_VERSION).apply()
        if (last == null) return emptyList()
        return entries.entries
            .filter { Updater.isNewer(it.key, last) && !Updater.isNewer(it.key, APP_VERSION) }
            .map { it.key to it.value }
            .reversed()
    }
}
