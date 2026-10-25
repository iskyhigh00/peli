package com.peli.app.ui.components

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Preview en oscuro y claro. */
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class ThemePreviews
