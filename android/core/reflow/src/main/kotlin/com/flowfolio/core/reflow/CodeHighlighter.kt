package com.flowfolio.core.reflow

/** The kinds of code token that get a colour (Phase 4 M4). */
enum class CodeTokenType { KEYWORD, STRING, COMMENT, NUMBER }

/** A coloured run of code: [start]..[end] in the source, of [type]. */
data class CodeToken(val type: CodeTokenType, val start: Int, val end: Int)

/** A language's keywords and comment markers, for detection and highlighting. */
enum class CodeLanguage(
    val keywords: Set<String>,
    val lineComments: Set<String>,
    val blockComment: Pair<String, String>? = null
) {
    PYTHON(
        setOf(
            "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "is",
            "none", "true", "false", "import", "from", "as", "with", "try", "except", "finally", "raise", "pass",
            "break", "continue", "lambda", "yield", "global", "nonlocal", "assert", "del", "self", "print", "len",
            "range", "str", "int", "float", "list", "dict", "set", "tuple", "super"
        ),
        setOf("#")
    ),
    JAVASCRIPT(
        setOf(
            "function", "return", "if", "else", "for", "while", "var", "let", "const", "class", "extends", "new",
            "this", "import", "export", "from", "async", "await", "try", "catch", "finally", "throw", "typeof",
            "instanceof", "of", "null", "undefined", "true", "false", "console", "break", "continue", "switch",
            "case", "default", "do", "delete"
        ),
        setOf("//"),
        "/*" to "*/"
    ),
    JAVA(
        setOf(
            "public", "private", "protected", "class", "interface", "extends", "implements", "static", "final",
            "void", "int", "long", "double", "float", "boolean", "char", "byte", "short", "return", "if", "else",
            "for", "while", "new", "try", "catch", "finally", "throw", "throws", "package", "import", "this",
            "super", "null", "true", "false", "system", "break", "continue", "switch", "case", "default", "do"
        ),
        setOf("//"),
        "/*" to "*/"
    ),
    KOTLIN(
        setOf(
            "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "for", "while", "when",
            "import", "package", "this", "super", "null", "true", "false", "println", "print", "override",
            "private", "public", "internal", "protected", "data", "sealed", "enum", "companion", "suspend", "by",
            "is", "in", "as", "break", "continue"
        ),
        setOf("//"),
        "/*" to "*/"
    ),
    SHELL(
        setOf(
            "if", "then", "else", "fi", "for", "while", "do", "done", "echo", "exit", "return", "function",
            "case", "esac", "local", "export", "cd", "ls", "mkdir", "rm", "cp", "mv", "grep", "sed", "awk",
            "sudo", "apt", "git", "npm", "docker", "cat", "chmod", "set", "unset", "source", "read", "pwd", "env"
        ),
        setOf("#")
    ),
    SQL(
        setOf(
            "select", "from", "where", "insert", "update", "delete", "create", "table", "drop", "alter", "join",
            "left", "right", "inner", "outer", "on", "group", "by", "order", "having", "limit", "values", "set",
            "into", "and", "or", "not", "null", "count", "sum", "avg", "min", "max", "as", "distinct", "primary",
            "key", "foreign", "references", "index"
        ),
        setOf("--"),
        "/*" to "*/"
    ),
    C(
        setOf(
            "int", "char", "float", "double", "void", "return", "if", "else", "for", "while", "struct", "class",
            "public", "private", "const", "static", "include", "define", "main", "printf", "scanf", "namespace",
            "using", "std", "new", "delete", "nullptr", "true", "false", "unsigned", "long", "short", "break",
            "continue", "switch", "case", "default", "do", "sizeof", "typedef", "enum"
        ),
        setOf("//"),
        "/*" to "*/"
    )
}

/**
 * Syntax highlighting for code in a Dynamic Flow book (Phase 4 M4), pure Kotlin. [detect] names the language from the
 * text alone; [tokenize] marks the runs to colour. The reader applies the theme's colours.
 */
object CodeHighlighter {
    /**
     * The language the code is most likely in, or null when nothing stands out. Keywords score a point each; a line
     * comment marker scores three, since a `#` or `//` names the language at a glance.
     */
    fun detect(text: String): CodeLanguage? {
        val words = text.split(Regex("[^A-Za-z0-9_]+"))
        var best: CodeLanguage? = null
        var bestScore = 0
        for (language in CodeLanguage.entries) {
            var score = 0
            for (word in words) if (word.lowercase() in language.keywords) score++
            for (marker in language.lineComments) if (text.contains(marker)) score += COMMENT_SCORE
            if (score > bestScore) {
                bestScore = score
                best = language
            }
        }
        return best?.takeIf { bestScore >= MIN_SCORE }
    }

    /** The coloured runs of [text] in [language]: keywords, strings, comments and numbers, in order. */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun tokenize(text: String, language: CodeLanguage): List<CodeToken> {
        val tokens = ArrayList<CodeToken>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val block = language.blockComment
            when {
                block != null && text.startsWith(block.first, i) -> {
                    val close = text.indexOf(block.second, i + block.first.length)
                    val end = if (close < 0) text.length else close + block.second.length
                    tokens += CodeToken(CodeTokenType.COMMENT, i, end)
                    i = end
                }

                language.lineComments.any { text.startsWith(it, i) } -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    tokens += CodeToken(CodeTokenType.COMMENT, i, end)
                    i = end
                }

                c == '"' || c == '\'' -> {
                    val end = stringEnd(text, i)
                    tokens += CodeToken(CodeTokenType.STRING, i, end)
                    i = end
                }

                c.isDigit() -> {
                    var end = i
                    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '.')) end++
                    tokens += CodeToken(CodeTokenType.NUMBER, i, end)
                    i = end
                }

                c.isLetter() || c == '_' -> {
                    var end = i
                    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
                    if (text.substring(i, end).lowercase() in language.keywords) {
                        tokens += CodeToken(CodeTokenType.KEYWORD, i, end)
                    }
                    i = end
                }

                else -> i++
            }
        }
        return tokens
    }

    /** The end (exclusive) of the string starting at [start] with quote [quote], honouring backslash escapes. */
    private fun stringEnd(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            if (text[i] == '\\') {
                i += 2
                continue
            }
            if (text[i] == text[start]) return i + 1
            i++
        }
        return text.length
    }

    private const val COMMENT_SCORE = 3
    private const val MIN_SCORE = 2
}
