package com.peli.app

import android.app.Application
import com.peli.app.data.AppDb
import com.peli.app.data.PairingStore
import com.peli.app.data.Repository
import com.peli.app.data.TmdbApi

class PeliApp : Application() {
    val pairing by lazy { PairingStore(this) }
    val repository by lazy { Repository(AppDb.create(this), TmdbApi.create()) }
}
