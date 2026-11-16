package com.peli.app.ui.features

import android.app.Application
import com.peli.app.PeliApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Lo que necesitan en común los módulos que salieron de MainViewModel (servidor, enlaces, mensaje de bienvenida…):
 * la app, dónde lanzar trabajo (con el mismo manejo de errores) y dónde dar avisos al usuario.
 */
class VmEnv(
    val app: Application,
    val peli: PeliApp,
    private val scope: CoroutineScope,
    private val errors: CoroutineExceptionHandler,
    val message: MutableStateFlow<String?>,
) {
    fun launch(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(context + errors, block = block)

    val scopeForFlows: CoroutineScope get() = scope
}
