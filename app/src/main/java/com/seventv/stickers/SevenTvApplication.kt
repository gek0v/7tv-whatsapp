package com.seventv.stickers

import android.app.Application
import com.seventv.stickers.di.AppContainer
import com.seventv.stickers.di.DefaultAppContainer

class SevenTvApplication : Application() {

    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}
