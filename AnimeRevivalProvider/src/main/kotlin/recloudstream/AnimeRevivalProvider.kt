package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class AnimeRevivalProvider : MainAPI() {
    override var mainUrl = "https://animerevival.xyz"
    override var name = "Anime Revival"
    override val supportedTypes = setOf(TvType.Anime)
    override var lang = "tl"
    override val hasMainPage = true

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(mainUrl).document
        val items = doc.select("a").mapNotNull { element ->
            val title = element.text().trim()
            val href = element.attr("abs:href")
            if (title.isNotEmpty() && href.startsWith(mainUrl) && href != "$mainUrl/") {
                newAnimeSearchResponse(title, href, TvType.Anime) {
                    posterUrl = element.selectFirst("img")?.attr("abs:src")
                }
            } else {
                null
            }
        }.distinctBy { it.url }.take(20)

        return newHomePageResponse(
            listOf(HomePageList("Tagalog Dubbed", items)),
            hasNext = false
        )
    }

        override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val cleanQuery = query.trim().lowercase()
        val blockedTitles = setOf("home", "anime movies", "anime series", "contact", "about", "privacy policy", "dmca")

        return doc.select("a[href]").mapNotNull { el ->
            val title = el.text().trim()
            val href = el.attr("abs:href")
            val isBlockedUrl = href.contains("/category/") || href.contains("/tag/") || href.contains("/page/")
            val isBlockedTitle = title.isEmpty() || blockedTitles.any { title.equals(it, ignoreCase = true) }
            val matchesQuery = title.lowercase().contains(cleanQuery) || href.lowercase().contains(cleanQuery)

            if (title.isNotEmpty() && href.startsWith(mainUrl) && href != "$mainUrl/" && !isBlockedUrl && !isBlockedTitle && matchesQuery) {
                newAnimeSearchResponse(title, href, TvType.Anime) {
                    posterUrl = el.selectFirst("img")?.attr("abs:src")
                }
            } else {
                null
            }
        }.distinctBy { it.url }
    }


        override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: name
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = doc.selectFirst("meta[name=description]")?.attr("content")

                val episodes = doc.select("a[href]").mapNotNull { el ->
            val epUrl = el.attr("abs:href")
            val epName = el.text().trim()
            val lowerUrl = epUrl.lowercase()
            val lowerName = epName.lowercase()

            val isNonEpisode = lowerName.contains("download") ||
                lowerName.contains("other") ||
                lowerName.contains("opening") ||
                lowerName.contains("ending") ||
                lowerName.contains("soundtrack") ||
                lowerName.contains("sound track") ||
                lowerName.contains("sound trip") ||
                lowerName.contains("random videos") ||
                lowerName.contains("requests") ||
                lowerName.contains("register") ||
                lowerName.contains("account") ||
                lowerName.contains("tv shows") ||
                lowerName.contains("ost") ||
                lowerUrl.contains("download") ||
                lowerUrl.contains("soundtrack") ||
                lowerUrl.contains("sound-trip") ||
                lowerUrl.contains("opening") ||
                lowerUrl.contains("ending") ||
                lowerUrl.contains("register") ||
                lowerUrl.contains("tv-shows")

            val isBlocked = epUrl == url ||
                epUrl == "$mainUrl/" ||
                lowerUrl.contains("/category/") ||
                lowerUrl.contains("/tag/") ||
                lowerUrl.contains("/page/") ||
                lowerName.isEmpty() ||
                lowerName == "home" ||
                lowerName.contains("anime movies") ||
                lowerName.contains("anime series") ||
                lowerName.contains("contact") ||
                lowerName.contains("privacy") ||
                lowerName.contains("dmca") ||
                isNonEpisode

            if (epUrl.startsWith(mainUrl) && !isBlocked) {
                newEpisode(epUrl) {
                    this.name = epName
                }
            } else {
                null
            }
        }.distinctBy { it.data }




        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = plot
            this.episodes = mutableMapOf(DubStatus.None to episodes)
        }
    }

            override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val rawHtml = doc.html()
        var found = false

        val frameUrls = doc.select("iframe, embed, video, source").mapNotNull { el ->
            val raw = el.attr("abs:src").ifBlank {
                el.attr("abs:data-src").ifBlank {
                    el.attr("src").ifBlank { el.attr("data-src") }
                }
            }.trim()
            if (raw.isBlank()) null else fixUrl(raw)
        }.distinct()

        for (link in frameUrls) {
            if (loadExtractor(link, data, subtitleCallback, callback)) {
                found = true
            }
        }

        val regexUrls = Regex("""https?://[^\s"'<>]+(?:\.m3u8|\.mp4)[^\s"'<>]*""")
            .findAll(rawHtml)
            .map { it.value.replace("\\/", "/") }
            .distinct()

        for (mediaUrl in regexUrls) {
            if (loadExtractor(mediaUrl, data, subtitleCallback, callback)) {
                found = true
            }
        }

        return found
    }


    }


