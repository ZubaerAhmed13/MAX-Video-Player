package com.zubaer.maxvideoplayer.feature.cloud.provider

import android.net.Uri
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudEntry
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFailure
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFileIdentity
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudMediaPolicy
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPage
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPlaybackResource
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

class GoogleDriveClient(
    override val accountId: String,
    private val tokenProvider: CloudAccessTokenProvider,
    apiBase: String = PRODUCTION_BASE,
) : CloudProviderClient {
    private val base = apiBase.trimEnd('/')

    override suspend fun browse(parentId: String?, pageToken: String?, pageSize: Int): CloudPage {
        val parent = parentId ?: "root"
        val query = "'$parent' in parents and trashed = false"
        return list(query, pageToken, pageSize)
    }

    override suspend fun search(query: String, pageToken: String?, pageSize: Int): CloudPage {
        val escaped = query.replace("\\", "\\\\").replace("'", "\\'")
        return list("name contains '$escaped' and trashed = false", pageToken, pageSize)
    }

    override suspend fun get(identity: CloudFileIdentity): CloudEntry {
        requireIdentity(identity)
        val url = "$base/files/${identity.providerFileId}".toHttpUrl().newBuilder()
            .addQueryParameter("fields", FILE_FIELDS)
            .addQueryParameter("supportsAllDrives", "true")
            .build()
        val response = authorizedRequest(url.toString())
        response.use {
            CloudHttp.requireSuccess(it, "Google Drive")
            return parseEntry(JSONObject(it.body.string()), parentId = null)
        }
    }

    override suspend fun resolvePlayback(identity: CloudFileIdentity, forceRefresh: Boolean): CloudPlaybackResource {
        requireIdentity(identity)
        val metadata = get(identity)
        if (metadata.isFolder || CloudMediaPolicy.isGoogleWorkspaceDocument(metadata.mimeType)) {
            throw CloudFailure.PermissionDenied("This Google Drive item is not a streamable blob media file.")
        }
        if (!metadata.canDownload) {
            throw CloudFailure.DownloadDisabled("This file cannot be streamed because its owner has disabled downloading.")
        }
        val token = tokenProvider.accessToken(forceRefresh)
        val uri = Uri.parse("$base/files/${identity.providerFileId}?alt=media&supportsAllDrives=true")
        return CloudPlaybackResource(
            identity = identity,
            uri = uri,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            revision = metadata.revision,
            requestHeaders = mapOf("Authorization" to "Bearer $token"),
            supportsRange = true,
        )
    }

    private suspend fun list(q: String, pageToken: String?, pageSize: Int): CloudPage {
        val url = "$base/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("pageSize", pageSize.coerceIn(1, 1000).toString())
            .addQueryParameter("fields", "nextPageToken,files($FILE_FIELDS)")
            .addQueryParameter("orderBy", "folder,name_natural")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("includeItemsFromAllDrives", "true")
            .apply { if (!pageToken.isNullOrBlank()) addQueryParameter("pageToken", pageToken) }
            .build()
        val response = authorizedRequest(url.toString())
        response.use {
            CloudHttp.requireSuccess(it, "Google Drive")
            val json = JSONObject(it.body.string())
            val files = json.optJSONArray("files")
            val entries = buildList {
                if (files != null) for (i in 0 until files.length()) add(parseEntry(files.getJSONObject(i), null))
            }.sortedWith(compareByDescending<CloudEntry> { entry -> entry.isFolder }.thenBy { entry -> entry.name.lowercase() })
            return CloudPage(entries, json.optString("nextPageToken").takeIf(String::isNotBlank))
        }
    }

    private suspend fun authorizedRequest(url: String): okhttp3.Response {
        fun request(token: String) = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()

        var response = CloudHttp.execute(request(tokenProvider.accessToken(false)))
        if (response.code == 401) {
            response.close()
            response = CloudHttp.execute(request(tokenProvider.accessToken(true)))
        }
        return response
    }

    private fun parseEntry(json: JSONObject, parentId: String?): CloudEntry {
        val mime = json.optString("mimeType").takeIf(String::isNotBlank)
        val id = json.getString("id")
        val caps = json.optJSONObject("capabilities")
        val thumbnail = json.optString("thumbnailLink").takeIf(String::isNotBlank)
        return CloudEntry(
            identity = CloudFileIdentity(CloudProvider.GOOGLE_DRIVE, accountId, id),
            parentId = parentId,
            name = json.optString("name", id),
            isFolder = mime == CloudMediaPolicy.GOOGLE_FOLDER_MIME,
            sizeBytes = json.optString("size").toLongOrNull(),
            mimeType = mime,
            modifiedAt = json.optString("modifiedTime").takeIf(String::isNotBlank),
            revision = json.optString("version").takeIf(String::isNotBlank)
                ?: json.optString("md5Checksum").takeIf(String::isNotBlank),
            canDownload = caps?.optBoolean("canDownload", true) ?: true,
            thumbnailUrl = thumbnail,
        )
    }

    private fun requireIdentity(identity: CloudFileIdentity) {
        require(identity.provider == CloudProvider.GOOGLE_DRIVE && identity.accountId == accountId) { "Wrong cloud identity." }
    }

    private companion object {
        const val PRODUCTION_BASE = "https://www.googleapis.com/drive/v3"
        const val FILE_FIELDS = "id,name,mimeType,size,modifiedTime,version,md5Checksum,capabilities(canDownload),thumbnailLink,parents"
    }
}
