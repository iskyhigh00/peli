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

## Estados de cada película
- **Quiero verla** → lista que se comparte y compara con tu pareja («Juntos»).
- **La vi y me gustó** → enseña tus gustos a la app (implica «la vi»).
- **No me gusta / no me interesa** → señal negativa; no vuelve a aparecer en «Descubrir».
- **Ya la vi (sin opinión)** → sale de lo pendiente, sin afectar las recomendaciones.

## Recomendador (`data/Recommender.kt`)
Perfil de géneros (me gustó +1, quiero verla +0.5, no me gusta −0.8/−1, más peso a lo reciente) y puntaje
por candidata: 50% afinidad de géneros, 30% nota de TMDB, 20% cuántas de tus «me gustó» la recomiendan.
Las candidatas salen de las recomendaciones de TMDB para tus últimas películas, de las mejor valoradas de tus
géneros favoritos y de algunas populares (exploración). Los datos viven en Room (base local);
`Repository` es el punto donde se conectaría una base remota para sincronizar la pareja.

## Valoraciones y reseñas
En el detalle de cada película: nota de **TMDB** (con reseñas de usuarios), y con una clave gratuita de OMDb también
**IMDb**, **Rotten Tomatoes** (crítica) y **Metacritic**. Tu nota (1–10) se guarda, mejora el recomendador y, con una
sala, tu pareja ve la suya. En Ajustes (engranaje) eliges qué fuentes mostrar.
Clave de OMDb: https://www.omdbapi.com/apikey.aspx → secreto `OMDB_API_KEY` (o `local.properties`).
Si la tabla de Supabase ya existía, correr de nuevo `supabase/schema.sql` (agrega la columna `my_rating`).

## Salas con tu pareja (Supabase)
Cada teléfono tiene un id anónimo; uno crea una sala (código de 10 caracteres) y el otro se une. Cada cambio
(quiero verla / me gustó / no me gusta) se sube a la tabla `entries` y cada 6 s se descarga lo de la pareja.
Cuando una película está en «quiero verla» de los dos aparece **«¡Es un match!»** y queda en «Juntos».
En «Descubrir» lo que le interesa a tu pareja sale primero. Sin servidor configurado, la app sigue
funcionando con el intercambio manual de códigos.

Configuración (una vez):
1. Crear un proyecto gratis en https://supabase.com.
2. SQL Editor → pegar `supabase/schema.sql` → Run.
3. Project Settings → API: copiar **Project URL** y la clave **anon public** como secretos de GitHub
   `SUPABASE_URL` y `SUPABASE_ANON_KEY` (o en `local.properties` para desarrollo).

Seguridad: la tabla usa RLS; una fila solo se lee/escribe si la petición trae el header `x-room` con el código de
esa sala, así que el código es el secreto compartido de la pareja (no lo publiques).

## Rediseño (Material 3 Expressive)
Tema propio en `ui/theme` (colores, Inter Variable en `res/font`, formas) y componentes en `ui/components`
(`GlassCard`, `GradientButton`, `ShimmerBox`, `AnimatedNavigationBar`…). Material You está disponible con
`PeliTheme(dynamicColor = true)`; por defecto se usa la paleta de marca.

## Auto-actualización
Al abrir, la app consulta el último Release de un repo privado de APK y, si hay versión nueva, muestra un banner
con «Actualizar» (descarga con progreso e instala encima, conservando los datos). Código en `update/Updater.kt`;
el workflow `.github/workflows/android-build.yml` compila, firma y publica `Peli.apk` + `version.txt`.

Versión: `x.y.z` con un dígito por campo (máx 9.9.9), definida solo en `appVersion` (app/build.gradle.kts);
`versionCode` se calcula (x*100+y*10+z). Para publicar: `scripts/bump-version.sh`, agregar las novedades en
`update/Changelog.kt` y hacer push: CI crea el release `vX.Y.Z` con `Peli.apk` y `version.txt`.
Mientras el repo sea público la app mira el último release de este repo; si CI recibe `APK_READ_TOKEN`
usa el repo privado `peli-apk`.

### Configuración en GitHub (una sola vez)
1. Crear el repo **privado** `iskyhigh00/peli-apk` (si usas otro nombre, cámbialo en `Updater.REPO`) y un Release con tag `peli-apk`.
2. Token fine-grained **solo lectura** (Contents: Read) sobre `peli-apk` → secreto `APK_READ_TOKEN` en este repo.
3. Token fine-grained **Contents: Read and write** sobre `peli-apk` → secreto `APK_PUBLISH_TOKEN`; secreto `APK_REPO` = `iskyhigh00/peli-apk`.
4. Keystore (`keytool -genkeypair -v -keystore peli.jks -alias peli -keyalg RSA -keysize 2048 -validity 10000`) →
   `base64 -w0 peli.jks` en `KEYSTORE_B64`, más `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Usar siempre la misma llave.
5. Secreto `TMDB_API_KEY` con tu clave de TMDB.
