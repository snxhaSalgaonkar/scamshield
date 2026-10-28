package com.scamguard.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scamguard.app.R

/** The app's shield mark, tinted to reflect protection state. */
@Composable
fun ShieldIcon(tint: Color, contentDescription: String?, size: Dp = 96.dp) {
    Image(
        painter = painterResource(R.drawable.ic_shield),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size),
    )
}
