package com.seventv.stickers.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil.Coil
import com.seventv.stickers.SevenTvApplication
import com.seventv.stickers.ui.components.CoilSetup
import com.seventv.stickers.ui.navigation.AppNavigation
import com.seventv.stickers.ui.theme.SevenTvTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Coil singleton image loader with animated WebP/GIF decoder
        Coil.setImageLoader(CoilSetup.createImageLoader(this))

        val appContainer = (application as SevenTvApplication).appContainer

        setContent {
            SevenTvTheme {
                AppNavigation(container = appContainer)
            }
        }
    }
}
