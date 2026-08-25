package moe.rukamori.archivetune.lyrics

import java.util.Locale

object LyricsFilter {
    // Default list — substring matching means stems cover variants
    // (fuck → fucking/fucker/motherfucker, shit → bullshit/shitty).
    // Words prone to false positives ("ass" ⊂ class/bass, "cum" ⊂ document,
    // "cock" ⊂ peacock, "hoe" ⊂ shoe, "spic" ⊂ spice) are excluded;
    // users can add them via custom list if wanted.
    val defaultWords = listOf(
        // general profanity
        "fuck", "shit", "bitch", "bastard", "damn", "piss", "crap",
        "asshole", "jackass", "dumbass", "douche",
        // sexual
        "dick", "pussy", "cunt", "cock sucker", "blowjob", "handjob", "rimjob",
        "dildo", "orgasm", "jizz", "gangbang", "porn", "horny",
        "slut", "whore", "skank", "tits", "boobs", "wank", "prick", "twat",
        "bollocks", "shag", "molest", "rape",
        // slurs
        "nigga", "nigger", "faggot", "fag", "kike", "chink", "tranny", "dyke",
        "wetback", "beaner", "retard", "retarded", "cripple",
    )

    fun parseWords(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(Regex("[,\\n\\r]+"))
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun wordsToString(words: List<String>): String = words.joinToString(",")

    fun effectiveWords(useDefault: Boolean, customRaw: String?): List<String> {
        val custom = parseWords(customRaw)
        return if (useDefault) {
            if (custom.isEmpty()) defaultWords else (defaultWords + custom).distinct()
        } else custom
    }

    fun containsFiltered(text: String, filterWords: List<String>): Boolean {
        if (filterWords.isEmpty() || text.isBlank()) return false
        val lower = text.lowercase(Locale.ROOT)
        return filterWords.any { w ->
            // whole-word vs substring: use substring for now (covers "fucking")
            lower.contains(w)
        }
    }

    fun censorText(text: String, filterWords: List<String>, maskChar: String = "*"): String {
        if (filterWords.isEmpty() || text.isBlank()) return text
        var out = text
        filterWords.forEach { w ->
            if (w.isBlank()) return@forEach
            // case-insensitive, word-boundary-ish: replace any occurrence
            val regex = Regex(Regex.escape(w), RegexOption.IGNORE_CASE)
            val mask = maskChar.repeat(w.length).ifEmpty { "***" }
            out = regex.replace(out, mask)
        }
        return out
    }

    fun filterWordsInLine(line: LyricsEntry, filterWords: List<String>): List<WordTimestamp> {
        if (filterWords.isEmpty() || line.words.isNullOrEmpty()) return emptyList()
        return line.words.filter { w -> containsFiltered(w.text, filterWords) }
    }

    fun skipIntervals(entries: List<LyricsEntry>, filterWords: List<String>): List<Pair<Long, Long>> {
        if (filterWords.isEmpty() || entries.isEmpty()) return emptyList()
        val intervals = mutableListOf<Pair<Long, Long>>()
        // NOTE: indexed iteration — indexOf() would match an EARLIER identical
        // line (repeated choruses!) and compute a bogus next-time, dropping
        // the interval entirely.
        entries.forEachIndexed { idx, e ->
            if (e.words != null) {
                e.words.filter { w -> containsFiltered(w.text, filterWords) }.forEach { w ->
                    val s = (w.startTime * 1000).toLong()
                    // some providers emit 0-duration first words; guarantee a
                    // minimal mute window so word-at-start isn't dropped
                    val en = maxOf((w.endTime * 1000).toLong(), s + 120)
                    intervals.add(s to en)
                }
            } else if (e.time >= 0 && containsFiltered(e.text, filterWords)) {
                // line-level fallback: skip whole line until next line
                val nextTime = entries.getOrNull(idx + 1)?.time ?: (e.time + 3000)
                if (nextTime > e.time) intervals.add(e.time to nextTime)
            }
        }
        // merge overlapping/adjacent so consecutive filtered words mute continuously
        val sorted = intervals.sortedBy { it.first }
        val merged = mutableListOf<Pair<Long, Long>>()
        sorted.forEach { (s, e) ->
            val last = merged.lastOrNull()
            if (last != null && s <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, e)
            } else {
                merged.add(s to e)
            }
        }
        return merged
    }

    fun nextSkipEnd(positionMs: Long, intervals: List<Pair<Long, Long>>): Long? {
        intervals.forEach { (s, e) ->
            if (positionMs in s until e) return e + 30 // small pad
            if (positionMs < s && s - positionMs < 120) return null // not yet
        }
        return null
    }

    fun isInFilteredInterval(positionMs: Long, intervals: List<Pair<Long, Long>>): Pair<Long, Long>? {
        return intervals.firstOrNull { (s, e) -> positionMs in s until e }
    }
}
