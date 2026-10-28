package com.ledger.app.data.db

/**
 * Which side of the ledger a category belongs to. Money in and money out need
 * different labels ("Rent" is never income, "Salary" is never spending).
 * [SHARED] is for the few that work both ways.
 */
enum class CategoryKind(val label: String) {
    OUT("Money out"),
    IN("Money in"),
    SHARED("Both"),
    ;

    companion object {
        /** An unknown value becomes money out, so the category still shows somewhere. */
        fun from(raw: String?): CategoryKind = entries.firstOrNull { it.name == raw } ?: OUT

        /**
         * The order the sides are shown in: out, both, in.
         *
         * [SHARED] sits between the other two. The editor groups sections this way,
         * and stored positions decide what the pickers show, so a shared category
         * stays next to the spending ones instead of falling below the income ones.
         */
        val DISPLAY_ORDER: List<CategoryKind> = listOf(OUT, SHARED, IN)
    }
}

/** A seeded category: its label and the side of the ledger it serves. */
data class DefaultCategory(val name: String, val kind: CategoryKind)

/**
 * The starting categories in display order: spending, the one shared label,
 * then income. Within each side they are ordered by how often they are likely
 * to be used, not alphabetically.
 */
object DefaultCategories {

    /** Where own-account transfers are filed. Must stay in [ALL] so it has an icon and colour. */
    const val TRANSFERS = "Transfers"

    /** The spending catch-all. [INCOME_CATCH_ALL] is the income one. */
    const val SPEND_CATCH_ALL = "Misc"
    const val INCOME_CATCH_ALL = "Other"

    /**
     * The row that groups provider fees in the breakdowns.
     *
     * It is not in [ALL], so it is never seeded, never offered by a picker, and
     * never shown in the category editor. It is only ever computed from the fee
     * column.
     *
     * The same text appears in `CategoryDao`'s breakdown queries. Room needs a
     * constant in `@Query`, so keep the two in step by hand.
     */
    const val TRANSACTION_FEES = "Transaction Fees"

    private fun spend(vararg names: String) = names.map { DefaultCategory(it, CategoryKind.OUT) }
    private fun income(vararg names: String) = names.map { DefaultCategory(it, CategoryKind.IN) }

    val ALL: List<DefaultCategory> =
        spend(
            "Food & Drink", "Dining & Outings", "Groceries", "Household", "Transport",
            "Fuel", "Car", "Bike", "Rent", "Bills & Utilities", "Data & Airtime",
            "Health", "Personal Care", "Clothing", "Family", "Kids", "Pet Care",
            "Gifts & Donations", "Shopping", "Fitness", "Entertainment",
            "Debt Repayment", "Taxes", "Savings", "Investment", SPEND_CATCH_ALL,
        ) +
            listOf(DefaultCategory(TRANSFERS, CategoryKind.SHARED)) +
            income(
                "Salary", "Bonus", "Freelance", "Investment Return", "Interest Income",
                "Reimbursement", "Gift Received", "Family Support", INCOME_CATCH_ALL,
            )

    /**
     * Which version of the default list a ledger has been given. Raise it when
     * adding defaults, and list the new names under the new number in [ADDED].
     */
    const val VERSION = 2

    /**
     * Defaults added after the first release, by the [VERSION] that added them.
     * Existing ledgers get these once, at the end of their list. Only new names
     * are added, so a default the user deleted earlier doesn't come back.
     */
    val ADDED: Map<Int, List<String>> = mapOf(
        2 to listOf("Dining & Outings", "Household", "Clothing", "Kids", "Pet Care", "Taxes"),
    )

    /** Offered when sorting a debit: spending plus shared. */
    val OUT_NAMES: List<String> = ALL.filter { it.kind != CategoryKind.IN }.map { it.name }

    /** Offered when sorting a credit: income plus shared. */
    val IN_NAMES: List<String> = ALL.filter { it.kind != CategoryKind.OUT }.map { it.name }

    /** Rows for a first-run seed, positioned in declaration order. */
    fun entities(): List<CategoryEntity> = ALL.mapIndexed { i, c ->
        CategoryEntity(name = c.name, position = i, kind = c.kind.name)
    }

    /**
     * The categories ticked by default on the setup screen. They are the ones
     * almost everyone needs: everyday spending, salary, and both catch-alls. The
     * rest are still available behind "More" in every picker.
     */
    val SUGGESTED: Set<String> = setOf(
        "Food & Drink", "Groceries", "Transport", "Rent", "Bills & Utilities",
        "Data & Airtime", "Family", "Shopping", SPEND_CATCH_ALL,
        TRANSFERS,
        "Salary", INCOME_CATCH_ALL,
    )
}

/**
 * The categories a picker should offer, already split by direction. Built once
 * when the table changes, so screens don't re-split the list on every redraw.
 */
data class CategoryOptions(
    val spending: List<String> = DefaultCategories.OUT_NAMES,
    val income: List<String> = DefaultCategories.IN_NAMES,
    /** The user's top spending categories, in order. Pickers show these first. */
    val quickSpending: List<String> = emptyList(),
    /** The user's top income labels, in order. */
    val quickIncome: List<String> = emptyList(),
) {
    /**
     * Every category, for pickers that cover both directions (History filters,
     * the rule editor, bulk re-categorise).
     */
    val all: List<String> = spending + income.filterNot { it in spending }

    /** @param direction a stored [com.ledger.domain.model.Direction] name. */
    fun forDirection(direction: String): List<String> =
        if (direction == "CREDIT") income else spending

    /**
     * The categories shown without opening the picker sheet.
     *
     * Uses the user's favourites, or the top of the list if they haven't picked
     * any. Either way it is capped at [INLINE_LIMIT], because each queued Review
     * card shows these chips and too many make the screen slow.
     */
    fun quickForDirection(direction: String): List<String> {
        val quick = if (direction == "CREDIT") quickIncome else quickSpending
        return quick.ifEmpty { forDirection(direction) }.take(INLINE_LIMIT)
    }

    companion object {
        /** How many labels a picker offers inline before deferring to "More". */
        const val INLINE_LIMIT = 8
    }
}
