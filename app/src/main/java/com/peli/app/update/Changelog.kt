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
        "1.3.6" to listOf(
            "Descubrir ahora tiene los mismos filtros que Buscar (años, puntajes, Oscar, premios y «Quitar filtros»); valen en las dos pantallas.",
            "En Descubrir puedes deslizar la tarjeta hacia un lado para omitirla.",
        ),
        "1.3.7" to listOf(
            "Nuevo botón «Película segura» (en Buscar y Descubrir): propone la película que más probablemente te guste según tus notas, con los motivos.",
            "Puedes marcarla «Ya la vi» (te pide la nota para aprender), «Quiero verla», «No me interesa» o pedir otra opción.",
            "Necesita al menos 5 películas con nota o «La vi y me gustó» para empezar. Es una estimación, no una garantía.",
        ),
        "1.3.8" to listOf(
            "Los títulos salen en su idioma original con la traducción entre paréntesis, por ejemplo «Spirited Away (El viaje de Chihiro)». Si coinciden, se muestra uno solo.",
            "Las películas que ya tenías marcadas completan su título original solas, en segundo plano.",
        ),
        "1.3.9" to listOf(
            "Compartir mi lista ya no falla con listas largas: el código ahora va comprimido (unas 10 veces más corto) y, si aun así fuera enorme, se envía como archivo.",
            "Si compartir falla por otro motivo, la app lo avisa y lo deja en el registro en vez de no hacer nada.",
            "El código de versiones anteriores sigue pudiéndose importar.",
        ),
        "1.4.1" to listOf(
            "En Buscar y Mis pelis, los filtros reaparecen arriba al instante cuando deslizas la lista hacia arriba, aunque estés lejos del principio; se esconden al seguir bajando.",
            "Desde esa barra se pueden editar los filtros sin volver al inicio de la lista.",
        ),
        "1.4.2" to listOf(
            "Compartir mi lista abre más rápido: avisa al instante, el código se arma sin congelar la pantalla y es unas 3 veces más corto (sin sinopsis).",
        ),
        "1.4.3" to listOf(
            "Compartir (en Mis pelis) ahora envía solo un ID, no la lista entera: tu ID de amigo si tienes cuenta, o el código de una sala (si no hay ninguna, crea una con tu lista). La lista ya está en el servidor.",
            "El código largo con toda la lista queda solo para cuando la app no tiene servidor.",
        ),
        "1.4.4" to listOf(
            "Se quitó el icono de actualizar de la barra de arriba: «Buscar actualización» sigue en Ajustes.",
        ),
        "1.4.5" to listOf(
            "En el detalle de una película, al deslizar el cartel hacia un lado aparece el tráiler (reproductor de YouTube; si no carga, hay botón para abrirlo en YouTube).",
            "Abajo del detalle ahora se ven el director y el reparto.",
        ),
        "1.4.6" to listOf(
            "Nuevo filtro «Directores» (en Buscar y Descubrir): lista los directores con 3 o más películas conocidas por Peli, primero los de más películas y más premios.",
            "Al elegir un director se ven sus películas (con los demás filtros aplicados). Se guarda como los otros filtros hasta tocar «Quitar filtros».",
            "La lista de directores crece a medida que marcas y exploras películas.",
        ),
        "1.4.7" to listOf(
            "El tráiler ahora se reproduce dentro de la app con un reproductor de YouTube (sin botón para salir a YouTube).",
            "Solo si YouTube no permite ese video fuera de su app, se explica el motivo y se ofrece abrirlo en YouTube.",
        ),
        "1.4.8" to listOf(
            "Los filtros de IMDb, Rotten Tomatoes, Metacritic y premios gastan menos consultas de OMDb: se descartan sin consultar las películas cuya nota de TMDB casi seguro no cumpliría.",
            "El aviso de límite de OMDb solo sale cuando OMDb de verdad dejó de responder (antes también salía al alcanzar el tope de cada carga) e indica cuándo se renueva.",
            "El límite diario de OMDb se renueva a medianoche UTC; la app espera hasta esa hora en vez de un tiempo fijo.",
        ),
        "1.4.9" to listOf(
            "Filtros ordenados: siempre Filtros, Años y Director (con íconos y su valor cuando están activos), «Limpiar» al final y debajo un resumen de lo activo. Las categorías (Quiero ver, Vistas…) van en su propia fila.",
            "«Película segura» ahora se ve como una acción aparte: junto a «Sugerencias para ti» y al inicio de la fila en Descubrir.",
            "Matches con otra persona: al conectarse ya no se celebran de golpe muchas coincidencias que existían (si son 3 o menos, sí se avisan), y la «foto inicial» se toma cuando de verdad llegó su lista.",
            "Más fluida: la memoria de lo ya mostrado se guarda en lote al desplazar la lista y abrir una película ya no gasta consultas de OMDb repetidas.",
        ),
        "1.5.0" to listOf(
            "Película segura respeta tus filtros de años y de nota TMDB.",
            "Además solo propone películas de la época de lo que te gusta: si nunca calificaste alto algo anterior al 2000, no te ofrecerá una de 1985 (si te gustan clásicos, sí los incluye).",
        ),
        "1.5.2" to listOf(
            "Con el tráiler a la vista, el video queda fijo arriba mientras te desplazas por la ficha: puedes leer y seguir viéndolo. Con el cartel, todo se desplaza como antes.",
            "El tráiler que se elige sigue tu preferencia de idioma: español latino, luego inglés (primero con subtítulos, después sin ellos) y al final español de España.",
        ),
        "1.5.3" to listOf(
            "El título ya no repite el nombre original entre paréntesis (\"The Art of Travel (El arte de viajar)\").",
            "Con el tráiler, el video queda debajo de la barra de estado, sin tapar la hora ni los íconos.",
            "Corregido: si una ficha guardada ya no existía, las tarjetas dejaban de abrir al tocarlas.",
        ),
        "1.5.4" to listOf(
            "Avisos de estreno: una notificación cuando se acerca el estreno de una película de \"Quiero verla\" (a una semana, el día anterior y el mismo día). Se pueden desactivar en Ajustes.",
            "La fecha de estreno de esas películas se revisa en segundo plano, porque suele cambiar.",
        ),
        "1.5.5" to listOf(
            "Corregido: la flecha de \"arrastrar para actualizar\" ya no queda pegada sobre el buscador.",
        ),
        "1.5.6" to listOf(
            "El tráiler se ve limpio, sin los controles ni el título de YouTube. Un toque pausa o reanuda; doble toque a la izquierda retrocede 10 s y a la derecha adelanta 10 s.",
        ),
        "1.5.7" to listOf(
            "El botón verde de Buscar ahora sirve para volver arriba (aparece al bajar por la lista). Descubrir queda solo en la barra de abajo.",
        ),
        "1.5.8" to listOf(
            "\"Buscar actualización\" responde dentro de Ajustes (al día, versión nueva con su botón de actualizar, o el error). La franja de la pantalla principal queda solo para cuando la app detecta sola una versión nueva al abrirse.",
        ),
        "1.5.9" to listOf(
            "La barra de filtros ya no salta completa por un roce hacia arriba: aparece poco a poco mientras sigues deslizando, y se esconde igual al bajar.",
        ),
        "1.6.0" to listOf(
            "Atrás con la lista desplazada: vuelve al comienzo y se recarga (como Instagram). Con un segundo Atrás se sale de la app.",
        ),
        "1.6.1" to listOf(
            "Recomendar una película a un amigo: botón \"Recomendar a un amigo\" en el detalle. A tu amigo le aparece en Juntos (\"Te recomendaron\") con \"Quiero verla\" y \"Descartar\", y le llega una notificación. Requiere cuenta y ejecutar recommendations.sql en Supabase.",
        ),
        "1.6.3" to listOf(
            "En Valoraciones, la fila de tu pareja (o amigo) usa su nombre y aparece siempre que su lista esté cargada: su nota, o si le gustó, la vio, la quiere ver o aún no la marca.",
        ),
        "1.6.4" to listOf(
            "Foto de ustedes en la app: de fondo en los matches (con colores cálidos) y arriba en la pestaña Juntos.",
        ),
        "1.6.5" to listOf(
            "Match: la foto ocupa toda la pantalla y es lo principal; la película queda abajo con sus botones.",
            "La cuenta, el ID y los amigos pasaron de Juntos a Ajustes. Juntos queda solo con la foto, las recomendaciones y los matches.",
        ),
        "1.6.7" to listOf(
            "Nuevo ícono de la app: los dos perritos con las palomitas, sin fondo propio, sobre un azul noche con un resplandor cálido. También como ícono adaptable y de color único (temas de Android 13).",
        ),
        "1.6.8" to listOf(
            "Ícono sin fondo: solo los perritos y las palomitas. En algunos teléfonos el lanzador puede ponerle un fondo propio detrás.",
        ),
        "1.6.9" to listOf(
            "Botón de recomendar en cada película de las listas, junto a guardar, me gusta y no me gusta. Con un solo amigo se envía directo; con varios, eliges a quién.",
        ),
        "1.7.0" to listOf(
            "Ahora puedes marcar una película como \"Quiero verla\" y también \"Ya la vi\", \"Me gustó\" o con nota, a la vez (para volver a verla). Solo \"No me gusta\" sigue siendo incompatible con \"Quiero verla\".",
        ),
        "1.7.1" to listOf(
            "Sección \"Premios\" en el detalle de la película, bajo las valoraciones: Óscares ganados o nominaciones, y el total de premios y nominaciones (solo si los tiene).",
        ),
        "1.7.2" to listOf(
            "El ícono de la app aparece arriba a la izquierda, pequeño y discreto.",
        ),
        "1.7.3" to listOf(
            "Calcomanía de los perritos, 3 veces más grande, a la derecha del encabezado (con borde blanco). Se va con la barra al desplazar. Se quitó el ícono pequeño de la barra, que hacía raro el desplazamiento.",
        ),
        "1.7.4" to listOf(
            "Juntos: se quitó el texto \"Ver pelis juntos\" sobre la foto; queda solo la foto.",
        ),
        "1.7.5" to listOf(
            "Chuba y Pepa tienen cada una su gusto fijo por las películas (Chuba: dulce y familiar; Pepa: oscuro y tenso). En Juntos, \"Cine con Chuba y Pepa\" muestra las que le gustan a cada una, con su nota y el motivo; \"Ver otras\" trae más.",
            "En el detalle de cada película, \"Chuba y Pepa opinan\": su nota de 1 a 10 y por qué. La misma película siempre recibe la misma nota.",
        ),
        "1.7.6" to listOf(
            "Chuba y Pepa con su verdadera personalidad: Chuba (inquieta, siempre con hambre) prefiere acción y aventuras; Pepa (tranquila, huye del ruido y del peligro) prefiere drama, romance y lo calmado.",
        ),
        "1.7.7" to listOf(
            "Las 20 calcomanías de Chuba y Pepa: aparecen de una en una en el encabezado (cambian cada 20 s y al cambiar de pestaña), en el perfil de cada una en Juntos y en su opinión de cada película.",
            "Perfil de Chuba y de Pepa en Juntos, con cómo es cada una; al tocarlo se ven las películas que le gustan.",
        ),
        "1.7.8" to listOf(
            "Avisos para preparar las películas del fin de semana: 2 el miércoles (12:00 y 19:30), 4 el jueves (9:00, 12:30, 17:00 y 20:00) y 1 el viernes en la mañana (9:00). Vienen apagados: cada persona los activa en Ajustes, en \"Fin de semana\".",
        ),
        "1.7.9" to listOf(
            "Los avisos de fin de semana ahora vienen apagados: se activan en Ajustes, en \"Fin de semana\", solo en el teléfono de quien los quiera.",
        ),
        "1.8.0" to listOf(
            "Solo el aviso de estrenos en el cine viene activado para todos. Las recomendaciones de amigos y los avisos de fin de semana vienen apagados: cada persona los activa en Ajustes.",
        ),
        "1.8.1" to listOf(
            "Los avisos de estreno y de recomendaciones de amigos vuelven a venir activados; solo los de fin de semana vienen apagados. Cada uno se puede cambiar en Ajustes.",
        ),
        "1.8.2" to listOf(
            "Se quitaron las 2 calcomanías de \"Estreno\" de Pepa, que tenían un error (le salían patas de más). Quedan 18.",
        ),
        "1.8.3" to listOf(
            "En Juntos, al elegir a Chuba o a Pepa y bajar por su lista, sus calcomanías se quedan fijas arriba (con un botón para ocultarlas).",
            "Los motivos de las notas de Chuba y Pepa ahora varían de una película a otra en vez de repetir la misma frase.",
        ),
        "1.8.4" to listOf(
            "Al recomendar una película a un amigo, la app siempre muestra a quién se la envías (aunque tengas un solo amigo) antes de enviarla.",
            "Chuba y Pepa recomiendan: unos 3 días a la semana, al azar, una de las dos te recomienda una película según su gusto, con nota y explicación (aviso y tarjeta en Juntos). Viene apagado: se activa en Ajustes, en \"Chuba y Pepa recomiendan\".",
        ),
        "1.8.5" to listOf(
            "Títulos y sinopsis en español de Latinoamérica (antes se pedían en español de España, con títulos como \"Resacón\" en vez de \"¿Qué pasó ayer?\"). En Ajustes puedes elegir Latinoamérica, Chile o España. Lo que ya tenías marcado se actualiza solo, de a poco.",
        ),
        "1.8.6" to listOf(
            "Botón \"Actualizar títulos de mis películas\" en Ajustes: vuelve a descargar ahora el título y la sinopsis de lo que ya tienes marcado, en el idioma elegido.",
        ),
        "1.8.7" to listOf(
            "20 calcomanías nuevas de Chuba y Pepa (cámara, astronauta, detective, pirata, magas, vaquera, princesa y más): ahora hay 38 que van rotando, en otro orden cada vez que abres la app.",
        ),
        "1.8.8" to listOf(
            "Las listas de Chuba y Pepa ahora cambian solas: cada 6 horas piden otras películas y otro orden (y \"Ver otras\" trae más al instante), en vez de mostrar siempre las mismas.",
        ),
        "1.8.9" to listOf(
            "El comienzo de las listas de Chuba y Pepa también cambia: las de nota 9 y 10 se mezclan entre sí en cada ronda, en vez de quedar siempre las mismas arriba.",
        ),
        "1.9.0" to listOf(
            "El gusto de Chuba y Pepa ahora también mira las palabras clave de cada película: la violencia, las peleas y el ruido bajan la nota de Pepa (aunque el género diga drama, como El club de la pelea), y los perros suben la de las dos.",
        ),
        "1.9.1" to listOf(
            "Al marcar «La vi y me gustó» dentro de una película, la nota se pide ahí mismo, sin tener que salir.",
        ),
        "1.9.2" to listOf(
            "Los comentarios de Chuba y Pepa ahora hablan en primera persona y según el género de cada película (Chuba con la comida y las persecuciones, Pepa con el ruido, los sustos y su rincón).",
        ),
        "1.9.3" to listOf(
            "Chuba y Pepa opinan con más de sus manías: Chuba persigue perros y ladra primero; Pepa mira de lejos, se esconde con el ruido, se queda en la cabecera y se va relajando de a poco.",
        ),
        "1.9.4" to listOf(
            "Las tarjetas de las listas muestran un broche diminuto sobre la carátula: trofeo dorado si la película ganó premios, verde si solo tiene nominaciones (cuando ya se conocen sus premios).",
        ),
        "1.9.5" to listOf(
            "Los avisos llevan una calcomanía distinta de Chuba y Pepa cada vez (la recomendación semanal usa la de la perrita que recomienda).",
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
