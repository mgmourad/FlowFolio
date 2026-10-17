package com.flowfolio.core.data

import android.content.Context
import com.flowfolio.core.pdf.PdfEngine
import com.flowfolio.core.pdf.pdfium.PdfiumEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

fun interface Clock {
    fun now(): Long
}

class SystemClock @Inject constructor() : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds
    abstract fun library(impl: DefaultLibraryRepository): LibraryRepository

    @Binds
    abstract fun settings(impl: DefaultSettingsRepository): SettingsRepository

    @Binds
    abstract fun documents(impl: AndroidDocumentSource): DocumentSource

    @Binds
    abstract fun inspector(impl: PdfiumInspector): PdfInspector

    @Binds
    abstract fun clock(impl: SystemClock): Clock
}

@Module
@InstallIn(SingletonComponent::class)
object DataProviders {
    @Provides
    @Singleton
    fun pdfEngine(): PdfEngine = PdfiumEngine()

    @Provides
    @Singleton
    fun bookFiles(@ApplicationContext context: Context): BookFiles = BookFiles(context.filesDir).also { it.clearTemp() }

    @Provides
    @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO
}
