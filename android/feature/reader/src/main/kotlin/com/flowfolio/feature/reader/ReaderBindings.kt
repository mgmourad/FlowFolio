package com.flowfolio.feature.reader

import com.flowfolio.core.data.flow.AnnotationCarrier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What the reader provides to the rest of the app: carrying annotations between a book's views (ADR-0045). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderBindings {
    @Binds
    abstract fun carrier(impl: FlowCarrier): AnnotationCarrier
}
