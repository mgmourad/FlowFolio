package com.flowfolio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every bundled recording is reachable as an atmosphere (owner, 2026-11-03).
 *
 * The first five presets used five of the eight `ambient_*.mp3` files, leaving Ocean, Stream and Cafe playable
 * only by building a custom atmosphere. From the outside that looks like the sounds were never added to the app,
 * which is how it was reported. A recording nothing offers may as well not ship.
 */
class AtmospherePresetsTest {
    @Test
    fun `every ambience but silence has a preset that plays it`() {
        val offered = AtmospherePresets.all.map { it.ambient }.toSet()
        val missing = Ambient.entries.filter { it != Ambient.SILENCE && it !in offered }
        assertEquals("ambiences no preset plays", emptyList<Ambient>(), missing)
    }

    @Test
    fun `presets do not share a name, since a name is how one is found again`() {
        val names = AtmospherePresets.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every preset is marked built in, so none can be edited away`() {
        assertTrue(AtmospherePresets.all.all { it.builtIn })
    }

    @Test
    fun `no preset is silent, since silence is what not choosing one already does`() {
        assertTrue(AtmospherePresets.all.none { it.silent })
    }
}
