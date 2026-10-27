package com.peli.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.peli.app.auth.GoogleResult
import com.peli.app.auth.GoogleSignIn
import com.peli.app.data.Friend
import com.peli.app.data.FriendCodes
import com.peli.app.data.Usernames
import com.peli.app.ui.components.CompactSpacing
import com.peli.app.ui.components.GradientButton
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.PeliFilterChip
import com.peli.app.ui.components.PeliTextField
import com.peli.app.ui.components.TonalButton
import kotlinx.coroutines.launch

/** Contenido de la tarjeta de cuenta en «Juntos»: entrar con Google, elegir usuario, ID y amigos. */
@Composable
fun AccountCardContent(vm: MainViewModel, syncError: String?) {
    val signedIn by vm.signedIn.collectAsState()
    val profile by vm.profile.collectAsState()
    val busy by vm.accountBusy.collectAsState()
    when {
        !signedIn -> SignInPrompt(vm, busy)
        profile == null -> UsernamePrompt(vm, busy)
        else -> FriendsPanel(vm, profile!!.username, FriendCodes.format(profile!!.friend_code), busy, syncError)
    }
}

@Composable
private fun SignInPrompt(vm: MainViewModel, busy: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Text("Conecta con tus amigos", style = MaterialTheme.typography.titleMedium)
    Text(
        "Entra con Google, elige un nombre de usuario y tendrás un ID para que tu pareja, familia o amigos te agreguen. " +
            "Así ven qué películas pueden ver juntos.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(ItemSpacing))
    GradientButton(
        if (busy) "Entrando…" else "Continuar con Google",
        {
            scope.launch {
                when (val r = GoogleSignIn.request(context)) {
                    is GoogleResult.Ok -> vm.signInWithGoogle(r.token)
                    is GoogleResult.Failed -> vm.showMessage(r.message)
                    GoogleResult.Cancelled -> Unit
                }
            }
        },
        Modifier.fillMaxWidth(),
        icon = Icons.Default.Group,
        enabled = !busy,
    )
}

@Composable
private fun UsernamePrompt(vm: MainViewModel, busy: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    val email by vm.accountEmail.collectAsState()
    val problem = Usernames.explain(name)
    Text("Elige tu nombre de usuario", style = MaterialTheme.typography.titleMedium)
    Text(
        "${if (email != null) "Sesión con $email. " else ""}Tus amigos te verán con este nombre. Al guardarlo se genera tu ID de amigo.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(ItemSpacing))
    PeliTextField(
        name, { name = it.take(20) }, "Nombre de usuario",
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.createProfile(name) }),
    )
    if (problem != null) {
        Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(ItemSpacing))
    Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
        GradientButton(
            if (busy) "Guardando…" else "Guardar",
            { vm.createProfile(name) },
            Modifier.weight(1f),
            enabled = !busy && Usernames.isValid(name),
        )
        TonalButton("Salir", vm::signOut)
    }
}

@Composable
private fun FriendsPanel(vm: MainViewModel, username: String, code: String, busy: Boolean, syncError: String?) {
    val friends by vm.friends.collectAsState()
    val active by vm.activeFriend.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Friend?>(null) }

    Text("@$username", style = MaterialTheme.typography.titleMedium)
    Text(
        "Tu ID de amigo: $code",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        if (syncError != null) "No se pudo sincronizar: $syncError" else "Compártelo para que te agreguen.",
        style = MaterialTheme.typography.bodyMedium,
        color = if (syncError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(ItemSpacing))
    Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
        TonalButton(
            "Copiar ID",
            {
                clipboard.setText(AnnotatedString(code))
                vm.showMessage("ID copiado.")
            },
            Modifier.weight(1f),
            icon = Icons.Default.ContentCopy,
        )
        TonalButton(
            "Compartir",
            {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Agrégame en Peli para ver qué películas podemos ver juntos. Mi ID: $code (o busca @$username)")
                }
                context.startActivity(Intent.createChooser(send, "Compartir mi ID"))
            },
            Modifier.weight(1f),
            icon = Icons.Default.Share,
        )
    }

    Spacer(Modifier.height(ItemSpacing))
    Text("Agregar amigo", style = MaterialTheme.typography.titleSmall)
    PeliTextField(
        query, { query = it }, "ID o @usuario",
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.addFriend(query); query = "" }),
    )
    Spacer(Modifier.height(CompactSpacing))
    GradientButton(
        "Enviar solicitud",
        { vm.addFriend(query); query = "" },
        Modifier.fillMaxWidth(),
        enabled = !busy && query.trim().length >= 3,
    )

    Spacer(Modifier.height(ItemSpacing))
    Text("Amigos", style = MaterialTheme.typography.titleSmall)
    if (friends.isEmpty()) {
        Text(
            "Aún no tienes amigos agregados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    friends.forEach { f ->
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CompactSpacing),
        ) {
            Column(Modifier.weight(1f)) {
                Text("@${f.username}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        f.accepted && f.friend_id == active -> "Comparando listas"
                        f.accepted -> "Amigo"
                        f.incoming -> "Quiere ser tu amigo"
                        else -> "Solicitud enviada"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                f.accepted -> {
                    PeliFilterChip(
                        if (f.friend_id == active) "Comparando" else "Comparar",
                        selected = f.friend_id == active,
                        onClick = { vm.selectFriend(f.friend_id) },
                    )
                    TextButton(onClick = { confirmRemove = f }) { Text("Quitar") }
                }
                f.incoming -> {
                    TextButton(onClick = { vm.acceptFriend(f) }, enabled = !busy) { Text("Aceptar") }
                    TextButton(onClick = { vm.removeFriend(f) }, enabled = !busy) { Text("Rechazar") }
                }
                else -> TextButton(onClick = { vm.removeFriend(f) }, enabled = !busy) { Text("Cancelar") }
            }
        }
    }

    Spacer(Modifier.height(ItemSpacing))
    TonalButton("Cerrar sesión", { confirmSignOut = true }, Modifier.fillMaxWidth())

    val scheme = MaterialTheme.colorScheme
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Dejarás de comparar listas con tus amigos. Tus películas se conservan en este teléfono y en tu cuenta.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Cerrar sesión", color = scheme.error) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
            containerColor = scheme.surface,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurfaceVariant,
        )
    }
    confirmRemove?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("¿Quitar a @${f.username}?") },
            text = { Text("Dejarán de ver las listas del otro. Pueden volver a agregarse cuando quieran.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; vm.removeFriend(f) }) { Text("Quitar", color = scheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar") } },
            containerColor = scheme.surface,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurfaceVariant,
        )
    }
}

/** Al iniciar sesión con películas guardadas sin cuenta: nunca se suben en silencio, el usuario decide. */
@Composable
fun ClaimGuestDialog(claim: PendingClaim, onChoose: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = {},
        title = { Text("¿Usar las películas de este teléfono con tu cuenta?") },
        text = {
            Text(
                "Hay ${claim.count} películas guardadas en este teléfono sin cuenta. Si las usas, se suben a tu cuenta " +
                    "y tus amigos podrán verlas. Si no, quedan guardadas aparte (no se borran) y tu cuenta empieza vacía.",
            )
        },
        confirmButton = { TextButton(onClick = { onChoose(true) }) { Text("Usarlas") } },
        dismissButton = { TextButton(onClick = { onChoose(false) }) { Text("Empezar vacía") } },
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
    )
}
