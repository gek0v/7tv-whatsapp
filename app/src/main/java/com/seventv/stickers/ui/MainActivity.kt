package com.seventv.stickers.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.seventv.stickers.core.media.animated.NativeWebPAnimEncoder

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
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

        val subtitle = TextView(this).apply {
            text = """
                Motor de procesamiento de stickers listo.
                
                • Canvas: 512x512 px (16px margen)
                • Optimización estática: < 100 KB
                • WebP animado: < 500 KB (≤ 30 fps, ≤ 3s)
                
                Estado del motor:
                $nativeStatus
            """.trimIndent()
            textSize = 15f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, (16 * density).toInt(), 0, 0)
            gravity = Gravity.CENTER
        }

        layout.addView(title)
        layout.addView(subtitle)

        setContentView(layout)
    }
}
