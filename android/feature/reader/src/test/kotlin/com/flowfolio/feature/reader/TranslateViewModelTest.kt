package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.TranslationRepository
import com.flowfolio.core.data.Translator
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PaperStyle
import com.flowfolio.core.model.ReadingPlace
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.Translation
import com.flowfolio.core.model.UserSettings
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeTranslator(var detected: String?) : Translator {
    override val languages = listOf("en", "es", "ar")

    override suspend fun detect(text: String) = detected

    override suspend fun isDownloaded(language: String) = true

    override suspend fun translate(text: String, from: String, to: String) = "[$from>$to] $text"
}

private class FakeTranslations(override val translator: Translator) : TranslationRepository {
    val saved = MutableStateFlow<List<Translation>>(emptyList())

    override fun history(limit: Int): Flow<List<Translation>> = saved

    override suspend fun translate(
        text: String,
        from: String,
        to: String,
        bookId: String?,
        at: ReadingPlace
    ): Translation {
        val t = Translation(
            saved.value.size + 1L,
            text,
            translator.translate(text, from, to),
            from,
            to,
            bookId,
            0,
            at.page,
            at.locator
        )
        saved.update { listOf(t) + it }
        return t
    }

    override suspend fun clearHistory() = saved.update { emptyList() }
}

private class Settings(initial: UserSettings) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state
    override suspend fun setStorageMode(mode: StorageMode) = Unit
    override suspend fun setAskStorageEachImport(ask: Boolean) = Unit
    override suspend fun setTheme(theme: ThemePreference) = Unit
    override suspend fun setStorageNoticeSeen() = Unit
    override suspend fun setLibrarySort(sort: LibrarySort) = Unit
    override suspend fun setReaderSpreads(on: Boolean) = Unit
    override suspend fun setSpreadCoverAlone(on: Boolean) = Unit
    override suspend fun setPaperStyle(style: PaperStyle) = Unit
    override suspend fun setPageTurn(turn: PageTurn) = Unit
    override suspend fun setNeonGlow(on: Boolean) = Unit
    override suspend fun setAutoUpdate(on: Boolean) = Unit

    override suspend fun setOnlineLookup(on: Boolean) = Unit
    override suspend fun setTranslateTo(language: String) = state.update { it.copy(translateTo = language) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranslateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val saved = Locale.getDefault()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(saved)
    }

    private fun vm(detected: String?, settings: UserSettings = UserSettings()) = Settings(settings).let { s ->
        TranslateViewModel(SavedStateHandle(mapOf("bookId" to "b")), FakeTranslations(FakeTranslator(detected)), s) to s
    }

    @Test
    fun aForeignPassageIsTranslatedIntoThePhonesLanguageAndRemembered() = runTest(dispatcher) {
        val (vm, _) = vm(detected = "es")
        vm.start("hola")
        val s = vm.state.value
        assertEquals("es", s.from)
        assertEquals("en", s.to)
        assertEquals("[es>en] hola", s.result)
        assertEquals(TranslateStatus.DONE, s.status)
    }

    @Test
    fun aPassageAlreadyInTheTargetAsksForALanguage() = runTest(dispatcher) {
        val (vm, settings) = vm(detected = "en")
        vm.start("hello")
        assertEquals(TranslateStatus.CHOOSE, vm.state.value.status)
        vm.setTo("ar")
        assertEquals("[en>ar] hello", vm.state.value.result)
        assertEquals("ar", settings.state.value.translateTo)
    }

    @Test
    fun theLastChosenLanguageIsUsedNextTime() = runTest(dispatcher) {
        val (vm, _) = vm(detected = "en", settings = UserSettings(translateTo = "es"))
        vm.start("hello")
        assertEquals("[en>es] hello", vm.state.value.result)
    }
}
