package com.seventv.stickers.core.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubAssetDto(
    val id: Long = 0L,
    val name: String,
    @SerialName("content_type") val contentType: String? = null,
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val digest: String? = null
)