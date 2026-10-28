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
        "1.2.8" to listOf(
            "Arreglado un cierre inesperado de la app (error de Compose «Placement happened before lookahead»): se quitó la animación compartida de la carátula al abrir el detalle, que lo provocaba.",
        ),
        "1.2.9" to listOf(
            "Al pulsar «Quiero verla» en una película que ya tenía otra persona, ahora se guarda tu marca (antes podía quedar vacía).",
            "Una película pendiente ya no conserva una nota antigua; los estados incoherentes se reparan al importarlos.",
            "Si falla la red al actualizar la lista principal, se conserva lo que ya se veía y se avisa en vez de mostrar «sin resultados».",
        ),
        "1.3.0" to listOf(
            "Tus marcas se guardan y se envían con una cola durable: lo que cambies sin conexión (incluso quitar una marca) se envía cuando vuelva la red.",
            "Cada cuenta tiene sus propias películas en el teléfono. Al iniciar sesión se te pregunta si las películas sin cuenta pasan a esa cuenta; nunca se suben solas.",
            "Si cambias de amigo, sales de una sala o cierras sesión mientras se carga algo, lo que llegue tarde se descarta.",
            "Las listas largas de tus amigos se descargan completas por páginas antes de reemplazar la anterior.",
            "Deshacer y el aprendizaje de Descubrir ya no esperan a la red.",
        ),
        "1.3.1" to listOf(
            "Descubrir anota cada tarjeta que de verdad se ve y vuelve a validar la cola contra lo entregado en la lista principal.",
            "Los plazos para repetir una película ya no se relajan solos: primero salen las nunca mostradas y, si no alcanzan, se buscan más fuentes o la lista queda más corta con un aviso.",
            "Más cobertura: cada consulta a TMDB recuerda su página (no repite), rota orden y décadas, comparte sobrantes entre pantallas y limita las peticiones a la vez.",
            "Las películas sin carátula o sin sinopsis ya no se descartan: solo bajan un poco en la lista.",
            "Descubrir usa la misma selección con diversidad que la lista principal y los próximos estrenos tienen un tope estricto (salvo que filtres años futuros).",
        ),
        "1.3.2" to listOf(
            "La app ya no lleva ninguna credencial de actualización: descarga solo desde el repositorio público.",
            "El actualizador baja exactamente la versión anunciada, no la última que aparezca.",
            "Los datos de sesión y vinculación quedan fuera de las copias de seguridad del teléfono.",
            "Al importar una lista se validan tamaño, identificadores y campos antes de guardar.",
        ),
        "1.3.3" to listOf(
            "La lista principal sigue cargando sugerencias al llegar al final: ya no hay que volver arriba para actualizar.",
            "Nuevos filtros: nota mínima en TMDB, IMDb, Rotten Tomatoes y Metacritic, Oscar (nominada o ganadora), premios ganados y nominaciones.",
            "Los filtros (y el de años) se guardan aunque cierres la app y siguen activos hasta que toques «Quitar filtros».",
            "Los datos de cada película (palabras clave, director, reparto, puntajes y premios) se piden una sola vez y se guardan en el teléfono.",
        ),
        "1.3.4" to listOf(
            "Las sugerencias ahora también consideran palabras clave, director y reparto de lo que te gusta (además de géneros y época).",
            "Esos datos se completan solos en segundo plano y se guardan en el teléfono: cada película se consulta una sola vez.",
        ),
        "1.3.5" to listOf(
            "Descubrir ahora tiene los mismos filtros que Buscar (años, puntajes, Oscar, premios y «Quitar filtros»); valen en las dos pantallas.",
            "En Descubrir puedes deslizar la tarjeta hacia un lado para omitirla.",
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
