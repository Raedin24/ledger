package com.ledger.app.data.db

/**
 * Values of [TransactionEntity.cashMove]: how a wallet transaction relates to
 * the cash ledger.
 *
 * A cash-out at an agent is a debit on the wallet, but the money hasn't been
 * spent yet. It is in the user's pocket and gets spent later as cash. Counting
 * both the withdrawal and the cash purchases would count the same money twice.
 *
 * Moving it to cash turns the withdrawal into a transfer between two of the
 * user's own accounts. The wallet row stops counting (except its fee, as with
 * any self-transfer), and a matching row is added to the cash ledger. A
 * cash-in works the same way in reverse.
 */
object CashMove {
    /** The parser saw an agent withdrawal or deposit; the user hasn't answered yet. */
    const val OFFER = "OFFER"

    /** Moved: this wallet row is a self-transfer with a matching [PAIR] row in cash. */
    const val MOVED = "MOVED"

    /** The user said no, or undid a move. Never asked again. */
    const val KEPT = "KEPT"

    /** The cash-side row matching a [MOVED] wallet row. */
    const val PAIR = "PAIR"

    /**
     * A correction to cash on hand: cash the user already had when they started,
     * or a recount that found more or less than the ledger shows. It changes the
     * balance but isn't income or spending, so it is filed as a self-transfer and
     * left out of every total.
     */
    const val OPENING = "OPENING"

    /** Filing a row without moving it answers an open question with "no". */
    fun String?.answeredNo(): String? = if (this == OFFER) KEPT else this

    /**
     * Dedup key of the cash row matching the wallet row keyed [sourceKey]. It is
     * derived from the wallet row's key, so the pair can be found, and can't be
     * created twice, without a link column.
     */
    fun pairKey(sourceKey: String): String = "CASH|$sourceKey"

    /** The wallet row a [PAIR] row matches. */
    fun sourceKey(pairKey: String): String = pairKey.removePrefix("CASH|")

    /** Dedup key for a hand-entered cash row, which has nothing naturally unique about it. */
    fun manualKey(): String = "M|${java.util.UUID.randomUUID()}"
}
