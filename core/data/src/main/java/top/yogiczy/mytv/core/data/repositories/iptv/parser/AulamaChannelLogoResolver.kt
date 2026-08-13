package top.yogiczy.mytv.core.data.repositories.iptv.parser

/**
 * Keeps the official Aulama TV clients on the same curated channel artwork as the web app.
 *
 * The playlist remains the fallback source for every channel that is not explicitly curated.
 */
internal object AulamaChannelLogoResolver {
    private const val LOGO_BASE_URL = "https://aulama.org/iptv/channel-logos"

    fun resolve(
        tvgId: String?,
        name: String,
        epgName: String,
        fallback: String?,
    ): String? {
        val id = tvgId.orEmpty().trim()
        val identity = "$name $epgName $id"

        return when {
            id == "368361" || Regex("TVB\\s*Plus", RegexOption.IGNORE_CASE).containsMatchIn(identity) ->
                "$LOGO_BASE_URL/tvb-plus.png"

            id == "3493" || Regex("TVB\\s*(?:星河|Xing\\s*He)", RegexOption.IGNORE_CASE).containsMatchIn(identity) ->
                "$LOGO_BASE_URL/tvb-xinghe.png"

            id == "HongKongInternationalBusinessChannel.hk" ||
                Regex("HOY\\s*76", RegexOption.IGNORE_CASE).containsMatchIn(identity) ->
                "$LOGO_BASE_URL/hoy-76.png?v=20260802-transparent"

            id == "HOYTV.hk" || Regex("HOY\\s*77", RegexOption.IGNORE_CASE).containsMatchIn(identity) ->
                "$LOGO_BASE_URL/hoy-77.png?v=20260802-transparent"

            id == "HOYInfotainment.hk" || Regex("HOY\\s*78", RegexOption.IGNORE_CASE).containsMatchIn(identity) ->
                "$LOGO_BASE_URL/hoy-78.png?v=20260802-transparent"

            Regex("鳳凰衛視香港台|凤凰卫视香港台", RegexOption.IGNORE_CASE).containsMatchIn(identity) ->
                "$LOGO_BASE_URL/phoenix-hk.png"

            else -> fallback
        }
    }
}
