package com.seventv.stickers.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.seventv.stickers.core.media.animated.NativeWebPAnimEncoder
import com.seventv.stickers.whatsapp.StickerContentProvider
import com.seventv.stickers.whatsapp.WhatsAppConstants
import com.seventv.stickers.whatsapp.WhatsAppIntentHelper

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "7TV Stickers for WhatsApp"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val nativeStatus = if (NativeWebPAnimEncoder.isLoaded) {
            "✅ libseven_tv_webp.so cargada correctamente (libwebp 1.4.0)"
        } else {
            "⚠️ Biblioteca nativa no cargada"
        }

        val whatsAppInstalled = WhatsAppIntentHelper.isWhatsAppInstalled(this)
        val whatsAppStatus = if (whatsAppInstalled) {
            "✅ WhatsApp detectado e instalado"
        } else {
            "ℹ️ WhatsApp no detectado en este dispositivo"
        }

        val providerCheckStatus = try {
            val metadataUri = Uri.parse("content://${StickerContentProvider.AUTHORITY}/metadata")
            contentResolver.query(metadataUri, null, null, null, null)?.use { cursor ->
                "✅ StickerContentProvider operativo (${cursor.count} packs en catálogo, ${cursor.columnCount} columnas)"
            } ?: "⚠️ StickerContentProvider devolvió cursor nulo"
        } catch (e: Exception) {
            "⚠️ Error al consultar StickerContentProvider: ${e.message}"
        }

        val subtitle = TextView(this).apply {
            text = """
                Motor de Medios, Red 7TV, Persistencia & Integración WhatsApp listos.

                • Canvas: 512x512 px (16px margen)
                • Optimización estática: < 100 KB
                • WebP animado: < 500 KB (≤ 30 fps, ≤ 3s)
                • Cliente 7TV API v3: Configurado (Retrofit + OkHttp)
                • Descargador & Caché atómica: Listo (15 MB límite)
                • Puente de stickers (EmoteStickerBridge): Integrado
                • Persistencia Room: Configurada (Cascade ON DELETE)
                • Partición de packs (PackSplittingEngine): Lista (3..30)
                • Gestor de almacenamiento interno: Operativo

                Integración Nativa WhatsApp:
                • StickerContentProvider authority: ${StickerContentProvider.AUTHORITY}
                • IPC Cursors: 13 columnas de metadatos + cursor de stickers por pack
                • Streaming de archivos: AssetFileDescriptor (openAssetFile con validación path traversal)
                • Constructor de intents: ${WhatsAppConstants.INTENT_ACTION_ENABLE_STICKER_PACK}
                • Verificación de Whitelist: ${WhatsAppConstants.WHITELIST_AUTHORITY}

                Diagnóstico del Sistema:
                $nativeStatus
                $whatsAppStatus
                $providerCheckStatus
            """.trimIndent()
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, (14 * density).toInt(), 0, 0)
            gravity = Gravity.START
        }

        layout.addView(title)
        layout.addView(subtitle)
        scrollView.addView(layout)

        setContentView(scrollView)
    }
}
