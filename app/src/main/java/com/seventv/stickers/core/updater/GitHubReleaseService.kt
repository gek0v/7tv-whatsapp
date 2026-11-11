package com.seventv.stickers.core.updater

import com.seventv.stickers.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface GitHubReleaseService {

    @Headers("Accept: application/vnd.github+json")
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String = DEFAULT_OWNER,
        @Path("repo") repo: String = DEFAULT_REPO,
        @Header("If-None-Match") ifNoneMatch: String? = null
    ): Response<GitHubReleaseDto>

    companion object {
        const val DEFAULT_BASE_URL = "https://api.github.com/"
        const val DEFAULT_OWNER = "gek0v"
        const val DEFAULT_REPO = "7tv-whatsapp"
        private const val TIMEOUT_SECONDS = 15L

        val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        fun createOkHttpClient(): OkHttpClient {
            val userAgent = "7TV-Stickers-Android/${BuildConfig.VERSION_NAME}"
            return OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                    chain.proceed(request)
                }
                .build()
        }

        fun create(
            baseUrl: String = DEFAULT_BASE_URL,
            okHttpClient: OkHttpClient = createOkHttpClient()
        ): GitHubReleaseService {
            val contentType = "application/json".toMediaType()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(GitHubReleaseService::class.java)
        }
    }
}

fun interface ChecksumAssetFetcher {
    suspend fun fetchText(url: String): String?
}

class OkHttpChecksumAssetFetcher(
    private val okHttpClient: OkHttpClient
) : ChecksumAssetFetcher {
    override suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}