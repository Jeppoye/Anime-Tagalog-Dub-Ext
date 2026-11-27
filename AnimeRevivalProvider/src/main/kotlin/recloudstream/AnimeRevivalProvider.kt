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
        return doc.select("a").mapNotNull { element ->
            val title = element.text().trim()
            val href = element.attr("abs:href")
            if (title.isNotEmpty() && href.startsWith(mainUrl) && href != "$mainUrl/") {
                newAnimeSearchResponse(title, href, TvType.Anime) {
                    posterUrl = element.selectFirst("img")?.attr("abs:src")
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
            val isEpisodeLink = epUrl.startsWith(mainUrl) &&
                epUrl != url &&
                !epUrl.endsWith("/") &&
                !epUrl.contains("/category/") &&
                !epUrl.contains("/tag/") &&
                epName.isNotEmpty() &&
                !epName.equals("Home", ignoreCase = true) &&
                !epName.contains("Anime Movies", ignoreCase = true) &&
                !epName.contains("Anime Series", ignoreCase = true) &&
                (epUrl.contains("/e") || epName.matches(Regex(".*\\d+.*")))

            if (isEpisodeLink) {
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
        val iframeUrl = doc.selectFirst("iframe[src], iframe[data-src]")
            ?.let { it.attr("abs:src").ifBlank { it.attr("abs:data-src") } }

        if (!iframeUrl.isNullOrBlank()) {
            loadExtractor(iframeUrl, data, subtitleCallback, callback)
            return true
        }

        val videoUrl = doc.selectFirst("video source[src], video[src]")
            ?.let { it.attr("abs:src") }

        if (!videoUrl.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    videoUrl,
                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = data
                }
            )
            return true
        }

        return false
    }

}
