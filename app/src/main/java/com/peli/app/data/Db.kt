package com.peli.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Transaction
    open suspend fun replaceAll(owner: String, entries: List<Entry>) {
        clear(owner)
        upsertAll(entries)
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN releaseDate TEXT")
    }
}

/** "favorite" pasa a significar "la vi y me gustó"; las que solo me interesaban pasan a "quiero verla". */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN watchlist INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE entries ADD COLUMN genres TEXT")
        db.execSQL("ALTER TABLE entries ADD COLUMN rating REAL")
        db.execSQL("ALTER TABLE entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE entries SET watchlist = 1, favorite = 0 WHERE favorite = 1 AND seen = 0")
    }
}

@Database(entities = [Entry::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun entries(): EntryDao

    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, AppDb::class.java, "peli.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
