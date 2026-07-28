package com.luckerlucky.magiciperf

/**
 * Quote-aware command-line tokenizer, mirroring the iOS app's ArgumentTokenizer:
 * whitespace splits tokens, single/double quotes group them, and unbalanced
 * quotes reject the whole command (null) instead of guessing.
 */
object ArgumentTokenizer {

    fun tokenize(input: String): List<String>? {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        // Distinguishes "no token yet" from an explicitly empty quoted token
        // ("" is a valid, empty argument).
        var hasToken = false

        for (ch in input) {
            when {
                quote != null -> {
                    if (ch == quote) quote = null else current.append(ch)
                }
                ch == '"' || ch == '\'' -> {
                    quote = ch
                    hasToken = true
                }
                ch.isWhitespace() -> {
                    if (hasToken) {
                        tokens.add(current.toString())
                        current.clear()
                        hasToken = false
                    }
                }
                else -> {
                    current.append(ch)
                    hasToken = true
                }
            }
        }
        if (quote != null) return null  // unbalanced quote
        if (hasToken) tokens.add(current.toString())
        return tokens
    }
}
