package com.peli.app.log

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Registra cada llamada de red: método, ruta (sin claves ni códigos), código y duración.
 * Las correctas van a nivel D (solo con registro detallado); los errores HTTP y de conexión, a W.
 */
class LogInterceptor(private val name: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val description = "$name ${request.method} ${LogFormat.sanitizeUrl(request.url.toString())}"
        val start = System.nanoTime()
        fun ms() = (System.nanoTime() - start) / 1_000_000
        return try {
            val response = chain.proceed(request)
            AppLog.log(
                if (response.isSuccessful) 'D' else 'W', "Red",
                "$description → ${response.code} (${ms()} ms)", null,
            )
            response
        } catch (e: IOException) {
            AppLog.w("Red", "$description ✗ ${e.javaClass.simpleName}: ${e.message} (${ms()} ms)")
            throw e
        }
    }
}
