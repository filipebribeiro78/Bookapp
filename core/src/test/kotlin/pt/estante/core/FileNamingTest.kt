package pt.estante.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileNamingTest {
    private val day = LocalDate.of(2026, 11, 2)

    @Test
    fun slugRemovesAccentsAndPunctuation() {
        assertEquals("memorial-do-convento", FileNaming.slug("Memorial do Convento"))
        assertEquals("os-lusiadas", FileNaming.slug("Os Lusíadas"))
        assertEquals("a-cidade-e-as-serras-romance", FileNaming.slug("A Cidade e as Serras: Romance"))
        assertEquals("acao-coracao-pao", FileNaming.slug("Ação, Coração & Pão"))
        assertEquals("oneill-poesia-completa", FileNaming.slug("O'Neill — Poesia Completa"))
        assertEquals("oeuvres-completes", FileNaming.slug("Œuvres complètes"))
        assertEquals("1984", FileNaming.slug("1984"))
    }

    @Test
    fun slugFallsBackWhenNothingIsLeft() {
        assertEquals("book", FileNaming.slug("   "))
        assertEquals("book", FileNaming.slug("¿?!"))
    }

    @Test
    fun longTitlesAreCutAtAWordBoundary() {
        val slug = FileNaming.slug(
            "A Very Long Title That Goes On About The History Of Portuguese Literature In The Twentieth Century",
        )
        assertTrue(slug.length <= 60, "was ${slug.length} characters: $slug")
        assertFalse(slug.endsWith("-"))
        assertTrue(slug.startsWith("a-very-long-title-that-goes-on"))
    }

    @Test
    fun fileNameFollowsBookDateAndPageOrder() {
        assertEquals(
            "memorial-do-convento_2026-11-02_cover.jpg",
            FileNaming.photoFileName("Memorial do Convento", day, PageType.COVER, 1),
        )
        assertEquals(
            "memorial-do-convento_2026-11-02_interior-2.jpg",
            FileNaming.photoFileName("Memorial do Convento", day, PageType.INTERIOR, 2),
        )
    }

    @Test
    fun positionsStartAtOne() {
        assertFailsWith<IllegalArgumentException> {
            FileNaming.photoFileName("Memorial do Convento", day, PageType.COVER, 0)
        }
    }

    @Test
    fun pageTypeCodesAreUniqueAndFileSafe() {
        val codes = PageType.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { assertTrue(it.matches(Regex("[a-z]+")), it) }
    }
}
