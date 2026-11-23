package com.dragolabs.able.feature.settings.update

/**
 * Able version as published on GitHub Releases (UPD-001, ADR-051).
 *
 * Accepted forms (case-insensitive): `able-0.9-beta`, `able-0.9.1-beta`, `v0.9.0-beta`,
 * `0.9.0-beta`, `1.0`, `able-1.0.0-rc.2`. A missing patch is 0, so `0.9` == `0.9.0`.
 * Semantic-versioning precedence applies: a pre-release (`-beta`) ranks below the same
 * release without one, pre-release identifiers are compared dot by dot (numeric < text,
 * numbers numerically). Build metadata (`+...`) and a trailing `-debug` build-type suffix of
 * the installed `versionName` are ignored.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
    val preRelease: List<String> = emptyList(),
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        return comparePreRelease(preRelease, other.preRelease)
    }

    /** Canonical form shown to the user and stored in preferences, e.g. `0.9.0-beta`. */
    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (preRelease.isNotEmpty()) append('-').append(preRelease.joinToString("."))
    }

    companion object {
        private val PATTERN = Regex(
            "^(?:able[-_]?|v)?(\\d{1,6})\\.(\\d{1,6})(?:\\.(\\d{1,6}))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]*)?$",
            RegexOption.IGNORE_CASE,
        )

        /** Build-type suffixes that never mean a different release. */
        private val IGNORED_SUFFIXES = setOf("debug")

        /** Parses a tag or `versionName`; null when it does not look like an Able version. */
        fun parse(value: String?): AppVersion? {
            val match = PATTERN.matchEntire(value?.trim().orEmpty()) ?: return null
            val (major, minor, patch, pre) = match.destructured
            val identifiers = pre.lowercase()
                .split('.', '-')
                .filter { it.isNotEmpty() }
                .dropLastWhile { it in IGNORED_SUFFIXES }
            return AppVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.ifEmpty { "0" }.toIntOrNull() ?: return null,
                preRelease = identifiers,
            )
        }

        private fun comparePreRelease(a: List<String>, b: List<String>): Int {
            if (a.isEmpty() && b.isEmpty()) return 0
            if (a.isEmpty()) return 1 // a final release ranks above any pre-release
            if (b.isEmpty()) return -1
            for (i in 0 until minOf(a.size, b.size)) {
                val x = a[i]
                val y = b[i]
                val xn = x.toLongOrNull()
                val yn = y.toLongOrNull()
                val c = when {
                    xn != null && yn != null -> xn.compareTo(yn)
                    xn != null -> -1
                    yn != null -> 1
                    else -> x.compareTo(y)
                }
                if (c != 0) return c
            }
            return a.size.compareTo(b.size)
        }
    }
}
