package pt.estante.core

/** Condition of a copy. [label] is the Portuguese wording used in the Sheet and in listings. */
enum class Condition(val label: String, val englishLabel: String) {
    LIKE_NEW("Como novo", "Like new"),
    VERY_GOOD("Muito bom", "Very good"),
    GOOD("Bom", "Good"),
    ACCEPTABLE("Aceitável", "Acceptable"),
}
