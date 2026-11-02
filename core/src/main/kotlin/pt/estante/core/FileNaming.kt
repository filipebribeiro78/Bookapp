package pt.estante.core

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

object FileNaming {
    private val ligatures = mapOf(
        "æ" to "ae", "œ" to "oe", "ø" to "o", "ß" to "ss",
        "ł" to "l", "đ" to "d", "ð" to "d", "þ" to "th",
    )
    private val combiningMarks = Regex("\\p{M}+")
    private val apostrophes = Regex("['’`´]")
    private val separators = Regex("[^a-z0-9]+")

    /** Lowercase ASCII words joined by hyphens: "Os Lusíadas" becomes "os-lusiadas". */
    fun slug(title: String, maxLength: Int = 60): String {
        var text = title.lowercase(Locale.ROOT)
        ligatures.forEach { (from, to) -> text = text.replace(from, to) }
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(apostrophes, "")
            .replace(separators, "-")
            .trim('-')
        if (text.length > maxLength) {
            val cut = text.substring(0, maxLength)
            val lastHyphen = cut.lastIndexOf('-')
            text = (if (lastHyphen >= maxLength / 2) cut.substring(0, lastHyphen) else cut).trim('-')
        }
        return text.ifEmpty { "book" }
    }

    /**
     * Book name, date of the photo and type of page, for example
     * `memorial-do-convento_2026-11-02_cover.jpg`. The second photo of a type gets `-2`, and so on.
     */
    fun photoFileName(
        title: String,
        takenOn: LocalDate,
        pageType: PageType,
        positionInType: Int,
        extension: String = "jpg",
    ): String {
        require(positionInType >= 1) { "positionInType starts at 1, was $positionInType" }
        val counter = if (positionInType > 1) "-$positionInType" else ""
        return "${slug(title)}_${takenOn}_${pageType.code}$counter.$extension"
    }
}
