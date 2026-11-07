package com.flowfolio.core.reflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {
    @Test
    fun `detects python`() {
        assertEquals(CodeLanguage.PYTHON, CodeHighlighter.detect("def hello(name):\n    print('hi', name)"))
    }

    @Test
    fun `detects javascript`() {
        assertEquals(CodeLanguage.JAVASCRIPT, CodeHighlighter.detect("function hello(name) {\n  console.log(name)\n}"))
    }

    @Test
    fun `detects shell by its comment and echo`() {
        assertEquals(CodeLanguage.SHELL, CodeHighlighter.detect("#!/bin/bash\necho hello"))
    }

    @Test
    fun `detects sql`() {
        assertEquals(CodeLanguage.SQL, CodeHighlighter.detect("SELECT name FROM users WHERE id = 1"))
    }

    @Test
    fun `returns null for non-code`() {
        assertNull(CodeHighlighter.detect("The quick brown fox jumps over the lazy dog."))
    }

    @Test
    fun `marks keywords, strings, comments and numbers`() {
        val code = "def f(x):  # double\n    return x * 2\n"
        val tokens = CodeHighlighter.tokenize(code, CodeLanguage.PYTHON)
        val of = { t: CodeToken -> code.substring(t.start, t.end) }
        assertTrue(tokens.any { it.type == CodeTokenType.KEYWORD && of(it) == "def" })
        assertTrue(tokens.any { it.type == CodeTokenType.KEYWORD && of(it) == "return" })
        assertTrue(tokens.any { it.type == CodeTokenType.COMMENT && of(it).contains("double") })
        assertTrue(tokens.any { it.type == CodeTokenType.NUMBER && of(it) == "2" })
    }

    @Test
    fun `a string with an escaped quote is one token`() {
        val code = "\"say \\\"hi\\\"\""
        val tokens = CodeHighlighter.tokenize(code, CodeLanguage.PYTHON)
        assertEquals(1, tokens.size)
        assertEquals(CodeTokenType.STRING, tokens[0].type)
        assertEquals(code, code.substring(tokens[0].start, tokens[0].end))
    }

    @Test
    fun `a block comment spans its whole body`() {
        val code = "/* a\nblock */ x"
        val tokens = CodeHighlighter.tokenize(code, CodeLanguage.JAVA)
        val comment = tokens.first { it.type == CodeTokenType.COMMENT }
        assertEquals("/* a\nblock */", code.substring(comment.start, comment.end))
    }
}
