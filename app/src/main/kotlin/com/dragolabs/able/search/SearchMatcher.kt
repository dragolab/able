package com.dragolabs.able.search

import java.text.Normalizer

/** Localized text of one searchable entry. */
data class SearchFields(
    val title: String,
    /** Synonyms, usually a comma-separated list ("torcia, flashlight, luce"). */
    val keywords: String = "",
    /** Breadcrumb or section names ("Battery › Monitor"). */
    val context: String = "",
)

/** Result of matching a query against one entry. [titleHighlights] index into the original title. */
data class SearchMatch(
    val score: Int,
    val titleHighlights: List<IntRange>,
)

data class RankedResult<T>(val item: T, val match: SearchMatch)

/**
 * Pure Kotlin matcher for the app-wide options search.
 *
 * - Case- and diacritic-insensitive ("perché" matches "perche", "LUMINOSITÀ" matches "luminosita").
 * - The query is tokenized on whitespace and punctuation; every token must match the title,
 *   the keywords or the context (AND semantics), otherwise the entry is excluded.
 * - Scoring per token: title word prefix > title substring > keyword word prefix >
 *   keyword substring > context word prefix > context substring. Bonuses when the whole query is
 *   a prefix of, or equal to, the title.
 * - Substring matches need at least 2 characters in the title and 3 in keywords/context, so a
 *   single letter only matches word starts.
 */
object SearchMatcher {

    private const val TITLE_FIRST_WORD_PREFIX = 120
    private const val TITLE_WORD_PREFIX = 100
    private const val TITLE_WHOLE_WORD_BONUS = 10
    private const val TITLE_CONTAINS = 60
    private const val KEYWORD_WORD_PREFIX = 50
    private const val KEYWORD_CONTAINS = 30
    private const val CONTEXT_WORD_PREFIX = 25
    private const val CONTEXT_CONTAINS = 10
    private const val QUERY_PREFIX_OF_TITLE_BONUS = 40
    private const val QUERY_EQUALS_TITLE_BONUS = 80

    /**
     * Folds [text] to lowercase without diacritics. Letters and digits keep their position;
     * every other character becomes a space, so the result has exactly the same length as
     * [text] and indexes can be mapped back for highlighting.
     */
    fun fold(text: String): String {
        val out = CharArray(text.length)
        for (i in text.indices) {
            out[i] = foldChar(text[i])
        }
        return String(out)
    }

    private fun foldChar(c: Char): Char {
        if (!c.isLetterOrDigit()) return ' '
        if (c.code < 128) return c.lowercaseChar()
        val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        val base = decomposed.firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() } ?: c
        return base.lowercaseChar()
    }

    /** Folded, non-empty tokens of [text]. */
    fun tokens(text: String): List<String> = fold(text).split(' ').filter { it.isNotEmpty() }

    /** Normalized form of a query, used to de-duplicate recent searches. */
    fun normalizeQuery(text: String): String = tokens(text).joinToString(" ")

    /** Matches [query] against [fields]; `null` when the query is blank or any token is missing. */
    fun match(query: String, fields: SearchFields): SearchMatch? {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return null
        val title = fold(fields.title)
        val keywords = fold(fields.keywords)
        val context = fold(fields.context)
        val titleWordStarts = wordStarts(title)

        var total = 0
        val highlights = mutableListOf<IntRange>()
        for (token in queryTokens) {
            var best = 0
            var bestRange: IntRange? = null

            val prefixAt = titleWordStarts.firstOrNull { title.startsWith(token, it) }
            if (prefixAt != null) {
                best = if (prefixAt == 0) TITLE_FIRST_WORD_PREFIX else TITLE_WORD_PREFIX
                val end = prefixAt + token.length
                if (end == title.length || title[end] == ' ') best += TITLE_WHOLE_WORD_BONUS
                bestRange = prefixAt until end
            } else if (token.length >= 2) {
                val at = title.indexOf(token)
                if (at >= 0) {
                    best = TITLE_CONTAINS
                    bestRange = at until at + token.length
                }
            }
            if (best < KEYWORD_WORD_PREFIX) {
                best = maxOf(best, secondaryScore(token, keywords, KEYWORD_WORD_PREFIX, KEYWORD_CONTAINS))
            }
            if (best < CONTEXT_WORD_PREFIX) {
                best = maxOf(best, secondaryScore(token, context, CONTEXT_WORD_PREFIX, CONTEXT_CONTAINS))
            }
            if (best == 0) return null
            total += best
            bestRange?.let(highlights::add)
        }

        val normalizedQuery = queryTokens.joinToString(" ")
        val normalizedTitle = tokens(fields.title).joinToString(" ")
        total += when {
            normalizedTitle == normalizedQuery -> QUERY_EQUALS_TITLE_BONUS
            normalizedTitle.startsWith(normalizedQuery) -> QUERY_PREFIX_OF_TITLE_BONUS
            else -> 0
        }
        return SearchMatch(score = total, titleHighlights = mergeRanges(highlights))
    }

    /** Matches and sorts [items] by descending score, then shorter title, then original order. */
    fun <T> rank(query: String, items: List<T>, fields: (T) -> SearchFields): List<RankedResult<T>> =
        items.mapIndexedNotNull { index, item ->
            val f = fields(item)
            match(query, f)?.let { Triple(RankedResult(item, it), f.title.length, index) }
        }
            .sortedWith(
                compareByDescending<Triple<RankedResult<T>, Int, Int>> { it.first.match.score }
                    .thenBy { it.second }
                    .thenBy { it.third },
            )
            .map { it.first }

    private fun secondaryScore(token: String, folded: String, prefixScore: Int, containsScore: Int): Int {
        if (folded.isBlank()) return 0
        if (wordStarts(folded).any { folded.startsWith(token, it) }) return prefixScore
        if (token.length >= 3 && folded.contains(token)) return containsScore
        return 0
    }

    private fun wordStarts(folded: String): List<Int> =
        folded.indices.filter { folded[it] != ' ' && (it == 0 || folded[it - 1] == ' ') }

    internal fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf(sorted.first())
        for (range in sorted.drop(1)) {
            val last = merged.last()
            if (range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }
}

/**
 * Search deep links are the shell deep links ("battery", "tool/torch", ...) optionally followed
 * by an anchor fragment: `<deepLink>#<area>[.<item>]`, for example `battery#monitor.alerts` or
 * `settings#privacy.crash_reports`. Navigation that does not know an anchor must ignore it.
 */
object SearchDeepLink {
    const val ANCHOR_SEPARATOR = '#'

    fun compose(deepLink: String, anchor: String?): String =
        if (anchor.isNullOrBlank()) deepLink else "$deepLink$ANCHOR_SEPARATOR$anchor"

    /** Splits a search deep link into the shell deep link and the optional anchor. */
    fun split(link: String): Pair<String, String?> {
        val at = link.indexOf(ANCHOR_SEPARATOR)
        if (at < 0) return link to null
        val anchor = link.substring(at + 1).takeIf { it.isNotBlank() }
        return link.substring(0, at) to anchor
    }

    /** The shell deep link without any anchor, always accepted by `routeFromDeepLink`. */
    fun base(link: String): String = split(link).first
}
