package com.flowfolio.core.pdf

import java.io.IOException

/** Why a PDF could not be opened or read. Every engine failure maps to one of these; none crash the app. */
sealed class PdfException(message: String) : IOException(message) {
    /** Encrypted: ask the user for a password (or the one given was wrong). */
    class PasswordRequired : PdfException("this PDF is password-protected")

    /** Not a PDF, or too damaged to read. */
    class InvalidFormat : PdfException("not a PDF or too damaged to open")

    /** The file could not be read (missing, empty, permission revoked). */
    class FileError : PdfException("the file could not be read")

    /** Uses an encryption or security handler the engine does not support. */
    class UnsupportedSecurity : PdfException("unsupported PDF security handler")

    /** A specific page is damaged. */
    class PageError(val pageIndex: Int) : PdfException("page ${pageIndex + 1} could not be read")

    /** The document was already closed. */
    class Closed : PdfException("document is closed")

    class Unknown(code: Int) : PdfException("PDF engine error $code")

    companion object {
        // PDFium's FPDF_ERR_* values, plus our own code for an unusable file descriptor (pdfium_jni.cpp).
        private const val ERR_FILE = 2
        private const val ERR_FORMAT = 3
        private const val ERR_PASSWORD = 4
        private const val ERR_SECURITY = 5
        private const val ERR_BAD_FD = 100

        fun fromCode(code: Int): PdfException = when (code) {
            ERR_FILE, ERR_BAD_FD -> FileError()
            ERR_FORMAT -> InvalidFormat()
            ERR_PASSWORD -> PasswordRequired()
            ERR_SECURITY -> UnsupportedSecurity()
            else -> Unknown(code)
        }
    }
}
