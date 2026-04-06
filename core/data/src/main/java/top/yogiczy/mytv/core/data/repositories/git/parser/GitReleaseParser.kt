package top.yogiczy.mytv.core.data.repositories.git.parser

import top.yogiczy.mytv.core.data.entities.git.GitRelease

/**
 * git發行版解析接口
 */
interface GitReleaseParser {
    /**
     * 是否支持該格式
     */
    fun isSupport(url: String): Boolean

    /**
     * 解析數據
     */
    suspend fun parse(data: String): GitRelease

    companion object {
        val instances = listOf(
            GithubGitReleaseParser(),
            GiteeGitReleaseParser(),
            CustomGitReleaseParser(),
            DefaultGitReleaseParser(),
        )
    }
}