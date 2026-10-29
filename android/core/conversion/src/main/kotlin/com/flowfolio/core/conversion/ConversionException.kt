package com.flowfolio.core.conversion

/** A PDF the converter refuses, with the [reason] the app shows the reader. */
class ConversionException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason {
        /** The PDF opens but has no pages. */
        NO_PAGES
    }
}
