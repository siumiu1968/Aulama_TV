package top.yogiczy.mytv.core.data.repositories.git.parser

import top.yogiczy.mytv.core.data.entities.git.GitRelease

/**
 * 缺省發行版解析
 */
class DefaultGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return true
    }

    override suspend fun parse(data: String, includePrerelease: Boolean): GitRelease {
        return GitRelease(
            version = "0.0.0",
            downloadUrl = "",
            description = "不支持當前鏈接",
        )
    }
}
