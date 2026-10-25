package com.peli.app

import android.app.Application
import com.peli.app.data.AppDb
import com.peli.app.data.Repository
import com.peli.app.data.TmdbApi

class PeliApp : Application() {
    val repository by lazy { Repository(AppDb.create(this), TmdbApi.create()) }
}
