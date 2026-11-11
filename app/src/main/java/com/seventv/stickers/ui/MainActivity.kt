package com.seventv.stickers.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.seventv.stickers.SevenTvApplication
import com.seventv.stickers.ui.navigation.AppNavigation
import com.seventv.stickers.ui.theme.SevenTvTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appContainer = (application as SevenTvApplication).appContainer

        setContent {
            SevenTvTheme {
                AppNavigation(container = appContainer)
            }
        }
    }
}
