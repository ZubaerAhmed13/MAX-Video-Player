package com.zubaer.maxvideoplayer.feature.cloud.provider

import android.net.Uri
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudEntry
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFailure
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFileIdentity
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPage
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPlaybackResource
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

class OneDriveClient(
    override val accountId: String,
    private val tokenProvider: CloudAccessTokenProvider,
    private val driveId: String? = null,
    graphBase: String = PRODUCTION_GRAPH,
) : CloudProviderClient {
    private val graph = graphBase.trimEnd('/')

    override suspend fun browse(parentId: String?, pageToken: String?, pageSize: Int): CloudPage {
        if (!pageToken.isNullOrBlank()) return fetchPage(pageToken)
        val base = if (driveId.isNullOrBlank()) "$graph/me/drive" else "$graph/drives/$driveId"
        val endpoint = if (parentId.isNullOrBlank() || parentId == "root") "$base/root/children" else "$base/items/$parentId/children"
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("\$select", SELECT)
            .addQueryParameter("\$top", pageSize.coerceIn(1, 200).toString())
            .addQueryParameter("\$orderby", "folder desc,name")
            .build()
        return fetchPage(url.toString())
    }

    override suspend fun search(query: String, pageToken: String?, pageSize: Int): CloudPage {
        if (!pageToken.isNullOrBlank()) return fetchPage(pageToken)
        val escaped = query.replace("'", "''")
        val base = if (driveId.isNullOrBlank()) "$graph/me/drive/root/search(q='$escaped')" else "$graph/drives/$driveId/root/search(q='$escaped')"
        val url = base.toHttpUrl().newBuilder()
            .addQueryParameter("\$select", SELECT)
            .addQueryParameter("\$top", pageSize.coerceIn(1, 200).toString())
            .build()
        return fetchPage(url.toString())
    }

    override suspend fun get(identity: CloudFileIdentity): CloudEntry {
        requireIdentity(identity)
        val resolvedDrive = identity.driveId ?: driveId
        val endpoint = if (resolvedDrive.isNullOrBlank()) "$graph/me/drive/items/${identity.providerFileId}" else "$graph/drives/$resolvedDrive/items/${identity.providerFileId}"
        val url = endpoint.toHttpUrl().newBuilder().addQueryParameter("\$select", SELECT).build()
        val response = authorizedRequest(url.toString())
        response.use {
            CloudHttp.requireSuccess(it, "OneDrive")
            return parseEntry(JSONObject(it.body.string()))
        }
    }

    override suspend fun resolvePlayback(identity: CloudFileIdentity, forceRefresh: Boolean): CloudPlaybackResource {
        requireIdentity(identity)
        tokenProvider.accessToken(forceRefresh)
        val entry = get(identity)
        if (entry.isFolder) throw CloudFailure.PermissionDenied("Folders cannot be streamed.")
        val resolvedDrive = identity.driveId ?: driveId
        val endpoint = if (resolvedDrive.isNullOrBlank()) "$graph/me/drive/items/${identity.providerFileId}" else "$graph/drives/$resolvedDrive/items/${identity.providerFileId}"
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("\$select", "id,size,file,eTag,cTag,@microsoft.graph.downloadUrl")
            .build()
        val response = authorizedRequest(url.toString())
        response.use {
            CloudHttp.requireSuccess(it, "OneDrive")
            val json = JSONObject(it.body.string())
            val downloadUrl = json.optString("@microsoft.graph.downloadUrl").takeIf(String::isNotBlank)
                ?: throw CloudFailure.Unavailable("OneDrive did not provide a temporary streaming URL.")
            return CloudPlaybackResource(
                identity = identity,
                uri = Uri.parse(downloadUrl),
                mimeType = entry.mimeType,
                sizeBytes = entry.sizeBytes,
                revision = entry.revision,
                requestHeaders = emptyMap(),
                expiresAtMs = System.currentTimeMillis() + 45L * 60L * 1000L,
                supportsRange = true,
            )
        }
    }

    private suspend fun fetchPage(url: String): CloudPage {
        val response = authorizedRequest(url)
        response.use {
            CloudHttp.requireSuccess(it, "OneDrive")
            val json = JSONObject(it.body.string())
            val array = json.optJSONArray("value")
            val entries = buildList {
                if (array != null) for (i in 0 until array.length()) add(parseEntry(array.getJSONObject(i)))
            }.sortedWith(compareByDescending<CloudEntry> { entry -> entry.isFolder }.thenBy { entry -> entry.name.lowercase() })
            return CloudPage(entries, json.optString("@odata.nextLink").takeIf(String::isNotBlank))
        }
    }

    private suspend fun authorizedRequest(url: String): okhttp3.Response {
        fun request(token: String) = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get().build()
        var response = CloudHttp.execute(request(tokenProvider.accessToken(false)))
        if (response.code == 401) {
            response.close()
            response = CloudHttp.execute(request(tokenProvider.accessToken(true)))
        }
        return response
    }

    private fun parseEntry(json: JSONObject): CloudEntry {
        val id = json.getString("id")
        val file = json.optJSONObject("file")
        val folder = json.optJSONObject("folder")
        val parent = json.optJSONObject("parentReference")
        val resolvedDrive = parent?.optString("driveId")?.takeIf(String::isNotBlank) ?: driveId
        return CloudEntry(
            identity = CloudFileIdentity(CloudProvider.ONEDRIVE, accountId, id, resolvedDrive),
            parentId = parent?.optString("id")?.takeIf(String::isNotBlank),
            name = json.optString("name", id),
            isFolder = folder != null,
            sizeBytes = if (json.has("size")) json.optLong("size") else null,
            mimeType = file?.optString("mimeType")?.takeIf(String::isNotBlank),
            modifiedAt = json.optString("lastModifiedDateTime").takeIf(String::isNotBlank),
            revision = json.optString("eTag").takeIf(String::isNotBlank)
                ?: json.optString("cTag").takeIf(String::isNotBlank),
            canDownload = file != null,
        )
    }

    private fun requireIdentity(identity: CloudFileIdentity) {
        require(identity.provider == CloudProvider.ONEDRIVE && identity.accountId == accountId) { "Wrong cloud identity." }
    }

    private companion object {
        const val PRODUCTION_GRAPH = "https://graph.microsoft.com/v1.0"
        const val SELECT = "id,name,size,file,folder,parentReference,lastModifiedDateTime,eTag,cTag"
    }
}
