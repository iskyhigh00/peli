package com.peli.app.data

class Repository(private val db: AppDb, private val api: TmdbApi) {
    private val dao = db.entries()

    val mine = dao.observe(OWNER_ME)
    val partner = dao.observe(OWNER_PARTNER)

    suspend fun search(q: String) = api.search(q).results.map { it.toEntry(OWNER_ME) }
    suspend fun popular(page: Int) = api.popular(page).results.map { it.toEntry(OWNER_ME) }
    suspend fun recommendations(id: Int) = api.recommendations(id).results.map { it.toEntry(OWNER_ME) }

    /** Aplica un cambio sobre mi marca de la película; si queda vacía se borra. */
    suspend fun update(base: Entry, change: (Entry) -> Entry) {
        val current = dao.get(OWNER_ME, base.movieId) ?: base.copy(owner = OWNER_ME)
        val next = change(current)
        if (next.isEmpty) dao.delete(OWNER_ME, next.movieId) else dao.upsert(next)
    }

    suspend fun importPartner(entries: List<Entry>) = dao.replaceAll(OWNER_PARTNER, entries)
    suspend fun clearPartner() = dao.clear(OWNER_PARTNER)
}
