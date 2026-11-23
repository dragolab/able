package com.dragolabs.able.feature.tools.downdetector

import java.text.Normalizer
import java.util.Locale

/**
 * A Downdetector country site (NET-002).
 *
 * [host] is the ASCII (punycode) host used in URLs; [displayHost] is what the user sees.
 * [statusPath] is the localized first path segment of a service page, for example
 * `https://downdetector.it/problemi/whatsapp/`.
 */
data class DowndetectorSite(
    val countryIso: String,
    val host: String,
    val displayHost: String = host,
    val statusPath: String,
) {
    val homeUrl: String get() = "https://$host/"

    fun statusUrl(serviceName: String): String? =
        DowndetectorSites.slugify(serviceName).takeIf { it.isNotEmpty() }?.let(::statusUrlForSlug)

    fun statusUrlForSlug(slug: String): String = "https://$host/$statusPath/$slug/"
}

/** A suggested service: [name] shown to the user, [slug] verified on the country site. */
data class SuggestedService(val name: String, val slug: String)

/**
 * Country sites verified on 2026-11-24 in a browser: the host list comes from the `hreflang`
 * alternates published on downdetector.com, and each [DowndetectorSite.statusPath] was read
 * from the service links on that country's homepage. Downdetector has no public API and its
 * robots.txt disallows `/` for generic crawlers, so Able never fetches these pages itself; it
 * only opens them in the user's browser.
 *
 * Countries with a Downdetector site that were not verified (for example Sweden, Poland,
 * Japan, Austria and Switzerland, whose site redirects by language) are intentionally not
 * listed; they fall back to [DEFAULT] and the user can pick another country.
 */
object DowndetectorSites {

    val DEFAULT = DowndetectorSite("US", "downdetector.com", statusPath = "status")

    val ALL: List<DowndetectorSite> = listOf(
        DEFAULT,
        DowndetectorSite("GB", "downdetector.co.uk", statusPath = "status"),
        DowndetectorSite("IE", "downdetector.ie", statusPath = "status"),
        DowndetectorSite("CA", "downdetector.ca", statusPath = "status"),
        DowndetectorSite("AU", "downdetector.com.au", statusPath = "status"),
        DowndetectorSite("IT", "downdetector.it", statusPath = "problemi"),
        DowndetectorSite("DE", "xn--allestrungen-9ib.de", displayHost = "allestörungen.de", statusPath = "stoerung"),
        DowndetectorSite("FR", "downdetector.fr", statusPath = "statut"),
        DowndetectorSite("ES", "downdetector.es", statusPath = "problemas"),
        DowndetectorSite("MX", "downdetector.mx", statusPath = "problemas"),
        DowndetectorSite("BR", "downdetector.com.br", statusPath = "fora-do-ar"),
        DowndetectorSite("PT", "downdetector.pt", statusPath = "fora-do-ar"),
        DowndetectorSite("NL", "allestoringen.nl", statusPath = "storing"),
        DowndetectorSite("BE", "allestoringen.be", statusPath = "storing"),
    )

    /** Services whose slug is the plain lowercase name on every listed site (checked on .it and .com). */
    val COMMON_SUGGESTIONS = listOf(
        SuggestedService("WhatsApp", "whatsapp"),
        SuggestedService("Instagram", "instagram"),
        SuggestedService("Facebook", "facebook"),
        SuggestedService("TikTok", "tiktok"),
        SuggestedService("Google", "google"),
        SuggestedService("YouTube", "youtube"),
        SuggestedService("Gmail", "gmail"),
        SuggestedService("Netflix", "netflix"),
        SuggestedService("Amazon", "amazon"),
    )

    /** Country-specific suggestions, slugs read from that country's homepage. */
    private val COUNTRY_SUGGESTIONS = mapOf(
        "IT" to listOf(
            SuggestedService("TIM", "tim"),
            SuggestedService("Vodafone", "vodafone"),
            SuggestedService("WindTre", "windtre"),
            SuggestedService("Iliad", "iliad"),
            SuggestedService("Fastweb", "fastweb"),
            SuggestedService("Poste", "poste"),
        ),
    )

    fun suggestions(countryIso: String): List<SuggestedService> =
        COUNTRY_SUGGESTIONS[countryIso.uppercase(Locale.ROOT)].orEmpty() + COMMON_SUGGESTIONS

    fun forCountry(iso: String?): DowndetectorSite {
        val code = iso?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return ALL.firstOrNull { it.countryIso == code } ?: DEFAULT
    }

    fun isSupported(iso: String?): Boolean {
        val code = iso?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return ALL.any { it.countryIso == code }
    }

    /**
     * Default country: network country, then SIM country, then the app locale's country, each
     * used only when Downdetector has a verified site for it; otherwise [DEFAULT].
     */
    fun resolveDefault(networkIso: String?, simIso: String?, localeIso: String?): DowndetectorSite =
        listOf(networkIso, simIso, localeIso).firstOrNull { isSupported(it) }?.let { forCountry(it) } ?: DEFAULT

    /**
     * Service name to Downdetector slug: lowercase, accents removed, `&` and `+` dropped,
     * every other run of non-alphanumerics becomes one dash, no leading/trailing dashes.
     * "Intesa Sanpaolo" -> "intesa-sanpaolo", "Wind Tre" -> "wind-tre".
     */
    fun slugify(name: String): String {
        val ascii = Normalizer.normalize(name.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return ascii
            .replace(Regex("[&+'’]"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_SLUG_LENGTH)
            .trimEnd('-')
    }

    /** Adds [name] to [favourites] (case-insensitive de-duplication, newest last, capped). */
    fun addFavourite(favourites: List<String>, name: String): List<String> {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)
        if (slugify(clean).isEmpty()) return favourites
        val without = favourites.filterNot { slugify(it) == slugify(clean) }
        return (without + clean).takeLast(MAX_FAVOURITES)
    }

    fun removeFavourite(favourites: List<String>, name: String): List<String> =
        favourites.filterNot { slugify(it) == slugify(name) }

    const val MAX_FAVOURITES = 30
    const val MAX_NAME_LENGTH = 60
    private const val MAX_SLUG_LENGTH = 80
}
