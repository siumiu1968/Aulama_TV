package top.yogiczy.mytv.core.data.repositories.iptv.parser

/**
 * Keeps the official Aulama TV clients on the same curated channel artwork as the web app.
 *
 * The playlist remains the fallback source for every channel that is not explicitly curated.
 */
internal object AulamaChannelLogoResolver {
    private const val LOGO_BASE_URL = "https://aulama.org/iptv/channel-logos"
    private const val CATALOG_VERSION = "20260814"

    /**
     * These channels either have no artwork in the source playlist, rely on rate-limited image
     * hosts, or use remote SVG files that are unreliable on older Android TV devices.
     *
     * Keeping the mapping by stable tvg-id means channel names and route labels can change without
     * breaking the artwork. The files are mirrored by Aulama so the TV and web clients use the same
     * HTTPS origin instead of dozens of third-party image hosts.
     */
    private val curatedLogoByTvgId = mapOf(
        // China
        "CCTV13.cn@HD" to "cn-cctv-13.png",
        "CCTV4Asia.cn@SD" to "cn-cctv-4-asia.png",
        "CGTN.cn@SD" to "cn-cgtn.png",
        "CGTNDocumentary.cn@SD" to "cn-cgtn-documentary.png",
        "CGTNFrench.cn@SD" to "cn-cgtn-french.png",
        "CGTNRussian.cn@SD" to "cn-cgtn-russian.png",
        "CGTNArabic.cn@SD" to "cn-cgtn-arabic.png",
        "CGTNSpanish.cn@SD" to "cn-cgtn-spanish.png",
        "ZhejiangSatelliteTV.cn@HD" to "cn-zhejiang-satellite.png",
        "ZhejiangQianjiangCity.cn@HD" to "cn-zhejiang-qianjiang-city.png",
        "ZhejiangEconomicLife.cn@HD" to "cn-zhejiang-economic-life.png",
        "ZhejiangEducationFilm.cn@HD" to "cn-zhejiang-education-film.png",
        "ZhejiangPeopleLeisure.cn@HD" to "cn-zhejiang-people-leisure.png",
        "ZhejiangNews.cn@HD" to "cn-zhejiang-news.png",
        "ZhejiangChildren.cn@HD" to "cn-zhejiang-children.png",
        "ZhejiangInternational.cn@HD" to "cn-zhejiang-international.png",
        "Suzhou4K.cn@HD" to "cn-suzhou-4k.png",
        "DragonTV.cn@HD" to "cn-dragon-tv.png",

        // International
        "BloombergTV.us@Plus" to "intl-bloomberg.png",
        "BloombergTV.us@US" to "intl-bloomberg.png",
        "BloombergOriginals.us@US" to "intl-bloomberg.png",
        "YahooFinance.us@SD" to "intl-yahoo-finance.png",
        "NBCNewsNOW.us@SD" to "intl-nbc-news-now.png",
        "CBSNews247.us" to "intl-cbs-news.png",
        "ABCNewsLive.us@SD" to "intl-abc-news-live.png",
        "CBCNewsNetwork.ca@SD" to "intl-cbc-news.png",
        "ReutersTV.us@SD" to "intl-reuters.png",
        "CNNInternational.us" to "intl-cnn.png",
        "France24.fr@English" to "intl-france24.png",
        "France24.fr@French" to "intl-france24.png",
        "France24.fr@Spanish" to "intl-france24.png",
        "France24.fr@Arabic" to "intl-france24.png",
        "DW.de@English" to "intl-dw.png",
        "BBCNews.uk" to "intl-bbc-news.png",
        "AlJazeera.qa@English" to "intl-al-jazeera.png",
        "AlJazeera.qa@Arabic" to "intl-al-jazeera.png",
        "AlArabiyaBusiness.ae@SD" to "intl-al-arabiya.png",
        "TRTWorld.tr@SD" to "intl-trt-world.png",
        "NHKWorldJapan.jp@SD" to "intl-nhk-world.png",
        "Weathernews.jp" to "intl-weathernews.png",
        "QVC.jp@SD" to "intl-qvc.png",
        "CGNTVJapan.jp@SD" to "intl-cgntv-japan.png",
        "GSTV.jp@SD" to "intl-gstv.png",
        "ArirangTV.kr@SD" to "intl-arirang.png",
        "OUN.kr@SD" to "intl-oun.png",
        "CNA.sg@SD" to "intl-cna.png",
        "CNAOriginals.sg@SD" to "intl-cna.png",
        "TVBSAsia.tw@SD" to "intl-tvbs-asia.png",
        "TaiwanPlusTV.tw@SD" to "intl-taiwanplus.png",
        "DaliTV.tw" to "intl-dali-tv.png",
        "TaiwanIndigenousTV.tw" to "intl-titv.png",
        "CNBCTV18.in@SD" to "intl-cnbc-tv18.png",
        "WION.in" to "intl-wion.png",
    )

    fun resolve(
        tvgId: String?,
        name: String,
        epgName: String,
        fallback: String?,
    ): String? {
        val id = tvgId.orEmpty().trim()
        val identity = "$name $epgName $id"
        val curatedLogo = curatedLogoByTvgId[id]

        if (curatedLogo != null) {
            return "$LOGO_BASE_URL/$curatedLogo?v=$CATALOG_VERSION"
        }

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
