package com.peli.app

import com.peli.app.data.Entry
import com.peli.app.data.OWNER_ME

fun entry(
    id: Int = 1,
    owner: String = OWNER_ME,
    title: String = "Título $id",
    year: String = "2020",
    genres: String? = null,
    rating: Double? = null,
    updatedAt: Long = 0,
    watchlist: Boolean = false,
    liked: Boolean = false,
    seen: Boolean = false,
    disliked: Boolean = false,
    myRating: Int = 0,
) = Entry(
    movieId = id, owner = owner, title = title, overview = "o", posterPath = null, year = year,
    liked = liked, seen = seen, disliked = disliked, watchlist = watchlist,
    genres = genres, rating = rating, updatedAt = updatedAt, myRating = myRating,
)
