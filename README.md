# Peli

App Android (Kotlin + Jetpack Compose + Room) para decidir qué películas ver en pareja.

## Qué hace
- **Buscar**: películas de TMDB con carátula y descripción; marca ❤ favorita o ✓ ya la vi.
- **Descubrir**: la app te muestra sugerencias (basadas en tus favoritas) y aprendes tus gustos con Sí / No / Omitir.
- **Mis pelis**: tu lista, guardada en base de datos local (Room). Botón *Compartir* genera un código para tu pareja.
- **Juntos**: importas el código de tu pareja y la app compara ambas listas, mostrando las películas favoritas de alguno que ninguno ha visto ni descartado (primero las que les gustan a los dos).

## Configuración
1. Crea una API key gratis en https://www.themoviedb.org/settings/api
2. En `local.properties` agrega: `TMDB_API_KEY=tu_clave`
3. Abre la carpeta en Android Studio y ejecuta.

## Siguiente paso posible
Sincronizar las listas automáticamente con un backend (p. ej. Firestore) en vez de intercambiar el código a mano.
