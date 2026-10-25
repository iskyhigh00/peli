package com.peli.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.peli.app.ui.theme.PeliTheme

/** Rectángulo gris con barrido de brillo. Base de todos los skeletons. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).shimmer())
}

/** Skeleton con el mismo layout que [MovieCard]. */
@Composable
fun MovieCardSkeleton(modifier: Modifier = Modifier) {
    GlassCard(
        modifier.semantics { contentDescription = "Cargando película" },
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row {
            ShimmerBox(Modifier.width(96.dp).aspectRatio(2f / 3f), MaterialTheme.shapes.large)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.fillMaxWidth(0.7f).height(18.dp))
                ShimmerBox(Modifier.width(48.dp).height(12.dp))
                Spacer(Modifier.height(4.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(12.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(12.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.6f).height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(Modifier.size(40.dp), CircleShape)
                    ShimmerBox(Modifier.size(40.dp), CircleShape)
                }
            }
        }
    }
}

/** Skeleton de la tarjeta grande de "Descubrir". */
@Composable
fun DiscoverSkeleton(modifier: Modifier = Modifier) {
    GlassCard(modifier.semantics { contentDescription = "Cargando sugerencia" }) {
        ShimmerBox(Modifier.fillMaxWidth().height(340.dp), MaterialTheme.shapes.large)
        Spacer(Modifier.height(16.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.6f).height(24.dp))
        Spacer(Modifier.height(8.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(12.dp))
        Spacer(Modifier.height(8.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.8f).height(12.dp))
    }
}

@ThemePreviews
@Composable
private fun SkeletonPreview() {
    PeliTheme {
        BrandBackground {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MovieCardSkeleton()
                MovieCardSkeleton()
            }
        }
    }
}
