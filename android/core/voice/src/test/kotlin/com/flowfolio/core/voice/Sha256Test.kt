package com.flowfolio.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256Test {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `computes the SHA-256 of a file`() {
        val f = tmp.newFile("a.txt")
        f.writeText("hello")
        // SHA-256 of "hello", well-known.
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", Sha256.of(f))
    }

    @Test
    fun `digest differs for different content`() {
        val a = tmp.newFile("a.txt")
        val b = tmp.newFile("b.txt")
        a.writeText("hello")
        b.writeText("world")
        assertNotEquals(Sha256.of(a), Sha256.of(b))
    }
}
