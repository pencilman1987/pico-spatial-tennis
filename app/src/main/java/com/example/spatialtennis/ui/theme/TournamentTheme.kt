package com.example.spatialtennis.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.defaultColorScheme
import com.pico.spatial.ui.foundation.vibrant.Vibrant
import com.pico.spatial.ui.foundation.vibrant.withVibrant

@Composable
fun TournamentTheme(content: @Composable () -> Unit) {
    val colors =
        remember {
            defaultColorScheme(
                fillPrimary = Color(0xF237493B).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally forest glass
                fillSecondary = Color(0xE8495E4B).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally moss
                fillTertiary = Color(0xD15E725A).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally elevated moss
                fillLight = Color(0xFFFFF5DF).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally cream
                labelPrimaryLight = Color(0xFFFFF8E9).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally bright cream
                labelPrimary = Color(0xFFFFF8E9).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally primary label
                labelSecondary = Color(0xFFD7E0C8).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally secondary label
                labelTertiary = Color(0xFFB6C5A7).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally tertiary label
                labelQuaternary = Color(0xFF8DA083).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally quaternary label
                lightenHover = Color(0x246FD69C).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally hover
                lightenPressed = Color(0x3D6FD69C).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally pressed
                error = Color(0xFFFF8068).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally coral
                alert = Color(0xFFF7C85B).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally honey
                passable = Color(0xFF79D9A5).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally leaf mint
                interaction = Color(0xFF75C8DE).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally sky
                dividerLine = Color(0x4DFFF8E9).withVibrant(Vibrant.None), // design-style: fixed-figma-color Island Rally divider
            )
        }
    PicoTheme(colorScheme = colors, content = content)
}
