package com.peli.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.peli.app.ui.theme.PeliTheme

/** Outlined con label flotante; el borde anima hacia primary (y se engrosa) al enfocar. */
@Composable
fun PeliTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    onClear: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge,
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingIcon = if (onClear != null && value.isNotEmpty()) {
            {
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Borrar texto") }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.onSurface.copy(alpha = 0.24f),
            focusedLabelColor = scheme.primary,
            cursorColor = scheme.primary,
            focusedContainerColor = scheme.surface.copy(alpha = 0.6f),
            unfocusedContainerColor = scheme.surface.copy(alpha = 0.4f),
        ),
    )
}

@ThemePreviews
@Composable
private fun PeliTextFieldPreview() {
    PeliTheme {
        BrandBackground {
            var text by remember { mutableStateOf("Interestelar") }
            PeliTextField(
                text, { text = it }, "Buscar película",
                Modifier.padding(20.dp), leadingIcon = Icons.Default.Search, onClear = { text = "" },
            )
        }
    }
}
