package com.peli.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de orden del código que se revisan solas en cada compilación, para que no se deshagan sin que nadie lo note. */
class SourceGuardTest {
    private val root: File = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }
    private val files: List<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun codeLines(f: File) = f.readLines().withIndex().filter { (_, l) ->
        val t = l.trim()
        t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
    }

    /** Todas las conexiones a internet son https; solo Jellyfin (tu servidor, p. ej. por Tailscale) puede ser http. */
    @Test fun `solo Jellyfin usa direcciones http sin cifrar`() {
        val allowed = setOf("Jellyfin.kt", "JellyfinClient.kt", "SearchLink.kt")
        val bad = files.filter { it.name !in allowed }.flatMap { f ->
            codeLines(f).filter { (_, l) -> l.contains("\"http://") }.map { (i, _) -> "${f.name}:${i + 1}" }
        }
        assertTrue("direcciones http fuera de Jellyfin: $bad", bad.isEmpty())
    }

    /** MainViewModel no vuelve a crecer sin límite: lo nuevo va en su propio módulo (ui/features). */
    @Test fun `la API social no usa Map con listas (Retrofit rechaza el comodin)`() {
        val src = java.io.File("src/main/java/com/peli/app/data/Account.kt").readText()
        val bad = Regex("""@Body\s+\w+:\s*Map<String,\s*(List|Set)<""").containsMatchIn(src)
        org.junit.Assert.assertFalse("Usa una clase de datos como cuerpo", bad)
    }

    @Test fun `MainViewModel se mantiene acotado`() {
        val lines = files.first { it.name == "MainViewModel.kt" }.readLines().size
        assertTrue("MainViewModel tiene $lines líneas (máximo 1600): saca lo nuevo a ui/features", lines <= 1600)
    }

    /** Los módulos de ui/features son lógica sin pantallas: no importan Compose. */
    @Test fun `los modulos de features no dependen de Compose`() {
        val bad = files.filter { it.path.replace('\\', '/').contains("/ui/features/") }
            .filter { f -> f.readLines().any { it.startsWith("import androidx.compose") } }.map { it.name }
        assertTrue("importan Compose: $bad", bad.isEmpty())
    }
}
