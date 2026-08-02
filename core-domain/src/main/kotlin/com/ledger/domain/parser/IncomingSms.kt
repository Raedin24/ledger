package com.ledger.domain.parser

import java.time.Instant

/**
 * A transient inbound SMS handed to the parser.
 *
 * IMPORTANT: this object is short-lived. The `body` exists only for the
 * duration of parsing and must never be persisted, logged, or copied into any
 * stored structure. Callers should drop their reference immediately after
 * SmsParser returns.
 */
data class IncomingSms(
    val senderId: String,
    val body: String,
    val receivedAt: Instant,
)
