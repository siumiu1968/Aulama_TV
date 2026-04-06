package com.lizongying.mytv

import com.lizongying.mytv.models.ProgramType

object TVList {

    private const val GROUP_MAIN = "香港常用"
    private const val GROUP_NEWS = "新聞財經"
    private const val GROUP_MORE = "更多頻道"

    fun current(): Map<String, List<TV>> = normalize(fullRows())

    private fun normalize(rows: LinkedHashMap<String, List<TV>>): Map<String, List<TV>> {
        val normalized = linkedMapOf<String, List<TV>>()
        var id = 0

        rows.forEach { (groupName, groupChannels) ->
            val cleaned = groupChannels.mapNotNull { tv ->
                val urls = tv.videoUrl
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                if (urls.isEmpty()) {
                    null
                } else {
                    tv.copy(id = id++, videoUrl = urls)
                }
            }

            if (cleaned.isNotEmpty()) {
                normalized[groupName] = cleaned
            }
        }

        return normalized
    }

    private fun parentRows(): LinkedHashMap<String, List<TV>> {
        return linkedMapOf(
            GROUP_MAIN to listOf(
                jade(),
                wirelessNews(),
                tvbPlus(),
                nowNews(),
                nowFinance(),
                viuTv(),
                pearl(),
                rthk31(),
            )
        )
    }

    private fun fullRows(): LinkedHashMap<String, List<TV>> {
        return linkedMapOf(
            GROUP_MAIN to listOf(
                jade(),
                wirelessNews(),
                tvbPlus(),
                nowNews(),
                nowFinance(),
                viuTv(),
                pearl(),
                rthk31(),
                hoyTv(),
            ),
            GROUP_NEWS to listOf(
                nowSports(),
                nowMovies(),
                nowStarMovies(),
                phoenixInfo(),
                phoenixChinese(),
                phoenixHongKong(),
                viuTvSix(),
                rthk32(),
            ),
            GROUP_MORE to listOf(
                tvbXingHe(),
                millenniumClassic(),
                rewindClassic(),
                meiYaMovie(),
                macau(),
                macauSports(),
                macauVariety(),
            )
        )
    }

    private fun directChannel(
        title: String,
        alias: String,
        row: String,
        logo: String,
        vararg urls: String
    ): TV {
        return TV(
            id = 0,
            title = title,
            alias = alias,
            videoUrl = urls.toList(),
            channel = row,
            logo = logo,
            pid = "",
            sid = "",
            programType = ProgramType.DIRECT,
            needToken = false,
            mustToken = false
        )
    }

    private fun jade(): TV = directChannel(
        title = "翡翠台",
        alias = "無綫翡翠",
        row = GROUP_MAIN,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/翡翠.png",
        "https://stream1.freetv.fun/8a31735d012894cb8c275ebf1a898a3bb42f7201dd54d6d1201d724877fd9c19.m3u8",
        "https://cloudfront41.lexanetwork.com:1344/relay01/livestream002.sdp/playlist.m3u8",
        "https://stream1.freetv.fun/96be57df137f3e952c37d15c81c54511223f65ab2929549598ef3c601f7f0add.ctv",
        "https://stream1.freetv.fun/169afe453d043b4888d3e0684163a3c8e7c13738df07bcfa187dcd05223e3891.ctv",
        "http://php.jdshipin.com/TVOD/iptv.php?id=fct",
        "https://stream1.freetv.fun/ec738c8f8b5d5706670eb42e4fe79edc5d6f471ba93d0eb20dff4c372efef4db.ctv",
        "https://mytv.cdn.loc.cc/o12.php?id=fct",
        "https://stream1.freetv.fun/363a02513a5dbd6658d52b26c733386779319595957dce38c539ddb689d7b401.ctv",
        "https://stream1.freetv.fun/7626cd1e0830deb6cc37d51b751648e27e25c8136a22f61f6bb6ce5a85dcb0c4.ctv",
        "http://php.jdshipin.com/PLTV/iptv.php?id=fct",
        "https://stream1.freetv.fun/07b8f89d6c2bf405852bd8eb695e2389afdb45dfaf30d5273f8d2b819720681f.ctv",
        "http://php.jdshipin.com/TVOD/iptv.php?id=fct3",
        "https://cdn6.101.qzz.io/163189/fct",
        "http://php.jdshipin.com:8880/TVOD/iptv.php?id=fct2",
        "http://php.jdshipin.com/TVOD/iptv.php?id=fct2",
        "http://php.jdshipin.com/PLTV/iptv.php?id=fct2",
        "https://stream1.freetv.fun/b1128a129ebb9fd5ddde711ab565c82831400d80e5aa6aa9822e6c1b33ed95c5.ctv",
        "https://stream1.freetv.fun/99bb646a71d6fb69f3287af93bc08e97e334d626c7889509579bf47e76b641f8.ctv",
        "https://stream1.freetv.fun/ad698c68f995a760534e836438ea8e8bc65abf16cd0ed1d6b8e005cd9b90913c.ctv",
    )

    private fun wirelessNews(): TV = directChannel(
        title = "無綫新聞台",
        alias = "無綫新聞",
        row = GROUP_MAIN,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/无线新闻.png",
        "https://stream1.freetv.fun/b9fc79702a33d75d0fa4c22a19341be1a7120d111c47d9e109566b9d42020383.ctv",
        "https://cdn6.101.qzz.io/163189/wxxw",
        "http://php.jdshipin.com/TVOD/iptv.php?id=wxxwt",
        "https://stream1.freetv.fun/9226b0d3b99778a509e911ce89c6a95ab011763c6cea4b2f682e76b6659a52d7.ctv",
        "https://stream1.freetv.fun/ccba0351b8318bedd9168ef7a76e3b2e9a0bee295e05694b0feb45aa0916b97d.ctv",
        "http://php.jdshipin.com:8880/smt.php?id=inews_twn",
        "http://php.jdshipin.com/PLTV/iptv.php?id=wxxwt",
    )

    private fun tvbPlus(): TV = directChannel(
        title = "TVB Plus",
        alias = "J2",
        row = GROUP_MAIN,
        logo = "https://epg.pw/media/images/channel/2023/07/22/large/20230722193451659796_71.png",
        "https://stream1.freetv.fun/ca3ef0d86499c5532593cd3f5f8e1e49b5cb6e458df18d5e4819e830955af5da.m3u8",
        "https://stream1.freetv.fun/da034e93c10ecf7d190dc26e54b87fb80766685de00d86afdf46a833c512cb27.ctv",
        "https://stream1.freetv.fun/e175c99ac93e5584a2134fc6349ebe81808147fb1b7b44d76c7d16fa98d82b74.m3u8",
        "https://stream1.freetv.fun/5c71ddc1ee039d8057898c807e9cda2dfc61c7534d017fbf7c0de04c5c58c890.m3u8",
        "https://stream1.freetv.fun/6ade45822dc9265d4aba6c1854bfd6a2635b13d96624929150024f662eca25d7.ctv",
        "https://stream1.freetv.fun/dec5e1d162ddc4e733fb714c2c8795323ccf94261596cffe7ee65f0034986c70.ctv",
        "https://stream1.freetv.fun/efea08e40d2af040689b157636dfc39a60cdd414ca5fa586e091a12876bb4bbf.ctv",
        "https://stream1.freetv.fun/391d21caea3269c8477f1c3fc0f24bbfded5d4f4fcf1d63d96b5cd8574792a34.ctv",
        "http://php.jdshipin.com/TVOD/iptv.php?id=j2",
        "https://stream1.freetv.fun/8be534f8e9fd65158d8caee0104146164383687d3a6aa3b0721edd031ddb6279.ctv",
    )

    private fun nowNews(): TV = directChannel(
        title = "Now 新聞台",
        alias = "Now News",
        row = GROUP_MAIN,
        logo = "https://epg.pw/media/images/channel/2024/04/17/large/20240417171909234814_40.png",
        "https://stream1.freetv.fun/60ac4015a508533f429e33b5d560c3f5b07a3f99a362214308f832a4f5bc138b.m3u8",
    )

    private fun nowFinance(): TV = directChannel(
        title = "Now 財經台",
        alias = "Now Finance",
        row = GROUP_MAIN,
        logo = "https://epg.pw/media/images/channel/2024/04/17/large/20240417171848551996_11.png",
        "https://stream1.freetv.fun/6f9c76c500afbfd33350f7e4ab4b7051a577ab6e5f1a9317b35b8c7f1f838512.m3u8",
    )

    private fun viuTv(): TV = directChannel(
        title = "ViuTV",
        alias = "ViuTV",
        row = GROUP_MAIN,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/VIUTV.png",
        "https://stream1.freetv.fun/ad77293b3968d9314a0a612d10a82fcd2617e3370c750355a5ff31cced30ca13.m3u8",
        "https://stream1.freetv.fun/f9680349a9abb624d2210ac1d8bcec0da482ef32c261d90845474ce89fa5f1ea.ctv",
        "https://stream1.freetv.fun/5fc2ba313b387d2b2538a2e46aee6266cc10890fc8b8ccf478498b82af6f7c3b.ctv",
        "http://php.jdshipin.com/PLTV/iptv.php?id=viutv2",
        "https://stream1.freetv.fun/acf28668e5790e2d2ac642051e52d00b0ea2372b79f0526617aa30a9af34b16b.ctv",
        "https://stream1.freetv.fun/566447a554a11bb540e5e37ea32162caa2959fefd3707625f70de6ac96255364.ctv",
        "http://php.jdshipin.com/TVOD/iptv.php?id=viutv",
        "https://stream1.freetv.fun/ab16a30d3816f20a7eabe52f579ceac858c17659b1a9c1915392d63552712622.ctv",
        "https://stream1.freetv.fun/dcb93bc96d44fc8ee102cde52bcb32f067ef1a2a23e785fa4809cf3f054b0b4d.ctv",
        "https://stream1.freetv.fun/855fcf2cb6242c3bbf91d073a966eefcecb239f58f03460b4947d32ee7785d2c.m3u8",
    )

    private fun pearl(): TV = directChannel(
        title = "明珠台",
        alias = "無綫明珠",
        row = GROUP_MAIN,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/明珠.png",
        "https://stream1.freetv.fun/69f1ead0e893a2afc15dd39f8bb06bb84a91875cf4fb29e1c7e3a4730273b641.m3u8",
        "https://stream1.freetv.fun/4db377997e788111dc3a7a47fec67132e36bd8221c27f01f0d14ae82d86bc4ff.ctv",
        "https://mytv.cdn.loc.cc/o12.php?id=mzt",
        "https://stream1.freetv.fun/9355776363cced50b636fb90bd1d63ba128a62b87206583d553b215847a74d2d.ctv",
        "http://php.jdshipin.com/TVOD/iptv.php?id=mzt2",
        "https://stream1.freetv.fun/b49f11a7e68a76202daba76fb5c90eb8ef1c34d977c5e9eac0825112f74f87d3.ctv",
        "http://php.jdshipin.com:8880/TVOD/iptv.php?id=mzt",
        "http://php.jdshipin.com/TVOD/iptv.php?id=mzt2",
        "http://php.jdshipin.com/PLTV/iptv.php?id=mzt2",
        "https://stream1.freetv.fun/18147fe9227d749afdbf2b79ad6a6182ecd2276f2338a696d0c23d998e2763b2.ctv",
        "https://stream1.freetv.fun/fc542dddf6d96335d494c255d5df19fc0f2c61c46779dd9fe85e54b1559c3460.ctv",
        "https://stream1.freetv.fun/e56abfec74a8d135ddfc2c074a81751b6fc41d0272d5f5519c218b2e56d3265f.ctv",
        "http://php.jdshipin.com/PLTV/iptv.php?id=mzt",
        "https://stream1.freetv.fun/a4ef3a05fe986f663938806c799210b5063fd8a4be0058da04fd45fe000a7e24.ctv",
        "https://stream1.freetv.fun/02a84e717bde4c74530fa751aff6d340c4a1757f966a59bdc9f37751a121ce37.ctv",
    )

    private fun rthk31(): TV = directChannel(
        title = "RTHK 31",
        alias = "港台電視31",
        row = GROUP_MAIN,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/RTHK31.png",
        "https://stream1.freetv.fun/d7d6ff5be0279cf81d1f76acf708cc8c4f234aa7fc67009fff694fcff34f2601.ctv",
        "http://php.jdshipin.com/PLTV/iptv.php?id=rthk31",
    )

    private fun hoyTv(): TV = directChannel(
        title = "HOY TV",
        alias = "HOY TV",
        row = GROUP_MAIN,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/HOYTV.png",
        "http://php.jdshipin.com/TVOD/iptv.php?id=hoytv",
        "http://php.jdshipin.com/PLTV/iptv.php?id=hoytv",
        "http://php.jdshipin.com/PLTV/iptv.php?id=hoytv2",
        "https://stream1.freetv.fun/46e229877cf43dbe3bb518a02d3f4dbd2b2288cf9b54b191145786eebf1a2648.ctv",
    )

    private fun nowSports(): TV = directChannel(
        title = "Now Sports",
        alias = "Now Sports",
        row = GROUP_NEWS,
        logo = "https://epg.pw/media/images/channel/2023/07/22/large/20230722205957773276_84.png",
        "https://stream1.freetv.fun/532b7aadf6620fa7495d3e9d13dd327378e73a41d774b8e0af98450c24fa8a51.m3u8",
    )

    private fun nowMovies(): TV = directChannel(
        title = "Now 爆谷台",
        alias = "Now 爆谷台",
        row = GROUP_NEWS,
        logo = "https://epg.pw/media/images/channel/2023/11/02/large/20231102234546918555_91.png",
        "https://stream1.freetv.fun/580dccfbcacd4d95bb3c11f675d4973ee2664654fbf8ab7a8d29e951bb98a983.ctv",
    )

    private fun nowStarMovies(): TV = directChannel(
        title = "Now 星影台",
        alias = "Now 星影台",
        row = GROUP_NEWS,
        logo = "https://epg.pw/media/images/channel/2024/04/17/large/20240417171857306359_61.png",
        "https://stream1.freetv.fun/ba5e059f42aec923498956a5b23f36ab9a232dbd87c10e3f4482dce7fe7536ae.ctv",
    )

    private fun phoenixInfo(): TV = directChannel(
        title = "鳳凰資訊台",
        alias = "鳳凰資訊",
        row = GROUP_NEWS,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/凤凰资讯.png",
        "https://stream1.freetv.fun/320c213f4bb29b607f6c659b2a94918fc7f922c996ede3738fdff2ab9bd90217.m3u8",
        "https://stream1.freetv.fun/6cb73e3cad873d275ae7399762214060046e7b08b051b249f8bdb95ec3fea2fc.m3u8",
        "https://stream1.freetv.fun/775da45dedb5d0e2186c592c1a582aabe6893f527089d022ba7104e2471c96cf.ctv",
        "http://php.jdshipin.com/TVOD/iptv.php?id=fhzx",
        "http://php.jdshipin.com/PLTV/iptv.php?id=fhzx",
        "https://cdn6.101.qzz.io/163189/fhzx",
        "http://php.jdshipin.com/TVOD/iptv.php?id=fhzx2",
    )

    private fun phoenixChinese(): TV = directChannel(
        title = "鳳凰中文台",
        alias = "鳳凰中文",
        row = GROUP_NEWS,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/凤凰中文.png",
        "https://stream1.freetv.fun/abf0dd89a3d8030e6464e736ad8b2c92c562eee2c9e491fa324de6b5d50b558a.ctv",
        "https://stream1.freetv.fun/c8ad10f8d135ebbea6b77779874c1d8a6da07d925786ac11f855c0dfa87b4487.m3u8",
        "https://stream1.freetv.fun/a3cfcb6f4429baaa2f182afaf1ab55003d71ca680ca0afd61968d36b33f26f03.m3u8",
        "https://stream1.freetv.fun/896df839610cd002779fa77d07c040e9559d9fa017c7de06f7be5dd3b9d528ac.m3u8",
        "https://stream1.freetv.fun/653363b4a3d6b0c5850c53b115d02d5e5e57d7d4c2954ef7c4163640e2cdd991.ctv",
        "https://stream1.freetv.fun/9af7ba5bbdf6641cf12253e1112bff185278bce6b4890b2cf8a38d8f3aa909bd.m3u8",
        "https://stream1.freetv.fun/a46f52861e9ace633f2259316777ba8dbeeaffc6dc31608c64a6b0896cb10839.ctv",
        "https://stream1.freetv.fun/a4e7e8c192fe538f4878412af5f611bf10948212e980cf029ac0500a7cb8962f.ctv",
        "http://php.jdshipin.com/PLTV/iptv.php?id=fhzw",
        "http://php.jdshipin.com/TVOD/iptv.php?id=fhzw",
        "http://php.jdshipin.com/TVOD/iptv.php?id=fhzw2",
        "https://cdn6.101.qzz.io/163189/fhzw",
    )

    private fun phoenixHongKong(): TV = directChannel(
        title = "鳳凰香港台",
        alias = "鳳凰香港",
        row = GROUP_NEWS,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/凤凰中文.png",
        "https://stream1.freetv.fun/ada32bf1237524edb183dcdef8285a2da3dd390b3d59be112c09d1a9c38c1077.ctv",
        "https://stream1.freetv.fun/a6e64c90fc24ccf34c0be8409108f8b9e4e4e91f51e8b3daa2bcfe108d2cfb20.m3u8",
        "https://stream1.freetv.fun/75e234378589af95bef39f3fc9b46fd9f199d2b0750b6ef7082cd363b1fb1654.ctv",
        "http://php.jdshipin.com/PLTV/iptv.php?id=fhhk",
        "https://cdn6.101.qzz.io/163189/fhhk",
    )

    private fun viuTvSix(): TV = directChannel(
        title = "ViuTVsix",
        alias = "ViuTVsix",
        row = GROUP_NEWS,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/VIUTV.png",
        "https://stream1.freetv.fun/dafbaa724a18418c77b2d637b28b1a31b7438f3a4dfc9e93e5c5e3b0bc538847.ctv",
        "https://stream1.freetv.fun/c34b453ddb57c4641e22202a5d868b398148f0d4f86cc98beccb2f49ebcd755a.ctv",
        "https://stream1.freetv.fun/2022de93c6175f00f9f45e21257f2a488e5631f779ee9a6780b403d77e2429b2.ctv",
        "https://stream1.freetv.fun/df53d054294bb3499e8c5897908dd1e7a190e2505b2f45abd837a8bc405771fb.ctv",
    )

    private fun rthk32(): TV = directChannel(
        title = "RTHK 32",
        alias = "港台電視32",
        row = GROUP_NEWS,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/RTHK32.png",
        "http://php.jdshipin.com/PLTV/iptv.php?id=rthk32",
        "https://stream1.freetv.fun/6293c65392dc2022f8a7b1f492f09dc41eea5a09ebe9c8ef1ec3a0d9c4065b69.ctv",
        "https://stream1.freetv.fun/3aa2d45ba708b9d42988f70476afacbaa94458c7c45cf49b0092f6e57801d554.ctv",
    )

    private fun tvbXingHe(): TV = directChannel(
        title = "TVB 星河",
        alias = "TVB 星河",
        row = GROUP_MORE,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/无线星河.png",
        "http://php.jdshipin.com/TVOD/iptv.php?id=xinghe",
        "http://php.jdshipin.com/PLTV/iptv.php?id=xinghe",
        "http://php.jdshipin.com/smt.php?id=Xinhe",
        "http://php.jdshipin.com/PLTV/iptv.php?id=tvbxh",
        "https://cdn6.101.qzz.io/163189/tvbxh",
        "https://stream1.freetv.fun/e1f2e0d3c534c1d7b467fa6bf2a647527e6841c6c2d76b14b29e22682fba93f1.ctv",
        "https://stream1.freetv.fun/1014774338dd6fcc6e3c0358712f55964ae23ffb1c8ff3a246979c2583fadff1.ctv",
        "https://stream1.freetv.fun/9bbb5f6b3fe726c6ed82737f4babafc986bc35e8b9a57840b28f69b623855886.ctv",
        "https://stream1.freetv.fun/1c202fca80785f8b4824cdf42054cbbdb391c0debedaa601e797953597ae2eda.ctv",
    )

    private fun millenniumClassic(): TV = directChannel(
        title = "千禧經典台",
        alias = "千禧經典",
        row = GROUP_MORE,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/千禧经典.png",
        "http://php.jdshipin.com/TVOD/iptv.php?id=tvbc",
        "http://php.jdshipin.com/PLTV/iptv.php?id=tvbc",
        "http://php.jdshipin.com:8880/TVOD/iptv.php?id=tvbc",
        "http://php.jdshipin.com/smt.php?id=Tvbclassic",
    )

    private fun rewindClassic(): TV = directChannel(
        title = "重溫經典",
        alias = "重溫經典",
        row = GROUP_MORE,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/重温经典.png",
        "http://php.jdshipin.com/TVOD/iptv.php?id=cwjd",
        "http://php.jdshipin.com/PLTV/iptv.php?id=cwjd",
    )

    private fun meiYaMovie(): TV = directChannel(
        title = "美亞電影",
        alias = "美亞電影",
        row = GROUP_MORE,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/美亚电影.png",
        "http://php.jdshipin.com/TVOD/iptv.php?id=meiya",
        "http://php.jdshipin.com/PLTV/iptv.php?id=meiya",
    )

    private fun macau(): TV = directChannel(
        title = "澳視澳門",
        alias = "澳視澳門",
        row = GROUP_MORE,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/澳视澳门.png",
        "https://stream1.freetv.fun/ao-shi-ao-men-4.m3u8",
        "http://php.jdshipin.com/TVOD/iptv.php?id=asam",
        "http://php.jdshipin.com/PLTV/iptv.php?id=asam",
    )

    private fun macauSports(): TV = directChannel(
        title = "澳門體育",
        alias = "澳門體育",
        row = GROUP_MORE,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/澳视澳门.png",
        "http://cdn6.163189.xyz/163189/amty",
    )

    private fun macauVariety(): TV = directChannel(
        title = "澳門綜藝",
        alias = "澳門綜藝",
        row = GROUP_MORE,
        logo = "https://gcore.jsdelivr.net/gh/taksssss/tv/icon/澳视澳门.png",
        "http://cdn6.163189.xyz/163189/amzy",
    )
}
