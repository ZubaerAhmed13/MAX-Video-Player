package com.zubaer.maxvideoplayer.feature.subtitle

import java.util.Locale

object SubtitleMatcher {
    data class Match(
        val displayName: String,
        val score: Int,
        val language: String?,
    )

    fun score(videoFileName: String, subtitleFileName: String): Match? {
        if (!isSubtitleExtension(subtitleFileName)) return null
        val videoStem = canonicalStem(videoFileName.substringBeforeLast('.', videoFileName))
        val subtitleRawStem = subtitleFileName.substringBeforeLast('.', subtitleFileName)
        val language = detectLanguageSuffix(subtitleRawStem)
        val subtitleStemWithoutLanguage = stripLanguageSuffix(subtitleRawStem)
        val subtitleStem = canonicalStem(subtitleStemWithoutLanguage)
        if (videoStem.isBlank() || subtitleStem.isBlank()) return null

        val generic = subtitleStem in GENERIC_STEMS
        val score = when {
            subtitleStem == videoStem -> if (language != null) 100 else 96
            !generic && subtitleStem.startsWith(videoStem) -> 82
            !generic && videoStem.startsWith(subtitleStem) && subtitleStem.length >= (videoStem.length * 0.8).toInt() -> 70
            else -> return null
        }
        return Match(subtitleFileName, score, language)
    }

    fun sortMatches(videoFileName: String, subtitleNames: Iterable<String>): List<Match> =
        subtitleNames.mapNotNull { score(videoFileName, it) }
            .sortedWith(compareByDescending<Match> { it.score }.thenBy { it.displayName.lowercase() })

    fun detectLanguageSuffix(fileStem: String): String? {
        val candidate = fileStem.split('.', '-', '_', ' ').lastOrNull()?.lowercase()?.trim().orEmpty()
        if (candidate.isBlank()) return null
        LANGUAGE_ALIASES[candidate]?.let { return it }
        if (candidate.length == 2 && candidate.all(Char::isLetter)) {
            val locale = Locale.forLanguageTag(candidate)
            return locale.language.takeIf { it.length == 2 }
        }
        return null
    }

    fun humanLanguageName(language: String?, displayLocale: Locale = Locale.getDefault()): String {
        if (language.isNullOrBlank()) return "Unknown"
        val canonical = LANGUAGE_ALIASES[language.lowercase()] ?: language.lowercase()
        val locale = Locale.forLanguageTag(canonical)
        return locale.getDisplayLanguage(displayLocale).takeIf { it.isNotBlank() } ?: language
    }

    fun canonicalLanguage(language: String?): String? {
        if (language.isNullOrBlank()) return null
        val lower = language.lowercase(Locale.ROOT)
        LANGUAGE_ALIASES[lower]?.let { return it }
        return Locale.forLanguageTag(lower).language.takeIf { it.isNotBlank() }
    }

    private fun stripLanguageSuffix(fileStem: String): String {
        val parts = fileStem.split('.', '-', '_', ' ').filter(String::isNotBlank)
        if (parts.size < 2) return fileStem
        val last = parts.last().lowercase(Locale.ROOT)
        if (LANGUAGE_ALIASES.containsKey(last) || (last.length == 2 && last.all(Char::isLetter))) {
            val cutAt = fileStem.lastIndexOfAny(charArrayOf('.', '-', '_', ' '))
            if (cutAt > 0) return fileStem.substring(0, cutAt)
        }
        return fileStem
    }

    private fun canonicalStem(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[._\\-]+"), " ")
        .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isSubtitleExtension(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in SUBTITLE_EXTENSIONS

    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ssa", "ass", "ttml", "dfxp", "xml")
    private val GENERIC_STEMS = setOf("subtitle", "subtitles", "caption", "captions", "sub")
    private val LANGUAGE_ALIASES = mapOf(
        "en" to "en", "eng" to "en",
        "de" to "de", "deu" to "de", "ger" to "de",
        "bn" to "bn", "ben" to "bn",
        "fr" to "fr", "fra" to "fr", "fre" to "fr",
        "es" to "es", "spa" to "es",
        "ar" to "ar", "ara" to "ar",
        "he" to "he", "heb" to "he",
        "ja" to "ja", "jpn" to "ja",
        "hi" to "hi", "hin" to "hi",
    )
}
