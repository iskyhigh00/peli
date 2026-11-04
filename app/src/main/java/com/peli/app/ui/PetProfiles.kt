package com.peli.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peli.app.data.Pet
import com.peli.app.ui.components.GlassCard

/** Cómo es cada perrita, para su perfil. */
private fun profileText(pet: Pet): Triple<String, String, String> = when (pet) {
    Pet.Chuba -> Triple(
        "Inquieta, ávida y siempre con hambre",
        "Persigue moscas y perros, ladra primero y corre como loca en la playa.",
        "Le gusta: acción, aventuras, comedia",
    )
    Pet.Pepa -> Triple(
        "Tranquila y de su espacio",
        "El ruido la molesta y, si hay peligro, arranca. Muy fiel con su gente.",
        "Le gusta: drama, romance, fantasía",
    )
}

/** Perfil de Chuba o Pepa: su calcomanía (que va cambiando), cómo es y qué películas le gustan. */
@Composable
fun PetProfileCard(pet: Pet, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (tagline, about, likes) = profileText(pet)
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, onClickLabel = "Ver las películas de ${pet.label}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RotatingSticker(Stickers.of(pet), Modifier.size(112.dp), periodMs = 11_000, offset = if (pet == Pet.Chuba) 0 else 3)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pet.label, style = MaterialTheme.typography.headlineSmall)
                Text(tagline, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Text(likes, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Text(
                    if (open) "Ocultar sus películas" else "Ver sus películas",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
