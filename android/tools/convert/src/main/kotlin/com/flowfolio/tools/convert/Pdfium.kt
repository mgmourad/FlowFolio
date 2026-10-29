package com.flowfolio.tools.convert

import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer

/**
 * PDFium's C API (`fpdfview.h`, `fpdf_text.h`, `fpdf_doc.h`, `fpdf_edit.h`) through JNA direct mapping: the functions
 * the converter reads on the desktop JVM, mirroring what `:core:pdf`'s JNI bridge reads on the phone. Rectangles and
 * out-parameters are passed as primitive arrays. PDFium is not thread-safe: call from one thread.
 */
@Suppress("FunctionName", "TooManyFunctions", "LongParameterList") // PDFium's own names and signatures
internal object Pdfium {
    init {
        Native.register("pdfium")
        FPDF_InitLibrary()
    }

    external fun FPDF_InitLibrary()

    external fun FPDF_LoadMemDocument64(data: Pointer, size: Long, password: String?): Pointer?

    external fun FPDF_CloseDocument(document: Pointer)

    external fun FPDF_GetLastError(): NativeLong

    external fun FPDF_GetPageCount(document: Pointer): Int

    /** [size] receives width, height in points, rotation applied. */
    external fun FPDF_GetPageSizeByIndexF(document: Pointer, index: Int, size: FloatArray): Int

    external fun FPDF_LoadPage(document: Pointer, index: Int): Pointer?

    external fun FPDF_ClosePage(page: Pointer)

    external fun FPDF_PageToDevice(
        page: Pointer,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        pageX: Double,
        pageY: Double,
        deviceX: IntArray,
        deviceY: IntArray
    ): Int

    external fun FPDF_GetMetaText(document: Pointer, tag: String, buffer: ByteArray?, length: NativeLong): NativeLong

    external fun FPDF_GetPageLabel(document: Pointer, index: Int, buffer: ByteArray?, length: NativeLong): NativeLong

    external fun FPDFBookmark_GetFirstChild(document: Pointer, bookmark: Pointer?): Pointer?

    external fun FPDFBookmark_GetNextSibling(document: Pointer, bookmark: Pointer): Pointer?

    external fun FPDFBookmark_GetTitle(bookmark: Pointer, buffer: ByteArray?, length: NativeLong): NativeLong

    external fun FPDFBookmark_GetDest(document: Pointer, bookmark: Pointer): Pointer?

    external fun FPDFBookmark_GetAction(bookmark: Pointer): Pointer?

    external fun FPDFAction_GetType(action: Pointer): NativeLong

    external fun FPDFAction_GetDest(document: Pointer, action: Pointer): Pointer?

    external fun FPDFDest_GetDestPageIndex(document: Pointer, dest: Pointer): Int

    external fun FPDFText_LoadPage(page: Pointer): Pointer?

    external fun FPDFText_ClosePage(textPage: Pointer)

    external fun FPDFText_CountChars(textPage: Pointer): Int

    external fun FPDFText_GetUnicode(textPage: Pointer, index: Int): Int

    external fun FPDFText_IsGenerated(textPage: Pointer, index: Int): Int

    /** [rect] receives left, top, right, bottom in page space. */
    external fun FPDFText_GetLooseCharBox(textPage: Pointer, index: Int, rect: FloatArray): Int

    external fun FPDFText_GetFontSize(textPage: Pointer, index: Int): Double

    external fun FPDFText_GetFontWeight(textPage: Pointer, index: Int): Int

    external fun FPDFText_GetFontInfo(
        textPage: Pointer,
        index: Int,
        buffer: ByteArray?,
        length: NativeLong,
        flags: IntArray
    ): NativeLong

    external fun FPDFText_GetFillColor(
        textPage: Pointer,
        index: Int,
        r: IntArray,
        g: IntArray,
        b: IntArray,
        a: IntArray
    ): Int

    external fun FPDFPage_CountObjects(page: Pointer): Int

    external fun FPDFPage_GetObject(page: Pointer, index: Int): Pointer?

    external fun FPDFPageObj_GetType(obj: Pointer): Int

    /** [left], [bottom], [right], [top] each receive one float, in page space. */
    external fun FPDFPageObj_GetBounds(
        obj: Pointer,
        left: FloatArray,
        bottom: FloatArray,
        right: FloatArray,
        top: FloatArray
    ): Int

    external fun FPDFFormObj_CountObjects(obj: Pointer): Int

    external fun FPDFFormObj_GetObject(obj: Pointer, index: NativeLong): Pointer?

    external fun FPDFPath_GetDrawMode(path: Pointer, fillMode: IntArray, stroke: IntArray): Int

    external fun FPDFBitmap_Create(width: Int, height: Int, alpha: Int): Pointer?

    external fun FPDFBitmap_FillRect(
        bitmap: Pointer,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: NativeLong
    ): Int

    external fun FPDF_RenderPageBitmap(
        bitmap: Pointer,
        page: Pointer,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    )

    external fun FPDFBitmap_GetBuffer(bitmap: Pointer): Pointer

    external fun FPDFBitmap_GetStride(bitmap: Pointer): Int

    external fun FPDFBitmap_Destroy(bitmap: Pointer)

    const val PAGEOBJ_PATH = 2
    const val PAGEOBJ_IMAGE = 3
    const val PAGEOBJ_FORM = 5
    const val ACTION_GOTO = 1L
    const val RENDER_ANNOT = 0x01
    const val RENDER_LCD_TEXT = 0x02
}
