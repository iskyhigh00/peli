package com.peli.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Database
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.peli.app.log.AppLog
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EntryDao {
    @Query("SELECT * FROM entries WHERE owner = :owner ORDER BY title")
    abstract fun observe(owner: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE owner = :owner AND movieId = :id")
    abstract suspend fun get(owner: String, id: Int): Entry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entry: Entry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entries: List<Entry>)

    @Query("DELETE FROM entries WHERE owner = :owner AND movieId = :id")
    abstract suspend fun delete(owner: String, id: Int)

    @Query("DELETE FROM entries WHERE owner = :owner")
    abstract suspend fun clear(owner: String)

    @Query("SELECT * FROM entries WHERE owner = :owner")
    abstract suspend fun all(owner: String): List<Entry>

    @Query("SELECT COUNT(*) FROM entries WHERE owner = :owner")
    abstract suspend fun count(owner: String): Int

    /** Completa el título original solo donde aún no se conocía; no cuenta como cambio de mi marca. */
    @Query("UPDATE entries SET originalTitle = :title WHERE owner = :owner AND movieId = :id AND originalTitle IS NULL")
    abstract suspend fun setOriginalTitle(owner: String, id: Int, title: String)

    @Query("UPDATE entries SET owner = :to WHERE owner = :from")
    abstract suspend fun rename(from: String, to: String)

    /**
     * Cambia la colección activa SIN borrar nada: la activa (OWNER_ME) se aparta bajo el nombre de su ámbito
     * y se trae la guardada del ámbito nuevo (vacía si no había).
     */
    @Transaction
    open suspend fun switchScope(fromScope: String, toScope: String) {
        if (fromScope == toScope) return
        clear(Scopes.stash(fromScope))
        rename(OWNER_ME, Scopes.stash(fromScope))
        rename(Scopes.stash(toScope), OWNER_ME)
    }

    @Transaction
    open suspend fun replaceAll(owner: String, entries: List<Entry>) {
        clear(owner)
        upsertAll(entries)
    }
}

/** Cola de envíos pendientes (una fila por destino y película). */
@Entity(tableName = "outbox", primaryKeys = ["scope", "movieId"])
data class OutboxRow(
    val scope: String,
    val movieId: Int,
    val rev: Long,
    val payload: String,
    val createdAt: Long,
    val attempts: Int,
)

@Dao
abstract class OutboxDao {
    @Query("SELECT * FROM outbox WHERE scope = :scope AND movieId = :id")
    abstract suspend fun get(scope: String, id: Int): OutboxRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(row: OutboxRow)

    @Query("SELECT * FROM outbox WHERE scope = :scope ORDER BY createdAt, movieId LIMIT :limit")
    abstract suspend fun batch(scope: String, limit: Int): List<OutboxRow>

    @Query("DELETE FROM outbox WHERE scope = :scope AND movieId = :id AND rev = :rev")
    abstract suspend fun deleteIfRev(scope: String, id: Int, rev: Long): Int

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE scope = :scope AND movieId = :id AND rev = :rev")
    abstract suspend fun fail(scope: String, id: Int, rev: Long)

    @Query("SELECT COUNT(*) FROM outbox WHERE scope = :scope")
    abstract suspend fun count(scope: String): Int
}

/** Une la lógica pura de [Outbox] con Room. */
class RoomOutboxTable(private val dao: OutboxDao) : OutboxTable {
    private fun OutboxRow.toItem() = OutboxItem(scope, movieId, rev, payload, createdAt, attempts)

    override suspend fun get(scope: String, movieId: Int) = dao.get(scope, movieId)?.toItem()
    override suspend fun put(item: OutboxItem) =
        dao.put(OutboxRow(item.scope, item.movieId, item.rev, item.payload, item.createdAt, item.attempts))
    override suspend fun batch(scope: String, limit: Int) = dao.batch(scope, limit).map { it.toItem() }
    override suspend fun deleteIfRev(scope: String, movieId: Int, rev: Long) = dao.deleteIfRev(scope, movieId, rev) > 0
    override suspend fun recordFailure(scope: String, movieId: Int, rev: Long) = dao.fail(scope, movieId, rev)
    override suspend fun count(scope: String) = dao.count(scope)
}

/** Datos extra de una película (ver [MovieFacts]); las listas se guardan separadas por "|". */
@Entity(tableName = "facts")
data class FactsRow(
    @PrimaryKey val movieId: Int,
    val imdbId: String?,
    val keywords: String,
    val director: String?,
    val actors: String,
    val tmdbAt: Long,
    val imdbRating: Double?,
    val imdbVotes: Int?,
    val rottenTomatoes: Int?,
    val metacritic: Int?,
    val oscarWins: Int,
    val oscarNominations: Int,
    val wins: Int,
    val nominations: Int,
    val omdbAt: Long,
) {
    fun toFacts() = MovieFacts(
        movieId, imdbId, keywords.split('|').filter { it.isNotBlank() }, director,
        actors.split('|').filter { it.isNotBlank() }, tmdbAt, imdbRating, imdbVotes, rottenTomatoes, metacritic,
        oscarWins, oscarNominations, wins, nominations, omdbAt,
    )
}

fun MovieFacts.toRow() = FactsRow(
    movieId, imdbId, keywords.joinToString("|") { it.replace('|', ' ') }, director,
    actors.joinToString("|") { it.replace('|', ' ') }, tmdbAt, imdbRating, imdbVotes, rottenTomatoes, metacritic,
    oscarWins, oscarNominations, wins, nominations, omdbAt,
)

@Dao
abstract class FactsDao {
    @Query("SELECT * FROM facts WHERE movieId IN (:ids)")
    abstract suspend fun get(ids: List<Int>): List<FactsRow>

    @Query("SELECT * FROM facts WHERE movieId = :id")
    abstract suspend fun one(id: Int): FactsRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(row: FactsRow)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        AppLog.i("DB", "migración 1→2")
        db.execSQL("ALTER TABLE entries ADD COLUMN releaseDate TEXT")
    }
}

/** "favorite" pasa a significar "la vi y me gustó"; las que solo me interesaban pasan a "quiero verla". */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        AppLog.i("DB", "migración 2→3")
        db.execSQL("ALTER TABLE entries ADD COLUMN watchlist INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE entries ADD COLUMN genres TEXT")
        db.execSQL("ALTER TABLE entries ADD COLUMN rating REAL")
        db.execSQL("ALTER TABLE entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE entries SET watchlist = 1, favorite = 0 WHERE favorite = 1 AND seen = 0")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        AppLog.i("DB", "migración 3→4")
        db.execSQL("ALTER TABLE entries ADD COLUMN myRating INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        AppLog.i("DB", "migración 4→5 (cola de envíos)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS outbox (scope TEXT NOT NULL, movieId INTEGER NOT NULL, rev INTEGER NOT NULL, " +
                "payload TEXT NOT NULL, createdAt INTEGER NOT NULL, attempts INTEGER NOT NULL, PRIMARY KEY(scope, movieId))",
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        AppLog.i("DB", "migración 5→6 (datos extra de películas)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS facts (movieId INTEGER NOT NULL, imdbId TEXT, keywords TEXT NOT NULL, " +
                "director TEXT, actors TEXT NOT NULL, tmdbAt INTEGER NOT NULL, imdbRating REAL, imdbVotes INTEGER, " +
                "rottenTomatoes INTEGER, metacritic INTEGER, oscarWins INTEGER NOT NULL, oscarNominations INTEGER NOT NULL, " +
                "wins INTEGER NOT NULL, nominations INTEGER NOT NULL, omdbAt INTEGER NOT NULL, PRIMARY KEY(movieId))",
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        AppLog.i("DB", "migración 6→7 (título original)")
        db.execSQL("ALTER TABLE entries ADD COLUMN originalTitle TEXT")
    }
}

@Database(entities = [Entry::class, OutboxRow::class, FactsRow::class], version = 7, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun outbox(): OutboxDao
    abstract fun facts(): FactsDao

    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, AppDb::class.java, "peli.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        AppLog.i("DB", "base de datos abierta (versión ${db.version})")
                    }
                })
                .build()
    }
}
