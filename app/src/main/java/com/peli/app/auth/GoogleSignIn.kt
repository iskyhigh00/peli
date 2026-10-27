package com.peli.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.peli.app.BuildConfig
import com.peli.app.log.AppLog
import java.security.MessageDigest
import java.security.SecureRandom

/** Token de Google más el nonce original (Supabase lo comprueba contra el hash que viaja en el token). */
data class GoogleToken(val idToken: String, val rawNonce: String)

sealed interface GoogleResult {
    data class Ok(val token: GoogleToken) : GoogleResult
    data object Cancelled : GoogleResult
    data class Failed(val message: String) : GoogleResult
}

object GoogleSignIn {
    private fun newNonce(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Abre el selector de cuentas de Google del teléfono. [activity] debe ser la Activity visible. */
    suspend fun request(activity: Context): GoogleResult {
        val raw = newNonce()
        return try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setNonce(sha256Hex(raw))
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                AppLog.i("Cuenta", "Google entregó el token de ${google.id.substringBefore('@').take(2)}…")
                GoogleResult.Ok(GoogleToken(google.idToken, raw))
            } else {
                AppLog.w("Cuenta", "Google devolvió un tipo inesperado: ${credential.type}")
                GoogleResult.Failed("Google devolvió una respuesta inesperada.")
            }
        } catch (e: GetCredentialCancellationException) {
            AppLog.i("Cuenta", "el usuario canceló el inicio de sesión con Google")
            GoogleResult.Cancelled
        } catch (e: GetCredentialException) {
            AppLog.w("Cuenta", "Google no pudo iniciar sesión (${e.type})", e)
            GoogleResult.Failed("Google no pudo iniciar sesión: ${e.message ?: e.type}")
        } catch (e: Exception) {
            AppLog.w("Cuenta", "falló el inicio de sesión con Google", e)
            GoogleResult.Failed("Falló el inicio de sesión: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
