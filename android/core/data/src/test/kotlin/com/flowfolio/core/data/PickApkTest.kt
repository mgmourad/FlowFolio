package com.flowfolio.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The updater installs the APK built for the phone's processor, and the universal one when there is none (TM1). */
class PickApkTest {
    private val split = listOf(
        "flowfolio-0.7.0-beta.4-700004.apk",
        "flowfolio-0.7.0-beta.4-700004.apk.sha256",
        "flowfolio-0.7.0-beta.4-700004-armeabi-v7a.apk",
        "flowfolio-0.7.0-beta.4-700004-arm64-v8a.apk",
        "flowfolio-0.7.0-beta.4-700004-x86_64.apk"
    )

    @Test
    fun thePhonesOwnProcessorComesFirst() {
        assertEquals(
            "flowfolio-0.7.0-beta.4-700004-arm64-v8a.apk",
            Updates.pickApk(split, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))
        )
        assertEquals(
            "flowfolio-0.7.0-beta.4-700004-armeabi-v7a.apk",
            Updates.pickApk(split, listOf("armeabi-v7a", "armeabi"))
        )
    }

    @Test
    fun theUniversalApkWhenNoneFits() {
        assertEquals("flowfolio-0.7.0-beta.4-700004.apk", Updates.pickApk(split, listOf("riscv64")))
        // A release from before TM2 has only the universal APK.
        assertEquals(
            "flowfolio-0.7.0-beta.2-700002.apk",
            Updates.pickApk(listOf("flowfolio-0.7.0-beta.2-700002.apk"), listOf("arm64-v8a"))
        )
        assertNull(Updates.pickApk(listOf("notes.md"), listOf("arm64-v8a")))
    }

    @Test
    fun theBuildNumberIsReadFromEveryKindOfName() {
        for (name in split.filter { it.endsWith(".apk") }) {
            assertEquals(name, "700004", Updates.APK_NAME.find(name)?.groupValues?.get(1))
        }
    }

    private val editions = listOf(
        "flowfolio-0.7.0-beta.5-700005.apk",
        "flowfolio-0.7.0-beta.5-700005-arm64-v8a.apk",
        "flowfolio-0.7.0-beta.5-700005-armeabi-v7a.apk",
        "flowfolio-0.7.0-beta.5-700005.full.apk",
        "flowfolio-0.7.0-beta.5-700005-arm64-v8a.full.apk",
        "flowfolio-0.7.0-beta.5-700005-armeabi-v7a.full.apk"
    )

    @Test
    fun eachEditionUpdatesToItself() {
        val arm64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
        assertEquals("flowfolio-0.7.0-beta.5-700005-arm64-v8a.apk", Updates.pickApk(editions, arm64))
        assertEquals("flowfolio-0.7.0-beta.5-700005-arm64-v8a.full.apk", Updates.pickApk(editions, arm64, full = true))
        assertEquals("flowfolio-0.7.0-beta.5-700005.full.apk", Updates.pickApk(editions, listOf("x86_64"), full = true))
        assertEquals("flowfolio-0.7.0-beta.5-700005.apk", Updates.pickApk(editions, listOf("x86_64")))
        // The build number is read from either edition's name.
        assertEquals("700005", Updates.APK_NAME.find(editions[4])?.groupValues?.get(1))
    }

    @Test
    fun theFullEditionFallsBackToEssentialWhenARelaseHasNoFullApk() {
        assertEquals(
            "flowfolio-0.7.0-beta.4-700004-arm64-v8a.apk",
            Updates.pickApk(split, listOf("arm64-v8a"), full = true)
        )
    }
}
