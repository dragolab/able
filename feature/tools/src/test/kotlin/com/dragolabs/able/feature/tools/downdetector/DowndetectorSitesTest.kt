package com.dragolabs.able.feature.tools.downdetector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DowndetectorSitesTest {

    @Test
    fun slugifies() {
        assertEquals("whatsapp", DowndetectorSites.slugify("WhatsApp"))
        assertEquals("intesa-sanpaolo", DowndetectorSites.slugify("  Intesa   Sanpaolo "))
        assertEquals("poste-italiane", DowndetectorSites.slugify("Poste Italiane!"))
        assertEquals("societe-generale", DowndetectorSites.slugify("Société Générale"))
        assertEquals("att", DowndetectorSites.slugify("AT&T"))
        assertEquals("", DowndetectorSites.slugify(" !! "))
    }

    @Test
    fun buildsLocalizedStatusUrls() {
        assertEquals("https://downdetector.it/problemi/whatsapp/", DowndetectorSites.forCountry("it").statusUrl("WhatsApp"))
        assertEquals("https://downdetector.com/status/netflix/", DowndetectorSites.forCountry("US").statusUrl("Netflix"))
        assertEquals("https://xn--allestrungen-9ib.de/stoerung/telekom/", DowndetectorSites.forCountry("DE").statusUrl("Telekom"))
        assertNull(DowndetectorSites.DEFAULT.statusUrl("  "))
        assertEquals("https://downdetector.co.uk/", DowndetectorSites.forCountry("GB").homeUrl)
    }

    @Test
    fun unknownCountriesFallBackToCom() {
        assertEquals(DowndetectorSites.DEFAULT, DowndetectorSites.forCountry("SE"))
        assertEquals(DowndetectorSites.DEFAULT, DowndetectorSites.forCountry(null))
    }

    @Test
    fun defaultPrefersNetworkThenSimThenLocale() {
        assertEquals("IT", DowndetectorSites.resolveDefault("it", "fr", "DE").countryIso)
        assertEquals("FR", DowndetectorSites.resolveDefault("", "fr", "DE").countryIso)
        assertEquals("DE", DowndetectorSites.resolveDefault(null, "xx", "DE").countryIso)
        assertEquals("US", DowndetectorSites.resolveDefault(null, null, "SE").countryIso)
    }

    @Test
    fun countryCodesAreUniqueAndHostsAscii() {
        val codes = DowndetectorSites.ALL.map { it.countryIso }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(DowndetectorSites.ALL.all { site -> site.host.all { it.code < 128 } })
    }

    @Test
    fun italianSuggestionsIncludeProviders() {
        val it = DowndetectorSites.suggestions("IT").map { it.slug }
        assertTrue(it.containsAll(listOf("tim", "vodafone", "windtre", "iliad", "fastweb", "whatsapp")))
        assertTrue(DowndetectorSites.suggestions("US").none { it.slug == "tim" })
    }

    @Test
    fun favouritesDeduplicateAndCap() {
        var favs = DowndetectorSites.addFavourite(emptyList(), "WhatsApp")
        favs = DowndetectorSites.addFavourite(favs, " whatsapp ")
        assertEquals(listOf("whatsapp"), favs)
        assertEquals(favs, DowndetectorSites.addFavourite(favs, "!!"))
        repeat(40) { favs = DowndetectorSites.addFavourite(favs, "Service $it") }
        assertEquals(DowndetectorSites.MAX_FAVOURITES, favs.size)
        assertEquals("Service 39", favs.last())
        assertTrue(DowndetectorSites.removeFavourite(favs, "SERVICE 39").none { it == "Service 39" })
    }
}
