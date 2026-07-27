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
    private val tvVersionPattern = Regex(
        pattern = "^v?(\\d+(?:\\.\\d+){1,3}(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?)$",
    )

    override fun isSupport(url: String): Boolean {
        return url.contains("github.com")
    }

    override suspend fun parse(data: String, includePrerelease: Boolean): GitRelease {
        val root = Json.parseToJsonElement(data)
        val release = when (root) {
            is JsonObject -> root.takeIf {
                isEligibleRelease(it, includePrerelease)
            }
                ?: error("找不到可用的 Android TV 發行版")
            is JsonArray -> root
                .map { it.jsonObject }
                .firstOrNull {
                    isEligibleRelease(it, includePrerelease)
                }
                ?: error("找不到可用的 Android TV 發行版")

            else -> error("GitHub 發行版格式不正確")
        }

        val asset = findTvApk(release) ?: error("發行版未附帶 Android TV APK")
        val tagName = release.getValue("tag_name").jsonPrimitive.content
        val version = tvVersionPattern.matchEntire(tagName)?.groupValues?.get(1)
            ?: error("Android TV 發行版版本格式不正確")

        return GitRelease(
            version = version,
            downloadUrl = asset.getValue("browser_download_url").jsonPrimitive.content,
            description = release["body"]?.jsonPrimitive?.content.orEmpty()
                .ifBlank { "此版本包含穩定性與介面改善。" },
            sha256 = asset["digest"]?.jsonPrimitive?.content
                ?.removePrefix("sha256:")
                .orEmpty(),
        )
    }

    private fun isEligibleRelease(
        release: JsonObject,
        includePrerelease: Boolean,
    ): Boolean {
        val tagName = release["tag_name"]?.jsonPrimitive?.content.orEmpty()
        return release["draft"]?.jsonPrimitive?.booleanOrNull != true &&
            (includePrerelease || release["prerelease"]?.jsonPrimitive?.booleanOrNull != true) &&
            tvVersionPattern.matches(tagName) &&
            findTvApk(release) != null
    }

    private fun findTvApk(release: JsonObject): JsonObject? {
        val assets = release["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
        return assets.firstOrNull {
            val name = it["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
            name.endsWith(".apk") &&
                ("android-tv" in name || "mytv-tv" in name)
        } ?: assets.firstOrNull {
            val name = it["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
            name.endsWith(".apk") && "mobile" !in name
        }
    }
}
