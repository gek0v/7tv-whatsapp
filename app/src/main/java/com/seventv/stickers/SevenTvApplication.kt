package com.seventv.stickers

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.seventv.stickers.di.AppContainer
import com.seventv.stickers.di.DefaultAppContainer
import com.seventv.stickers.ui.components.CoilSetup

class SevenTvApplication : Application(), ImageLoaderFactory {

    lateinit var appContainer: AppContainer
    val container: AppContainer get() = appContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return CoilSetup.createImageLoader(this)
    }
}
