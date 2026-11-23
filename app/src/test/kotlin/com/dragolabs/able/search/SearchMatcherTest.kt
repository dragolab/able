package com.dragolabs.able.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {

    private val torch = SearchFields(title = "Torch", keywords = "torcia, flashlight, luce", context = "Tools")
    private val theme = SearchFields(title = "Theme", keywords = "dark mode, tema scuro", context = "Settings › Appearance")
    private val monitor = SearchFields(title = "Live monitor", keywords = "drain, consumo", context = "Battery › Monitor")
    private val luminosita = SearchFields(title = "Luminosità automatica", context = "Strumenti")

    @Test
    fun foldKeepsLengthAndRemovesCaseAndDiacritics() {
        val text = "Luminosità, PERCHÉ!"
        val folded = SearchMatcher.fold(text)
        assertEquals(text.length, folded.length)
        assertEquals("luminosita  perche ", folded)
    }

    @Test
    fun blankQueryMatchesNothing() {
        assertNull(SearchMatcher.match("", torch))
        assertNull(SearchMatcher.match("  , ", torch))
    }

    @Test
    fun titlePrefixMatchesAndHighlights() {
        val match = SearchMatcher.match("tor", torch)!!
        assertEquals(listOf(0..2), match.titleHighlights)
    }

    @Test
    fun caseAndDiacriticInsensitive() {
        assertNotNull(SearchMatcher.match("LUMINOSITA", luminosita))
        assertNotNull(SearchMatcher.match("luminosità", luminosita))
        assertNotNull(SearchMatcher.match("Automatica", luminosita))
    }

    @Test
    fun keywordSynonymsMatchWithoutTitleHighlight() {
        val italian = SearchMatcher.match("torcia", torch)!!
        assertTrue(italian.titleHighlights.isEmpty())
        assertNotNull(SearchMatcher.match("tema scuro", theme))
        assertNotNull(SearchMatcher.match("dark", theme))
    }

    @Test
    fun allTokensMustMatch() {
        assertNotNull(SearchMatcher.match("live mon", monitor))
        assertNull(SearchMatcher.match("live compass", monitor))
    }

    @Test
    fun contextMatchesBreadcrumb() {
        assertNotNull(SearchMatcher.match("battery", monitor))
        assertNotNull(SearchMatcher.match("battery drain", monitor))
    }

    @Test
    fun substringInTitleMatchesButSingleLetterOnlyMatchesWordStarts() {
        val sub = SearchMatcher.match("onit", monitor)!!
        assertEquals(listOf(6..9), sub.titleHighlights)
        assertNull(SearchMatcher.match("q", torch))
        assertNotNull(SearchMatcher.match("t", torch))
    }

    @Test
    fun scoringPrefersTitleOverKeywordsOverContext() {
        val title = SearchMatcher.match("monitor", monitor)!!.score
        val keyword = SearchMatcher.match("consumo", monitor)!!.score
        val context = SearchMatcher.match("battery", monitor)!!.score
        assertTrue(title > keyword)
        assertTrue(keyword > context)
    }

    @Test
    fun exactTitleBeatsPrefix() {
        val exact = SearchFields(title = "Storage")
        val longer = SearchFields(title = "Storage access")
        assertTrue(SearchMatcher.match("storage", exact)!!.score > SearchMatcher.match("storage", longer)!!.score)
    }

    @Test
    fun rankOrdersByScoreThenShorterTitle() {
        val items = listOf(
            SearchFields(title = "Reset Home layout", keywords = "torch"),
            SearchFields(title = "Torch shortcuts tile"),
            torch,
        )
        val ranked = SearchMatcher.rank("torch", items) { it }.map { it.item.title }
        assertEquals(listOf("Torch", "Torch shortcuts tile", "Reset Home layout"), ranked)
    }

    @Test
    fun highlightsAreMergedAndSorted() {
        val fields = SearchFields(title = "Battery history retention")
        val match = SearchMatcher.match("retention battery bat", fields)!!
        assertEquals(listOf(0..6, 16..24), match.titleHighlights)
        assertEquals(listOf(0..5, 8..9), SearchMatcher.mergeRanges(listOf(8..9, 0..2, 3..5)))
    }

    @Test
    fun deepLinkAnchorsSplitAndCompose() {
        assertEquals("battery#monitor.alerts", SearchDeepLink.compose("battery", "monitor.alerts"))
        assertEquals("battery", SearchDeepLink.compose("battery", null))
        assertEquals("tool/lock" to "double_tap", SearchDeepLink.split("tool/lock#double_tap"))
        assertEquals("settings" to null, SearchDeepLink.split("settings"))
        assertEquals("settings" to null, SearchDeepLink.split("settings#"))
        assertEquals("battery", SearchDeepLink.base("battery#unknown.fragment"))
    }

    @Test
    fun recentSearchesDeduplicateAndCap() {
        var list = emptyList<String>()
        list = RecentSearches.add(list, "Torch")
        list = RecentSearches.add(list, "  tema   scuro ")
        list = RecentSearches.add(list, "TORCH")
        assertEquals(listOf("TORCH", "tema scuro"), list)
        assertEquals(list, RecentSearches.add(list, "   "))
        repeat(20) { list = RecentSearches.add(list, "q$it") }
        assertEquals(RecentSearches.MAX, list.size)
        assertEquals("q19", list.first())
    }
}
