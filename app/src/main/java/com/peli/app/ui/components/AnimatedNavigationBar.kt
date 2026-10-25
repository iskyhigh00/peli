package com.peli.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.peli.app.ui.theme.PeliTheme

data class NavItem(val label: String, val icon: ImageVector)

/**
 * Barra inferior flotante de vidrio. La píldora indicadora se desplaza entre pestañas con
 * `animateDpAsState` (spring). Mide 64dp + 8dp de margen + inset de navegación del sistema.
 */
@Composable
fun AnimatedNavigationBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
            .navigationBarsPadding()
            .height(64.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(0.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / items.size
            val pillOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "pillOffset",
            )
            androidx.compose.foundation.layout.Box(
                Modifier
                    .offset(x = pillOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.horizontalGradient(
                            listOf(scheme.primary.copy(alpha = 0.30f), scheme.tertiary.copy(alpha = 0.22f)),
                        ),
                    ),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val tint by animateColorAsState(
                        if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                        label = "navTint",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .clip(MaterialTheme.shapes.large)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun AnimatedNavigationBarPreview() {
    PeliTheme {
        BrandBackground {
            var selected by remember { mutableIntStateOf(1) }
            AnimatedNavigationBar(
                items = listOf(
                    NavItem("Buscar", Icons.Default.Search),
                    NavItem("Descubrir", Icons.Default.Explore),
                    NavItem("Mis pelis", Icons.Default.Movie),
                    NavItem("Juntos", Icons.Default.Group),
                ),
                selectedIndex = selected,
                onSelect = { selected = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
