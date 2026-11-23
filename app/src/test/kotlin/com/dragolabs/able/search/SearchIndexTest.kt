package com.dragolabs.able.search

import com.dragolabs.able.core.model.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    private val shellDeepLinks = setOf("home", "battery", "apps", "tools", "settings", "tiles")

    @Test
    fun idsAreUnique() {
        val ids = SearchIndex.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyToolHasAnEntry() {
        val links = SearchIndex.entries.map { it.deepLink }.toSet()
        ToolId.entries.forEach { tool ->
            assertTrue("missing tool/${tool.key}", "tool/${tool.key}" in links)
        }
    }

    @Test
    fun everySectionIsCoveredAndPopularIsNotEmpty() {
        SearchSection.entries.forEach { section ->
            assertTrue("no entries for $section", SearchIndex.entries.any { it.section == section })
        }
        assertTrue(SearchIndex.popular.isNotEmpty())
    }

    @Test
    fun deepLinksAreShellDeepLinksWithoutFragments() {
        SearchIndex.entries.forEach { entry ->
            val link = entry.deepLink
            assertTrue("${entry.id}: $link", '#' !in link)
            val valid = link in shellDeepLinks || link.startsWith("tool/") && ToolId.fromKey(link.removePrefix("tool/")) != null
            assertTrue("${entry.id}: $link", valid)
            assertEquals(link, SearchDeepLink.base(entry.deepLinkWithAnchor))
        }
    }
}
