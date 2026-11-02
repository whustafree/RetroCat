package com.swordfish.lemuroid.lib.library.db.dao

/**
 * Escapes free-form user input into a single FTS4 phrase.
 *
 * `fts_games MATCH ?` receives the query as a bound string, so the text is still parsed as
 * FTS query syntax. Characters such as `"`, `*`, `(`, `^`, `:` and bare `AND` / `OR` /
 * `NEAR` tokens either silently change the meaning of the search or abort it with a
 * `SQLiteException`, which used to break the search screens as soon as a user typed them.
 *
 * Wrapping the input in a quoted phrase and doubling any embedded quote makes it literal
 * text, which is what a search box is expected to do. Tokens inside the phrase are still
 * normalized by the `unicode61` tokenizer, so accented and case-insensitive matches keep
 * working.
 *
 * Returns `null` for blank input so callers can skip the `MATCH` altogether: FTS4 rejects an
 * empty operand, so an empty search must never reach the query.
 *
 * [prefixLastToken] turns the last token of the phrase into a prefix match. FTS4 can only
 * match whole tokens, so a catalog that used `LIKE '%text%'` would stop offering incremental
 * search otherwise: typing `ala` still finds `Alan Wake` with the prefix, while the index
 * stays usable instead of degrading into a full table scan.
 *
 * The `*` has to sit *inside* the closing quote. FTS4 only reads it as a prefix marker there;
 * written after the quote, as in `"ala"*`, the phrase is matched literally and every search
 * comes back empty.
 */
fun String.toFtsPhrase(prefixLastToken: Boolean = false): String? {
    val trimmed = trim()

    if (trimmed.isEmpty()) {
        return null
    }

    val escaped = trimmed.replace("\"", "\"\"")
    val prefix = if (prefixLastToken) "*" else ""

    return "\"$escaped$prefix\""
}
