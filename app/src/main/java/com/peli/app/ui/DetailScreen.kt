package com.peli.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.peli.app.data.Entry
import com.peli.app.ui.components.GlassCard
import com.peli.app.ui.components.GradientButton
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.PosterImage
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.SectionSpacing
import com.peli.app.ui.components.StatusChip
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.components.UpcomingChip
import com.peli.app.ui.components.sharedPoster
import com.peli.app.ui.components.sharedTitle

@Composable
fun DetailScreen(entry: Entry, mine: Entry?, vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scheme = MaterialTheme.colorScheme
    val favorite = mine?.favorite == true
    val seen = mine?.seen == true
    val disliked = mine?.disliked == true

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PosterImage(
                path = entry.posterPath,
                contentDescription = "Carátula de ${entry.title}",
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).sharedPoster(entry.movieId),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            Column(
                Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = SectionSpacing),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                GlassCard {
                    Text(entry.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.sharedTitle(entry.movieId))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.dateLabel.isNotBlank()) StatusChip(entry.dateLabel)
                        if (entry.isUpcoming) UpcomingChip()
                    }
                    Text(
                        entry.overview.ifBlank { "Sin descripción" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = ItemSpacing),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                    GradientButton(
                        if (favorite) "En favoritas" else "Agregar a favoritas",
                        { vm.toggleFavorite(mine ?: entry) },
                        Modifier.fillMaxWidth(),
                        icon = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    )
                    TonalButton(
                        if (seen) "Marcada como vista" else "Ya la vi",
                        { vm.toggleSeen(mine ?: entry) },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Default.Check,
                    )
                    TonalButton(
                        if (disliked) "Marcada como no me gusta" else "No me gusta",
                        { vm.toggleDislike(mine ?: entry) },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Default.ThumbDown,
                    )
                }
                Box(Modifier.height(32.dp))
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .clip(CircleShape)
                .background(scheme.surface.copy(alpha = 0.6f))
                .align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
    }
}
