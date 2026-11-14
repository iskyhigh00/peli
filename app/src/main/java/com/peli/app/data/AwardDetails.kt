package com.peli.app.data

/** Un premio o nominación de una película: a qué categoría, si lo ganó, quién (si fue una persona) y el año. */
data class AwardRow(val award: String, val won: Boolean, val who: String?, val year: Int?)

/**
 * Detalle de premios y nominaciones. Los datos vienen de Wikidata (OMDb solo da totales): categoría, ganador o nominado
 * (la película o una persona por su trabajo en ella) y año. Wikidata no siempre está completo.
 */
object AwardDetails {
    private val imdbId = Regex("""^tt\d{5,10}$""")
    private val unlabeled = Regex("""^Q\d+$""")

    /** Consulta SPARQL para las nominaciones y premios de la película y de las personas por su trabajo en ella; null si el id no es de IMDb. */
    fun query(imdb: String): String? {
        if (!imdbId.matches(imdb)) return null
        return """
            SELECT ?awardLabel ?won ?whoLabel ?year WHERE {
              ?film wdt:P345 "$imdb".
              {
                { ?film p:P1411 ?st. ?st ps:P1411 ?award. BIND(false AS ?won) }
                UNION
                { ?film p:P166 ?st. ?st ps:P166 ?award. BIND(true AS ?won) }
                BIND(?film AS ?who)
              } UNION {
                { ?who p:P1411 ?st. ?st ps:P1411 ?award; pq:P1686 ?film. BIND(false AS ?won) }
                UNION
                { ?who p:P166 ?st. ?st ps:P166 ?award; pq:P1686 ?film. BIND(true AS ?won) }
              }
              OPTIONAL { ?st pq:P585 ?date. BIND(YEAR(?date) AS ?year) }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "es,en". }
            }
            LIMIT 150
        """.trimIndent()
    }

    /** Limpia lo que llega: sin repetidos, sin etiquetas sin traducir (Q123) y sin repetir el nombre de la propia película como "quién". */
    fun clean(rows: List<AwardRow>, filmTitles: Set<String>): List<AwardRow> {
        val titles = filmTitles.map { it.trim().lowercase() }.toSet()
        return rows.mapNotNull { r ->
            val award = r.award.trim()
            if (award.isEmpty() || unlabeled.matches(award)) return@mapNotNull null
            val who = r.who?.trim()?.takeIf { it.isNotEmpty() && !unlabeled.matches(it) && it.lowercase() !in titles }
            AwardRow(award, r.won, who, r.year)
        }.distinct()
    }

    /** Ganados primero; dentro de cada grupo lo más reciente arriba. */
    fun sorted(rows: List<AwardRow>): List<AwardRow> =
        rows.sortedWith(compareByDescending<AwardRow> { it.won }.thenByDescending { it.year ?: 0 }.thenBy { it.award }.thenBy { it.who.orEmpty() })

    /** «Premio Óscar a la mejor actriz — Nombre (1999)». */
    fun line(r: AwardRow): String = buildString {
        append(r.award)
        r.who?.let { append(" — ").append(it) }
        r.year?.let { append(" (").append(it).append(")") }
    }
}
