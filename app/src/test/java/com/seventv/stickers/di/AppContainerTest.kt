package com.seventv.stickers.di

import android.content.Context
import com.seventv.stickers.SevenTvApplication
import com.seventv.stickers.data.local.AppDatabase
import com.seventv.stickers.data.local.storage.DefaultStickerStorageManager
import com.seventv.stickers.data.local.storage.StickerStorageManager
import com.seventv.stickers.data.repository.DefaultSevenTvRepository
import com.seventv.stickers.data.repository.DefaultStickerPackRepository
import com.seventv.stickers.data.repository.SevenTvRepository
import com.seventv.stickers.data.repository.StickerPackRepository
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppContainerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        AppDatabase.setTestInstance(null)
    }

    @Test
    fun defaultAppContainer_initializesAllDependenciesAsNonNullSingletons() {
        val container: AppContainer = DefaultAppContainer(context)

        assertNotNull("database must not be null", container.database)
        assertNotNull("storageManager must not be null", container.storageManager)
        assertNotNull("sevenTvRepository must not be null", container.sevenTvRepository)
        assertNotNull("stickerPackRepository must not be null", container.stickerPackRepository)

        assertTrue(container.storageManager is DefaultStickerStorageManager)
        assertTrue(container.sevenTvRepository is DefaultSevenTvRepository)
        assertTrue(container.stickerPackRepository is DefaultStickerPackRepository)

        // Verify reference stability (lazy/singleton properties)
        assertSame(container.database, container.database)
        assertSame(container.storageManager, container.storageManager)
        assertSame(container.sevenTvRepository, container.sevenTvRepository)
        assertSame(container.stickerPackRepository, container.stickerPackRepository)
    }

    @Test
    @Config(application = SevenTvApplication::class)
    fun sevenTvApplication_initializesAppContainerOnCreate() {
        val application = RuntimeEnvironment.getApplication() as SevenTvApplication
        assertNotNull("application.appContainer should be initialized in onCreate", application.appContainer)
        assertNotNull(application.appContainer.database)
        assertNotNull(application.appContainer.storageManager)
        assertNotNull(application.appContainer.sevenTvRepository)
        assertNotNull(application.appContainer.stickerPackRepository)
    }
}
