package com.ledger.domain.parser

import java.time.Instant

/**
 * An incoming SMS, handed to the parser.
 *
 * Important: `body` exists only while the message is parsed. It must never be
 * saved, logged, or copied into anything that is stored. Callers should drop
 * their reference as soon as SmsParser returns.
 */
data class IncomingSms(
    val senderId: String,
    val body: String,
    val receivedAt: Instant,
)
