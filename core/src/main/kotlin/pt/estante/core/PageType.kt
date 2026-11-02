package pt.estante.core

/** A kind of photo taken of a book. [code] is what goes into file names and the Sheet. */
enum class PageType(val code: String, val label: String) {
    COVER("cover", "Cover"),
    BACK("back", "Back cover"),
    TITLE("title", "Title page"),
    COPYRIGHT("copyright", "Copyright / edition page"),
    CONTENTS("contents", "Contents"),
    SIGNATURE("signature", "Signature / dedication"),
    DAMAGE("damage", "Damage"),
    INTERIOR("interior", "Other page");

    companion object {
        /** The shots most books need, in the order the camera screen walks through them. */
        val guidedOrder: List<PageType> = listOf(COVER, BACK, TITLE, COPYRIGHT)
    }
}
