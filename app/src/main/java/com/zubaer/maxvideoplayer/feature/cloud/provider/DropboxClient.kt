package com.zubaer.maxvideoplayer.feature.cloud.provider

import android.net.Uri
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudEntry
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFileIdentity
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPage
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPlaybackResource
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DropboxClient(
    override val accountId: String,
    private val tokenProvider: CloudAccessTokenProvider,
    apiBase: String = PRODUCTION_API,
    contentBase: String = PRODUCTION_CONTENT,
) : CloudProviderClient {
    private val api = apiBase.trimEnd('/')
    private val content = contentBase.trimEnd('/')

    override suspend fun browse(parentId: String?, pageToken: String?, pageSize: Int): CloudPage {
        if (!pageToken.isNullOrBlank()) {
            return rpc("/2/files/list_folder/continue", JSONObject().put("cursor", pageToken)) { parseListFolder(it) }
        }
        val path = when {
            parentId.isNullOrBlank() || parentId == "root" -> ""
            parentId.startsWith("id:") -> parentId
            else -> parentId
        }
        val body = JSONObject()
            .put("path", path)
            .put("recursive", false)
            .put("include_deleted", false)
            .put("include_non_downloadable_files", true)
            .put("limit", pageSize.coerceIn(1, 2000))
        return rpc("/2/files/list_folder", body) { parseListFolder(it) }
    }

    override suspend fun search(query: String, pageToken: String?, pageSize: Int): CloudPage {
        if (!pageToken.isNullOrBlank()) {
            return rpc(
                "/2/files/search/continue_v2",
                JSONObject().put("cursor", pageToken),
            ) { parseSearch(it) }
        }
        val options = JSONObject()
            .put("path", "")
            .put("max_results", pageSize.coerceIn(1, 1000))
            .put("file_status", "active")
        return rpc(
            "/2/files/search_v2",
            JSONObject().put("query", query).put("options", options),
        ) { parseSearch(it) }
    }

    override suspend fun get(identity: CloudFileIdentity): CloudEntry {
        requireIdentity(identity)
        return rpc(
            "/2/files/get_metadata",
            JSONObject().put("path", identity.providerFileId).put("include_deleted", false),
        ) { parseEntry(it) }
    }

    override suspend fun resolvePlayback(identity: CloudFileIdentity, forceRefresh: Boolean): CloudPlaybackResource {
        requireIdentity(identity)
        val entry = get(identity)
        require(!entry.isFolder) { "Folders cannot be streamed." }
        val token = tokenProvider.accessToken(forceRefresh)
        return CloudPlaybackResource(
            identity = identity,
            uri = Uri.parse("$content/2/files/download"),
            mimeType = entry.mimeType,
            sizeBytes = entry.sizeBytes,
            revision = entry.revision,
            requestHeaders = mapOf(
                "Authorization" to "Bearer $token",
                "Dropbox-API-Arg" to JSONObject().put("path", identity.providerFileId).toString(),
            ),
            supportsRange = true,
        )
    }

    private suspend fun <T> rpc(path: String, body: JSONObject, parse: (JSONObject) -> T): T {
        suspend fun execute(token: String): okhttp3.Response {
            val request = Request.Builder()
                .url(api + path)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON))
                .build()
            return CloudHttp.execute(request)
        }
        var response = execute(tokenProvider.accessToken(false))
        if (response.code == 401) {
            response.close()
            response = execute(tokenProvider.accessToken(true))
        }
        response.use {
            CloudHttp.requireSuccess(it, "Dropbox")
            return parse(JSONObject(it.body.string()))
        }
    }

    private fun parseListFolder(json: JSONObject): CloudPage {
        val entriesJson = json.optJSONArray("entries")
        val entries = buildList {
            if (entriesJson != null) for (i in 0 until entriesJson.length()) add(parseEntry(entriesJson.getJSONObject(i)))
        }.sortedWith(compareByDescending<CloudEntry> { entry -> entry.isFolder }.thenBy { entry -> entry.name.lowercase() })
        return CloudPage(entries, if (json.optBoolean("has_more")) json.optString("cursor").takeIf(String::isNotBlank) else null)
    }

    private fun parseSearch(json: JSONObject): CloudPage {
        val matches = json.optJSONArray("matches")
        val entries = buildList {
            if (matches != null) for (i in 0 until matches.length()) {
                val metadata = matches.getJSONObject(i).optJSONObject("metadata")?.optJSONObject("metadata")
                    ?: matches.getJSONObject(i).optJSONObject("metadata")
                if (metadata != null) add(parseEntry(metadata))
            }
        }
        return CloudPage(entries, if (json.optBoolean("has_more")) json.optString("cursor").takeIf(String::isNotBlank) else null)
    }

    private fun parseEntry(json: JSONObject): CloudEntry {
        val tag = json.optString(".tag")
        val isFolder = tag == "folder"
        val id = json.optString("id").takeIf(String::isNotBlank)
            ?: json.optString("path_lower").takeIf(String::isNotBlank)
            ?: error("Dropbox entry is missing identity")
        return CloudEntry(
            identity = CloudFileIdentity(CloudProvider.DROPBOX, accountId, id),
            parentId = json.optString("parent_shared_folder_id").takeIf(String::isNotBlank),
            name = json.optString("name", id),
            isFolder = isFolder,
            sizeBytes = if (!isFolder && json.has("size")) json.optLong("size") else null,
            mimeType = null,
            modifiedAt = json.optString("server_modified").takeIf(String::isNotBlank),
            revision = json.optString("rev").takeIf(String::isNotBlank),
            canDownload = !isFolder,
            pathHint = json.optString("path_display").takeIf(String::isNotBlank),
        )
    }

    private fun requireIdentity(identity: CloudFileIdentity) {
        require(identity.provider == CloudProvider.DROPBOX && identity.accountId == accountId) { "Wrong cloud identity." }
    }

    private companion object {
        const val PRODUCTION_API = "https://api.dropboxapi.com"
        const val PRODUCTION_CONTENT = "https://content.dropboxapi.com"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
