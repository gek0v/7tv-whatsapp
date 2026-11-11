package com.seventv.stickers.core.network.api

import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object SevenTvNetworkFactory {
    private const val DEFAULT_BASE_URL = "https://7tv.io/"
    const val BTTV_BASE_URL = "https://api.betterttv.net/"
    const val FFZ_BASE_URL = "https://api.frankerfacez.com/"
    const val TWITCH_IVR_BASE_URL = "https://api.ivr.fi/"

    private const val USER_AGENT = "7TV-WhatsApp-Android/1.0"
    private const val TIMEOUT_SECONDS = 15L
    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024 // 10 MB

    fun createOkHttpClient(cacheDir: File? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }

        cacheDir?.let { dir ->
            builder.cache(Cache(dir, CACHE_SIZE_BYTES))
        }

        return builder.build()
    }

    private val defaultJson: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun createApi(
        baseUrl: String = DEFAULT_BASE_URL,
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): SevenTvApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(defaultJson.asConverterFactory(contentType))
            .build()
            .create(SevenTvApi::class.java)
    }

    fun createSevenTvApi(): SevenTvApi = createApi()

    fun createBttvApi(
        baseUrl: String = BTTV_BASE_URL,
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): BttvApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(defaultJson.asConverterFactory(contentType))
            .build()
            .create(BttvApi::class.java)
    }

    fun createFfzApi(
        baseUrl: String = FFZ_BASE_URL,
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): FfzApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(defaultJson.asConverterFactory(contentType))
            .build()
            .create(FfzApi::class.java)
    }

    fun createTwitchEmotesApi(
        baseUrl: String = TWITCH_IVR_BASE_URL,
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): TwitchEmotesApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(defaultJson.asConverterFactory(contentType))
            .build()
            .create(TwitchEmotesApi::class.java)
    }
}
