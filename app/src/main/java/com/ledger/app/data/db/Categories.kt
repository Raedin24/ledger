package com.ledger.app.data.db

/**
 * Which side of the ledger a category belongs to.
 *
 * Money in and money out want genuinely different labels — "Rent" is never
 * income and "Salary" is never a spend — so a single flat list meant every
 * picker was mostly wrong for whichever direction was being sorted. [SHARED] is
 * for the handful that legitimately go both ways.
 */
enum class CategoryKind(val label: String) {
    OUT("Money out"),
    IN("Money in"),
    SHARED("Both"),
    ;

    companion object {
        /** Tolerant parse — an unknown value degrades to spending rather than
         *  disappearing from every picker. */
        fun from(raw: String?): CategoryKind = entries.firstOrNull { it.name == raw } ?: OUT

        /**
         * The order the sides are laid out in, everywhere: out, both, in.
         *
         * Not [entries] order, which is the order the constants happen to be
         * declared. [SHARED] belongs between the two it spans — both because
         * that's how the editor groups its sections, and because stored position
         * is what splits the pickers, so a shared label sorted after the income
         * block would land at the bottom of the spending list rather than
         * alongside the spending labels it sits with.
         */
        val DISPLAY_ORDER: List<CategoryKind> = listOf(OUT, SHARED, IN)
    }
}

/** A seeded category: its label and the side of the ledger it serves. */
data class DefaultCategory(val name: String, val kind: CategoryKind)

/**
 * The starting category set, in display order: what money goes out on, the one
 * label that spans both directions, then where money comes in from.
 *
 * Order here is the order the pickers show, so it is grouped by kind and roughly
 * by how often each is likely to be reached for — not alphabetised.
 */
object DefaultCategories {

    /** Where recognised own-account movements are filed. Must stay in [ALL] so it
     *  has a visual identity out of the box. */
    const val TRANSFERS = "Transfers"

    /** The spending catch-all. Paired with [INCOME_CATCH_ALL] — before the split
     *  a single "Other" served both, which is why the 4→5 migration has to pick
     *  one side for it. */
    const val SPEND_CATCH_ALL = "Misc"
    const val INCOME_CATCH_ALL = "Other"

    /**
     * Where provider charges are pooled in the breakdowns.
     *
     * Deliberately **not** in [ALL], so it is never seeded, never offered by a
     * picker, and never appears in the category editor to be renamed, reordered,
     * hidden or deleted. It exists only as a computed row: the fee column already
     * says which transactions were charged, so there is nothing here for the user
     * to file or maintain.
     *
     * Kept in step by hand with the literal in `CategoryDao`'s breakdown queries —
     * Room needs a constant expression in `@Query`, so the string cannot be
     * interpolated from here.
     */
    const val TRANSACTION_FEES = "Transaction Fees"

    private fun spend(vararg names: String) = names.map { DefaultCategory(it, CategoryKind.OUT) }
    private fun income(vararg names: String) = names.map { DefaultCategory(it, CategoryKind.IN) }

    val ALL: List<DefaultCategory> =
        spend(
            "Food & Drink", "Groceries", "Transport", "Fuel", "Car", "Bike", "Rent",
            "Bills & Utilities", "Data & Airtime", "Health", "Personal Care", "Family",
            "Gifts & Donations", "Shopping", "Fitness", "Entertainment", "Debt Repayment",
            "Savings", "Investment", SPEND_CATCH_ALL,
        ) +
            listOf(DefaultCategory(TRANSFERS, CategoryKind.SHARED)) +
            income(
                "Salary", "Bonus", "Freelance", "Investment Return", "Interest Income",
                "Reimbursement", "Gift Received", "Family Support", INCOME_CATCH_ALL,
            )

    /** Offered when sorting a debit — spending plus anything shared. */
    val OUT_NAMES: List<String> = ALL.filter { it.kind != CategoryKind.IN }.map { it.name }

    /** Offered when sorting a credit — income plus anything shared. */
    val IN_NAMES: List<String> = ALL.filter { it.kind != CategoryKind.OUT }.map { it.name }

    /** Rows for a first-run seed, positioned in declaration order. */
    fun entities(): List<CategoryEntity> = ALL.mapIndexed { i, c ->
        CategoryEntity(name = c.name, position = i, kind = c.kind.name)
    }

    /**
     * What the setup shortlist starts with ticked.
     *
     * Not a guess at this user — nothing is known about them yet — but the labels
     * almost every ledger ends up needing: the everyday spends, the one that
     * covers salary, and both catch-alls, so there is always somewhere to file a
     * transaction that fits nothing else. Everything left over is one tap away on
     * the same screen, and one switch away afterwards.
     */
    val SUGGESTED: Set<String> = setOf(
        "Food & Drink", "Groceries", "Transport", "Rent", "Bills & Utilities",
        "Data & Airtime", "Family", "Shopping", SPEND_CATCH_ALL,
        TRANSFERS,
        "Salary", INCOME_CATCH_ALL,
    )
}

/**
 * The labels a picker should offer, already split by direction.
 *
 * Built once per category-table change rather than filtered at each call site,
 * so a screen showing one card per queued transaction isn't re-partitioning the
 * whole set on every recomposition.
 */
data class CategoryOptions(
    val spending: List<String> = DefaultCategories.OUT_NAMES,
    val income: List<String> = DefaultCategories.IN_NAMES,
) {
    /** Every label, for the pickers that span a mixed selection (History's
     *  filters, the rule editor, a bulk re-categorise). */
    val all: List<String> = spending + income.filterNot { it in spending }

    /** @param direction a stored [com.ledger.domain.model.Direction] name. */
    fun forDirection(direction: String): List<String> =
        if (direction == "CREDIT") income else spending
}
