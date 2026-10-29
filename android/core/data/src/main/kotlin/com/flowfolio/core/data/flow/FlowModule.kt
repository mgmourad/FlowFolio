package com.flowfolio.core.data.flow

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Dynamic Flow's bindings (ADR-0045). */
@Module
@InstallIn(SingletonComponent::class)
abstract class FlowModule {
    @Binds
    abstract fun dynamicFlow(impl: DefaultDynamicFlow): DynamicFlow

    @Binds
    abstract fun readingViews(impl: DefaultReadingViews): ReadingViews
}
