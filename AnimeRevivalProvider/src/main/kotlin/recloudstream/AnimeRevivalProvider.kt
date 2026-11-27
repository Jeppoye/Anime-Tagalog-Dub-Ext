package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

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
        val blockedTitles = setOf(
            "home", "anime movies", "anime series",
            "contact", "about", "privacy policy", "dmca"
        )

        return doc.select("a[href]").mapNotNull { el ->
            val title = el.text().trim()
            val href = el.attr("abs:href")
            val isBlockedUrl = href.contains("/category/") ||
                href.contains("/tag/") ||
                href.contains("/page/")
            val isBlockedTitle = title.isEmpty() ||
                blockedTitles.any { title.equals(it, ignoreCase = true) }
            val matchesQuery = title.lowercase().contains(cleanQuery) ||
                href.lowercase().contains(cleanQuery)

            if (title.isNotEmpty() &&
                href.startsWith(mainUrl) &&
                href != "$mainUrl/" &&
                !isBlockedUrl &&
                !isBlockedTitle &&
                matchesQuery
            ) {
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

        val blockedKeywords = listOf(
            "download", "other", "opening", "ending",
            "soundtrack", "sound track", "sound trip",
            "random videos", "requests", "register",
            "account", "tv shows", "ost",
            "report dead links", "tv tokyo", "updated", "chatbox"
        )

        val episodes = doc.select("a[href]").mapNotNull { el ->
            val epUrl = el.attr("abs:href")
            val epName = el.text().trim()
            val lowerUrl = epUrl.lowercase()
            val lowerName = epName.lowercase()

            val isJunkName = epName.isBlank() ||
                epName.length <= 2 ||
                epName.all { !it.isLetter() } ||
                blockedKeywords.any { lowerName.contains(it) }

            val isNonEpisode = isJunkName ||
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
            val raw = el.attr("abs:src")
                .ifBlank { el.attr("abs:data-src") }
                .ifBlank { el.attr("abs:data-url") }
                .ifBlank { el.attr("abs:data-lazy-src") }
                .ifBlank { el.attr("src") }
                .ifBlank { el.attr("data-src") }
                .ifBlank { el.attr("data-url") }
                .trim()
            if (raw.isBlank()) null else fixUrl(raw)
        }.distinct()

        for (link in frameUrls) {
            if (loadExtractor(link, data, subtitleCallback, callback)) {
                found = true
            }
        }

        val dataAttrUrls = doc.select("[data-src], [data-url], [data-link], [data-embed], [data-player]")
            .mapNotNull { el ->
                val raw = el.attr("abs:data-src")
                    .ifBlank { el.attr("abs:data-url") }
                    .ifBlank { el.attr("abs:data-link") }
                    .ifBlank { el.attr("abs:data-embed") }
                    .ifBlank { el.attr("abs:data-player") }
                    .trim()
                if (raw.isBlank()) null else fixUrl(raw)
            }.distinct()

        for (attrUrl in dataAttrUrls) {
            if (loadExtractor(attrUrl, data, subtitleCallback, callback)) {
                found = true
            }
        }

        val regexUrls = Regex(
            """https?://[^\s"'<>\\)]+(?:\.m3u8|\.mp4|embed|player|stream)[^\s"'<>\\)]*""",
            RegexOption.IGNORE_CASE
        )
            .findAll(rawHtml)
            .map { match ->
                match.value
                    .replace("\\/", "/")
                    .replace("&amp;", "&")
                    .trimEnd(',', ';', ')', ']')
            }
            .distinct()

        for (mediaUrl in regexUrls) {
            if (mediaUrl.contains(".m3u8", ignoreCase = true) || mediaUrl.contains(".mp4", ignoreCase = true)) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = mediaUrl,
                        type = if (mediaUrl.contains(".m3u8", ignoreCase = true)) {
                            ExtractorLinkType.M3U8
                        } else {
                            ExtractorLinkType.VIDEO
                        }
                    ) {
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                    }
                )
                found = true
            } else if (loadExtractor(mediaUrl, data, subtitleCallback, callback)) {
                found = true
            }
        }

        return found
    }
}
