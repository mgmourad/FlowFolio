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
}
