package com.seventv.stickers.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WhatsAppWhitelistCheckerTest {

    private lateinit var context: Context

    class MockWhitelistContentProvider : ContentProvider() {
        companion object {
            var cursorToReturn: Cursor? = null
            var exceptionToThrow: RuntimeException? = null
            var lastQueriedUri: Uri? = null

            fun reset() {
                cursorToReturn = null
                exceptionToThrow = null
                lastQueriedUri = null
            }
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            lastQueriedUri = uri
            exceptionToThrow?.let { throw it }
            return cursorToReturn
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        MockWhitelistContentProvider.reset()
        val providerInfo = ProviderInfo().apply {
            authority = WhatsAppConstants.WHITELIST_AUTHORITY
        }
        Robolectric.buildContentProvider(MockWhitelistContentProvider::class.java)
            .create(providerInfo)
            .get()
    }

    @After
    fun tearDown() {
        MockWhitelistContentProvider.reset()
    }

    @Test
    fun returnsTrue_whenResultIsOne() {
        val cursor = MatrixCursor(arrayOf(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)).apply {
            addRow(arrayOf(1))
        }
        MockWhitelistContentProvider.cursorToReturn = cursor

        val isWhitelisted = WhatsAppWhitelistChecker.isPackWhitelisted(context, "test_pack_1")

        assertTrue(isWhitelisted)
        assertEquals(
            "content://com.whatsapp.provider.sticker_whitelist_check/is_whitelisted?authority=com.seventv.stickers.stickercontentprovider&identifier=test_pack_1",
            MockWhitelistContentProvider.lastQueriedUri.toString()
        )
    }

    @Test
    fun returnsFalse_whenResultIsZero() {
        val cursor = MatrixCursor(arrayOf(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)).apply {
            addRow(arrayOf(0))
        }
        MockWhitelistContentProvider.cursorToReturn = cursor

        val isWhitelisted = WhatsAppWhitelistChecker.isPackWhitelisted(context, "test_pack_1")

        assertFalse(isWhitelisted)
    }

    @Test
    fun returnsFalse_whenCursorIsNull() {
        MockWhitelistContentProvider.cursorToReturn = null

        val isWhitelisted = WhatsAppWhitelistChecker.isPackWhitelisted(context, "test_pack_1")

        assertFalse(isWhitelisted)
    }

    @Test
    fun returnsFalse_whenCursorIsEmpty() {
        val cursor = MatrixCursor(arrayOf(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN))
        MockWhitelistContentProvider.cursorToReturn = cursor

        val isWhitelisted = WhatsAppWhitelistChecker.isPackWhitelisted(context, "test_pack_1")

        assertFalse(isWhitelisted)
    }

    @Test
    fun returnsFalse_whenResultColumnMissing() {
        val cursor = MatrixCursor(arrayOf("other_column")).apply {
            addRow(arrayOf("some_value"))
        }
        MockWhitelistContentProvider.cursorToReturn = cursor

        val isWhitelisted = WhatsAppWhitelistChecker.isPackWhitelisted(context, "test_pack_1")

        assertFalse(isWhitelisted)
    }

    @Test
    fun returnsFalse_whenQueryThrowsException() {
        MockWhitelistContentProvider.exceptionToThrow = SecurityException("Permission denial")

        val isWhitelisted = WhatsAppWhitelistChecker.isPackWhitelisted(context, "test_pack_1")

        assertFalse(isWhitelisted)
    }

    @Test
    fun queryUri_usesCustomAuthorityWhenProvided() {
        val cursor = MatrixCursor(arrayOf(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)).apply {
            addRow(arrayOf(1))
        }
        MockWhitelistContentProvider.cursorToReturn = cursor

        val isWhitelisted = WhatsAppWhitelistChecker.isPackWhitelisted(
            context = context,
            packIdentifier = "pack_custom",
            authority = "com.custom.authority"
        )

        assertTrue(isWhitelisted)
        assertEquals(
            "content://com.whatsapp.provider.sticker_whitelist_check/is_whitelisted?authority=com.custom.authority&identifier=pack_custom",
            MockWhitelistContentProvider.lastQueriedUri.toString()
        )
    }
}
