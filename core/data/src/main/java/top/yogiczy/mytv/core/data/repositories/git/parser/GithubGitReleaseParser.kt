package top.yogiczy.mytv.core.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yogiczy.mytv.core.data.entities.git.GitRelease

/**
 * github發行版解析
 */
class GithubGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("github.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val root = Json.parseToJsonElement(data)
        val release = when (root) {
            is JsonObject -> root
            is JsonArray -> root
                .map { it.jsonObject }
                .firstOrNull {
                    it["draft"]?.jsonPrimitive?.booleanOrNull != true &&
                        findTvApk(it) != null
                }
                ?: error("找不到可用的 Android TV 發行版")

            else -> error("GitHub 發行版格式不正確")
        }

        val asset = findTvApk(release) ?: error("發行版未附帶 Android TV APK")
        val tagName = release.getValue("tag_name").jsonPrimitive.content

        return GitRelease(
            version = tagName.removePrefix("v"),
            downloadUrl = asset.getValue("browser_download_url").jsonPrimitive.content,
            description = release["body"]?.jsonPrimitive?.content.orEmpty()
                .ifBlank { "此版本包含穩定性與介面改善。" },
            sha256 = asset["digest"]?.jsonPrimitive?.content
                ?.removePrefix("sha256:")
                .orEmpty(),
        )
    }

    private fun findTvApk(release: JsonObject): JsonObject? {
        val assets = release["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
        return assets.firstOrNull {
            val name = it["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
            name.endsWith(".apk") && ("tv" in name || "all-sdk" in name)
        } ?: assets.firstOrNull {
            it["name"]?.jsonPrimitive?.content.orEmpty().endsWith(".apk", ignoreCase = true)
        }
    }
}
