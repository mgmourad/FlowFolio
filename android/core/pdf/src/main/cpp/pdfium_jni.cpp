// JNI bridge to PDFium (ADR-0010). Kotlin calls these from one dedicated thread; the mutex is a second line of
// defence because PDFium is not thread-safe. Documents are read through a dup'ed file descriptor so files from the
// Storage Access Framework (content:// URIs) work the same as app-private files.

#include <android/bitmap.h>
#include <android/log.h>
#include <errno.h>
#include <jni.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include "fpdf_doc.h"
#include "fpdf_edit.h"
#include "fpdf_text.h"
#include "fpdfview.h"

#define LOG_TAG "FlowFolioPdfium"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

std::once_flag g_init;
std::mutex g_lock;

void ensureInit() {
    std::call_once(g_init, [] {
        FPDF_LIBRARY_CONFIG cfg{};
        cfg.version = 2;
        FPDF_InitLibraryWithConfig(&cfg);
    });
}

// Error codes returned to Kotlin; 1-6 mirror FPDF_ERR_*, the rest are ours.
constexpr int kErrBadFd = 100;

struct NativeDoc {
    FPDF_DOCUMENT doc = nullptr;
    int fd = -1;
    FPDF_FILEACCESS access{};
    // Kept only so the document can be reloaded to drop PDFium's caches (nativeReload); never logged or persisted.
    std::string password;
    bool hasPassword = false;
};

int readBlock(void* param, unsigned long position, unsigned char* buf, unsigned long size) {
    int fd = *static_cast<int*>(param);
    unsigned long done = 0;
    while (done < size) {
        ssize_t n = pread(fd, buf + done, size - done, static_cast<off_t>(position + done));
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return 0;  // read error or unexpected end of file: PDFium treats the file as damaged
        done += static_cast<unsigned long>(n);
    }
    return 1;
}

NativeDoc* asDoc(jlong handle) { return reinterpret_cast<NativeDoc*>(handle); }

jstring utf16(JNIEnv* env, const std::vector<unsigned short>& buf, unsigned long byteCount) {
    jsize len = byteCount >= 2 ? static_cast<jsize>(byteCount / 2 - 1) : 0;  // byteCount includes the NUL
    return env->NewString(reinterpret_cast<const jchar*>(buf.data()), len);
}

struct OutlineItem {
    int depth;
    int page;
    std::vector<unsigned short> title;
    unsigned long titleBytes;
};

void walkOutline(FPDF_DOCUMENT doc, FPDF_BOOKMARK parent, int depth, std::vector<OutlineItem>& out, int& budget) {
    FPDF_BOOKMARK bm = FPDFBookmark_GetFirstChild(doc, parent);
    while (bm != nullptr && budget-- > 0) {
        OutlineItem item{depth, -1, {}, 0};
        unsigned long n = FPDFBookmark_GetTitle(bm, nullptr, 0);
        item.title.resize(n / 2 + 1);
        item.titleBytes = n > 0 ? FPDFBookmark_GetTitle(bm, item.title.data(), n) : 0;
        FPDF_DEST dest = FPDFBookmark_GetDest(doc, bm);
        if (dest == nullptr) {
            FPDF_ACTION action = FPDFBookmark_GetAction(bm);
            if (action != nullptr && FPDFAction_GetType(action) == PDFACTION_GOTO) dest = FPDFAction_GetDest(doc, action);
        }
        if (dest != nullptr) item.page = FPDFDest_GetDestPageIndex(doc, dest);
        out.push_back(std::move(item));
        if (depth < 32) walkOutline(doc, bm, depth + 1, out, budget);  // malformed files can nest or loop
        bm = FPDFBookmark_GetNextSibling(doc, bm);
    }
}

// --- Paper styles (reader option: Paper, Sepia, Night, Black; ADR-0014) --------------------------------------------
// Text and line art are mapped from white..black onto paper..ink; dark papers first invert lightness with hue kept
// (a blue link stays blue). Photos (image objects) are restored exactly as printed.

struct Rect {
    int x0, y0, x1, y1;
};

bool containsImage(FPDF_PAGEOBJECT obj, int depth) {
    int type = FPDFPageObj_GetType(obj);
    if (type == FPDF_PAGEOBJ_IMAGE) return true;
    if (type != FPDF_PAGEOBJ_FORM || depth > 4) return false;
    int n = FPDFFormObj_CountObjects(obj);
    for (int i = 0; i < n && i < 4096; i++) {
        FPDF_PAGEOBJECT child = FPDFFormObj_GetObject(obj, static_cast<unsigned long>(i));
        if (child != nullptr && containsImage(child, depth + 1)) return true;
    }
    return false;
}

// Device-space rectangles (clipped to the bitmap) of the page's images; a form that holds images counts as a whole.
std::vector<Rect> imageRects(FPDF_PAGE page, int offsetX, int offsetY, int pageW, int pageH, int w, int h) {
    std::vector<Rect> rects;
    int n = FPDFPage_CountObjects(page);
    for (int i = 0; i < n && rects.size() < 512; i++) {
        FPDF_PAGEOBJECT obj = FPDFPage_GetObject(page, i);
        if (obj == nullptr || !containsImage(obj, 0)) continue;
        float l, b, r, t;
        if (!FPDFPageObj_GetBounds(obj, &l, &b, &r, &t)) continue;
        int x1, y1, x2, y2;
        FPDF_PageToDevice(page, -offsetX, -offsetY, pageW, pageH, 0, l, t, &x1, &y1);
        FPDF_PageToDevice(page, -offsetX, -offsetY, pageW, pageH, 0, r, b, &x2, &y2);
        Rect rc{std::max(0, std::min(x1, x2)), std::max(0, std::min(y1, y2)), std::min(w, std::max(x1, x2) + 1),
                std::min(h, std::max(y1, y2) + 1)};
        if (rc.x0 < rc.x1 && rc.y0 < rc.y1) rects.push_back(rc);
    }
    return rects;
}

// Pixels are RGBA bytes (Android ARGB_8888 memory order).
void tintPage(uint8_t* pixels, int w, int h, int stride, const std::vector<Rect>& keep, bool invert, uint32_t paper,
              uint32_t ink) {
    // Save the image areas, transform everything, then put the images back untouched.
    std::vector<std::vector<uint8_t>> saved;
    saved.reserve(keep.size());
    for (const Rect& r : keep) {
        std::vector<uint8_t> block(static_cast<size_t>(r.x1 - r.x0) * 4 * (r.y1 - r.y0));
        for (int y = r.y0; y < r.y1; y++) {
            std::copy_n(pixels + y * stride + r.x0 * 4, (r.x1 - r.x0) * 4, block.data() + (y - r.y0) * (r.x1 - r.x0) * 4);
        }
        saved.push_back(std::move(block));
    }
    // Colours are ARGB ints from Kotlin. With invert, white maps to paper (a dark colour) and black to ink (light);
    // without, white maps to paper and black to ink directly. Paper colours are chosen to be exact in a 16-bit page,
    // so the page and the screen around it match.
    const int bg[3] = {static_cast<int>((paper >> 16) & 0xFF), static_cast<int>((paper >> 8) & 0xFF),
                       static_cast<int>(paper & 0xFF)};
    const int fg[3] = {static_cast<int>((ink >> 16) & 0xFF), static_cast<int>((ink >> 8) & 0xFF),
                       static_cast<int>(ink & 0xFF)};
    for (int y = 0; y < h; y++) {
        uint8_t* row = pixels + y * stride;
        for (int x = 0; x < w; x++) {
            uint8_t* px = row + x * 4;
            int r = px[0], g = px[1], b = px[2];
            if (invert) {
                int shift = 255 - std::max(r, std::max(g, b)) - std::min(r, std::min(g, b));  // invert lightness
                int v[3] = {r + shift, g + shift, b + shift};
                for (int c = 0; c < 3; c++) px[c] = static_cast<uint8_t>(bg[c] + v[c] * (fg[c] - bg[c]) / 255);
            } else {
                int v[3] = {r, g, b};
                for (int c = 0; c < 3; c++) px[c] = static_cast<uint8_t>(fg[c] + v[c] * (bg[c] - fg[c]) / 255);
            }
        }
    }
    for (size_t i = 0; i < keep.size(); i++) {
        const Rect& r = keep[i];
        for (int y = r.y0; y < r.y1; y++) {
            std::copy_n(saved[i].data() + (y - r.y0) * (r.x1 - r.x0) * 4, (r.x1 - r.x0) * 4, pixels + y * stride + r.x0 * 4);
        }
    }
}

}  // namespace

extern "C" {

// Returns a handle, or 0 with errOut[0] set to an error code.
JNIEXPORT jlong JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativeOpen(JNIEnv* env, jobject, jint fd, jstring jpassword,
                                                                                     jintArray errOut) {
    ensureInit();
    jint err = 0;
    auto* nd = new NativeDoc();
    nd->fd = dup(fd);
    struct stat st{};
    if (nd->fd < 0 || fstat(nd->fd, &st) != 0 || st.st_size <= 0) {
        err = kErrBadFd;
    } else {
        nd->access.m_FileLen = static_cast<unsigned long>(st.st_size);
        nd->access.m_GetBlock = readBlock;
        nd->access.m_Param = &nd->fd;
        const char* password = jpassword != nullptr ? env->GetStringUTFChars(jpassword, nullptr) : nullptr;
        if (password != nullptr) {
            nd->password = password;
            nd->hasPassword = true;
        }
        {
            std::lock_guard<std::mutex> guard(g_lock);
            nd->doc = FPDF_LoadCustomDocument(&nd->access, password);
            if (nd->doc == nullptr) err = static_cast<jint>(FPDF_GetLastError());
        }
        if (password != nullptr) env->ReleaseStringUTFChars(jpassword, password);
        if (nd->doc != nullptr && err == 0) return reinterpret_cast<jlong>(nd);
        if (err == 0) err = 1;
    }
    if (nd->fd >= 0) close(nd->fd);
    delete nd;
    env->SetIntArrayRegion(errOut, 0, 1, &err);
    return 0;
}

// PDFium keeps per-document caches (fonts, glyphs, parsed resources) that grow with every page loaded and are only
// released when the document closes: measured +12 MB per 100 textbook pages. Reloading swaps in a fresh document over
// the same file descriptor; the new one is opened before the old one is closed, so a failure leaves the old intact.
JNIEXPORT jboolean JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativeReload(JNIEnv*, jobject, jlong handle) {
    NativeDoc* nd = asDoc(handle);
    std::lock_guard<std::mutex> guard(g_lock);
    FPDF_DOCUMENT fresh = FPDF_LoadCustomDocument(&nd->access, nd->hasPassword ? nd->password.c_str() : nullptr);
    if (fresh == nullptr) return JNI_FALSE;
    FPDF_CloseDocument(nd->doc);
    nd->doc = fresh;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativeClose(JNIEnv*, jobject, jlong handle) {
    NativeDoc* nd = asDoc(handle);
    {
        std::lock_guard<std::mutex> guard(g_lock);
        FPDF_CloseDocument(nd->doc);
    }
    close(nd->fd);
    delete nd;
}

JNIEXPORT jint JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativePageCount(JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> guard(g_lock);
    return FPDF_GetPageCount(asDoc(handle)->doc);
}

// Returns {width, height} in points, or null when the page cannot be read.
JNIEXPORT jfloatArray JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativePageSize(JNIEnv* env, jobject, jlong handle, jint index) {
    FS_SIZEF size{0, 0};
    FPDF_BOOL ok;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        ok = FPDF_GetPageSizeByIndexF(asDoc(handle)->doc, index, &size);
    }
    if (!ok || size.width <= 0 || size.height <= 0) return nullptr;
    jfloat values[2] = {size.width, size.height};
    jfloatArray result = env->NewFloatArray(2);
    env->SetFloatArrayRegion(result, 0, 2, values);
    return result;
}

// Renders the page scaled to pageW x pageH pixels with its top-left corner at (-offsetX, -offsetY) in the target
// bitmap, so a full page uses offset 0 and a zoom tile uses the tile's position. The target is filled white first.
JNIEXPORT jboolean JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativeRender(JNIEnv* env, jobject, jlong handle, jint index,
                                                                                          jobject bitmap, jint pageW, jint pageH,
                                                                                          jint offsetX, jint offsetY, jint mode,
                                                                                          jint paper, jint ink) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    bool ok = false;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        FPDF_PAGE page = FPDF_LoadPage(asDoc(handle)->doc, index);
        if (page != nullptr) {
            int w = static_cast<int>(info.width), h = static_cast<int>(info.height);
            FPDF_BITMAP target = FPDFBitmap_CreateEx(w, h, FPDFBitmap_BGRA, pixels, static_cast<int>(info.stride));
            if (target != nullptr) {
                FPDFBitmap_FillRect(target, 0, 0, w, h, 0xFFFFFFFF);
                // REVERSE_BYTE_ORDER writes RGBA, which is Android's ARGB_8888 memory layout. ANNOT draws existing
                // highlights and notes stored in the PDF.
                FPDF_RenderPageBitmap(target, page, -offsetX, -offsetY, pageW, pageH, 0, FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER);
                FPDFBitmap_Destroy(target);
                if (mode != 0) {
                    tintPage(static_cast<uint8_t*>(pixels), w, h, static_cast<int>(info.stride),
                             imageRects(page, offsetX, offsetY, pageW, pageH, w, h), mode == 2,
                             static_cast<uint32_t>(paper), static_cast<uint32_t>(ink));
                }
                ok = true;
            }
            FPDF_ClosePage(page);
        } else {
            LOGW("FPDF_LoadPage(%d) failed: %lu", index, FPDF_GetLastError());
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Returns "depth|pageIndex|title" strings in document order (parsed by OutlineCodec on the Kotlin side).
JNIEXPORT jobjectArray JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativeOutline(JNIEnv* env, jobject, jlong handle) {
    std::vector<OutlineItem> items;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        int budget = 20000;
        walkOutline(asDoc(handle)->doc, nullptr, 0, items, budget);
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(items.size()), stringClass, nullptr);
    for (size_t i = 0; i < items.size(); i++) {
        std::string prefix = std::to_string(items[i].depth) + "|" + std::to_string(items[i].page) + "|";
        std::vector<jchar> line(prefix.begin(), prefix.end());
        jsize titleLen = items[i].titleBytes >= 2 ? static_cast<jsize>(items[i].titleBytes / 2 - 1) : 0;
        line.insert(line.end(), items[i].title.begin(), items[i].title.begin() + titleLen);
        jstring s = env->NewString(line.data(), static_cast<jsize>(line.size()));
        env->SetObjectArrayElement(result, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return result;
}

JNIEXPORT jstring JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativePageLabel(JNIEnv* env, jobject, jlong handle, jint index) {
    std::vector<unsigned short> buf;
    unsigned long bytes;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        unsigned long n = FPDF_GetPageLabel(asDoc(handle)->doc, index, nullptr, 0);
        if (n <= 2) return nullptr;  // absent, or only the terminator
        buf.resize(n / 2 + 1);
        bytes = FPDF_GetPageLabel(asDoc(handle)->doc, index, buf.data(), n);
    }
    return utf16(env, buf, bytes);
}

// Links on a page as "left|top|right|bottom|pageIndex|uri" strings (parsed by LinkCodec). The rectangle is a fraction
// (0..1) of the page as displayed, top-left origin, after the page's own rotation; pageIndex is -1 for web links and
// uri is empty for internal ones. Links with neither a destination nor a URI are skipped.
JNIEXPORT jobjectArray JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativeLinks(JNIEnv* env, jobject, jlong handle, jint index) {
    constexpr int kScale = 1000000;  // device space used to normalise coordinates
    constexpr int kMaxLinks = 2000;  // malformed files can carry absurd numbers of annotations
    std::vector<std::string> lines;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        FPDF_DOCUMENT doc = asDoc(handle)->doc;
        FPDF_PAGE page = FPDF_LoadPage(doc, index);
        if (page != nullptr) {
            int pos = 0;
            FPDF_LINK link = nullptr;
            while (static_cast<int>(lines.size()) < kMaxLinks && FPDFLink_Enumerate(page, &pos, &link)) {
                FS_RECTF r{};
                if (!FPDFLink_GetAnnotRect(link, &r)) continue;
                int target = -1;
                std::string uri;
                FPDF_DEST dest = FPDFLink_GetDest(doc, link);
                FPDF_ACTION action = FPDFLink_GetAction(link);
                if (dest == nullptr && action != nullptr && FPDFAction_GetType(action) == PDFACTION_GOTO) {
                    dest = FPDFAction_GetDest(doc, action);
                }
                if (dest != nullptr) {
                    target = FPDFDest_GetDestPageIndex(doc, dest);
                } else if (action != nullptr && FPDFAction_GetType(action) == PDFACTION_URI) {
                    unsigned long n = FPDFAction_GetURIPath(doc, action, nullptr, 0);
                    if (n > 1 && n < 8192) {
                        std::vector<char> buf(n);
                        FPDFAction_GetURIPath(doc, action, buf.data(), n);
                        for (unsigned long i = 0; i + 1 < n; i++) {
                            unsigned char ch = static_cast<unsigned char>(buf[i]);
                            uri.push_back(ch >= 0x20 && ch < 0x7F && ch != '|' ? static_cast<char>(ch) : '_');
                        }
                    }
                }
                if (target < 0 && uri.empty()) continue;
                int x1, y1, x2, y2;
                FPDF_PageToDevice(page, 0, 0, kScale, kScale, 0, r.left, r.top, &x1, &y1);
                FPDF_PageToDevice(page, 0, 0, kScale, kScale, 0, r.right, r.bottom, &x2, &y2);
                auto frac = [](int v) { return std::to_string(static_cast<double>(v) / kScale); };
                lines.push_back(frac(std::min(x1, x2)) + "|" + frac(std::min(y1, y2)) + "|" + frac(std::max(x1, x2)) + "|" +
                                frac(std::max(y1, y2)) + "|" + std::to_string(target) + "|" + uri);
            }
            FPDF_ClosePage(page);
        }
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(lines.size()), stringClass, nullptr);
    for (size_t i = 0; i < lines.size(); i++) {
        jstring s = env->NewStringUTF(lines[i].c_str());  // printable ASCII only, so valid modified UTF-8
        env->SetObjectArrayElement(result, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return result;
}

// The page's text and where each character sits: returns {String text, float[] boxes} where boxes holds
// left, top, right, bottom per character as fractions of the page as displayed (rotation applied), or -1s for
// characters PDFium generates without a box (line breaks). Null when the page cannot be read.
JNIEXPORT jobjectArray JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativePageText(JNIEnv* env, jobject, jlong handle, jint index) {
    constexpr int kScale = 1000000;
    constexpr int kMaxChars = 200000;  // a page of tiny print; malformed files can claim far more
    std::vector<unsigned short> text;
    std::vector<jfloat> boxes;
    bool ok = false;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        FPDF_PAGE page = FPDF_LoadPage(asDoc(handle)->doc, index);
        if (page != nullptr) {
            FPDF_TEXTPAGE textPage = FPDFText_LoadPage(page);
            if (textPage != nullptr) {
                int n = std::min(FPDFText_CountChars(textPage), kMaxChars);
                if (n > 0) {
                    text.resize(static_cast<size_t>(n) + 1);
                    int got = FPDFText_GetText(textPage, 0, n, text.data());
                    text.resize(got > 0 ? static_cast<size_t>(got - 1) : 0);  // drop the terminating zero
                    boxes.resize(static_cast<size_t>(n) * 4, -1.0f);
                    for (int i = 0; i < n; i++) {
                        // The loose box spans the font's full height (ascent to descent), so a highlighted line is
                        // one even band rather than the tight outline of each glyph.
                        FS_RECTF box{};
                        if (!FPDFText_GetLooseCharBox(textPage, i, &box)) continue;
                        int x1, y1, x2, y2;
                        FPDF_PageToDevice(page, 0, 0, kScale, kScale, 0, box.left, box.top, &x1, &y1);
                        FPDF_PageToDevice(page, 0, 0, kScale, kScale, 0, box.right, box.bottom, &x2, &y2);
                        size_t k = static_cast<size_t>(i) * 4;
                        boxes[k] = static_cast<jfloat>(std::min(x1, x2)) / kScale;
                        boxes[k + 1] = static_cast<jfloat>(std::min(y1, y2)) / kScale;
                        boxes[k + 2] = static_cast<jfloat>(std::max(x1, x2)) / kScale;
                        boxes[k + 3] = static_cast<jfloat>(std::max(y1, y2)) / kScale;
                    }
                } else {
                    text.clear();
                }
                FPDFText_ClosePage(textPage);
                ok = true;
            }
            FPDF_ClosePage(page);
        }
    }
    if (!ok) return nullptr;
    jstring jtext = env->NewString(reinterpret_cast<const jchar*>(text.data()), static_cast<jsize>(text.size()));
    jfloatArray jboxes = env->NewFloatArray(static_cast<jsize>(boxes.size()));
    if (!boxes.empty()) env->SetFloatArrayRegion(jboxes, 0, static_cast<jsize>(boxes.size()), boxes.data());
    jobjectArray result = env->NewObjectArray(2, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(result, 0, jtext);
    env->SetObjectArrayElement(result, 1, jboxes);
    return result;
}

// What the converter reads from a page (Phase 3, `PdfSource.page`), mirroring `JnaPdfSource` on the desktop JVM field
// for field: {float[] size, String text, float[] boxes, float[] fontSizes, int[] weights, int[] fontIds, int[] colors,
// boolean[] generated, String[] fontNames, int[] fontFlags, float[] images, float[] paths, boolean[] pathFilled}.
// Boxes are points from the top left of the page as displayed (-1s for characters without one). Null when the page
// cannot be read.
JNIEXPORT jobjectArray JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativeConversionPage(JNIEnv* env, jobject, jlong handle, jint index) {
    constexpr int kSub = 100;  // device units per point
    constexpr int kMaxChars = 200000;
    constexpr int kMaxObjects = 100000;
    FS_SIZEF size{0, 0};
    std::vector<unsigned short> text;
    std::vector<jfloat> boxes, fontSizes, images, paths;
    std::vector<jint> weights, fontIds, colors, fontFlags;
    std::vector<jboolean> generated, pathFilled;
    std::vector<std::string> fontNames;
    bool ok = false;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        FPDF_DOCUMENT doc = asDoc(handle)->doc;
        FPDF_GetPageSizeByIndexF(doc, index, &size);
        FPDF_PAGE page = FPDF_LoadPage(doc, index);
        if (page != nullptr) {
            ok = true;
            const int w = static_cast<int>(size.width * kSub), h = static_cast<int>(size.height * kSub);
            auto toTop = [&](float l, float t, float r, float b, std::vector<jfloat>& out) {
                int x1, y1, x2, y2;
                FPDF_PageToDevice(page, 0, 0, w, h, 0, l, t, &x1, &y1);
                FPDF_PageToDevice(page, 0, 0, w, h, 0, r, b, &x2, &y2);
                out.push_back(static_cast<jfloat>(std::min(x1, x2)) / kSub);
                out.push_back(static_cast<jfloat>(std::min(y1, y2)) / kSub);
                out.push_back(static_cast<jfloat>(std::max(x1, x2)) / kSub);
                out.push_back(static_cast<jfloat>(std::max(y1, y2)) / kSub);
            };
            int objects = std::min(FPDFPage_CountObjects(page), kMaxObjects);
            for (int i = 0; i < objects; i++) {
                FPDF_PAGEOBJECT obj = FPDFPage_GetObject(page, i);
                int type = FPDFPageObj_GetType(obj);
                if (type != FPDF_PAGEOBJ_IMAGE && type != FPDF_PAGEOBJ_PATH && type != FPDF_PAGEOBJ_FORM) continue;
                float l, b, r, t;
                if (!FPDFPageObj_GetBounds(obj, &l, &b, &r, &t)) continue;
                if (type == FPDF_PAGEOBJ_IMAGE || (type == FPDF_PAGEOBJ_FORM && containsImage(obj, 0))) {
                    toTop(l, t, r, b, images);
                } else if (type == FPDF_PAGEOBJ_PATH) {
                    int fill = 0;
                    FPDF_BOOL stroke = 0;
                    FPDFPath_GetDrawMode(obj, &fill, &stroke);
                    toTop(l, t, r, b, paths);
                    pathFilled.push_back(fill != 0 ? JNI_TRUE : JNI_FALSE);
                }
            }
            FPDF_TEXTPAGE textPage = FPDFText_LoadPage(page);
            if (textPage != nullptr) {
                int n = std::max(0, std::min(FPDFText_CountChars(textPage), kMaxChars));
                char nameBuf[256];
                for (int i = 0; i < n; i++) {
                    text.push_back(static_cast<unsigned short>(FPDFText_GetUnicode(textPage, i)));
                    generated.push_back(FPDFText_IsGenerated(textPage, i) == 1 ? JNI_TRUE : JNI_FALSE);
                    FS_RECTF box{};
                    if (FPDFText_GetLooseCharBox(textPage, i, &box)) {
                        toTop(box.left, box.top, box.right, box.bottom, boxes);
                    } else {
                        for (int k = 0; k < 4; k++) boxes.push_back(-1.0f);
                    }
                    fontSizes.push_back(static_cast<jfloat>(FPDFText_GetFontSize(textPage, i)));
                    weights.push_back(FPDFText_GetFontWeight(textPage, i));
                    int flags = 0;
                    unsigned long len = FPDFText_GetFontInfo(textPage, i, nameBuf, sizeof(nameBuf), &flags);
                    int id = -1;
                    if (len > 1) {
                        std::string name(nameBuf, std::min<unsigned long>(len, sizeof(nameBuf)) - 1);
                        for (size_t f = 0; f < fontNames.size(); f++) {
                            if (fontNames[f] == name && fontFlags[f] == flags) { id = static_cast<int>(f); break; }
                        }
                        if (id < 0) {
                            id = static_cast<int>(fontNames.size());
                            fontNames.push_back(name);
                            fontFlags.push_back(flags);
                        }
                    }
                    fontIds.push_back(id);
                    unsigned int cr = 0, cg = 0, cb = 0, ca = 0;
                    colors.push_back(FPDFText_GetFillColor(textPage, i, &cr, &cg, &cb, &ca)
                                         ? static_cast<jint>((ca << 24) | (cr << 16) | (cg << 8) | cb)
                                         : static_cast<jint>(0xFF000000));
                }
                FPDFText_ClosePage(textPage);
            }
            FPDF_ClosePage(page);
        }
    }
    if (!ok) return nullptr;
    auto floats = [&](const std::vector<jfloat>& v) {
        jfloatArray a = env->NewFloatArray(static_cast<jsize>(v.size()));
        if (!v.empty()) env->SetFloatArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
        return a;
    };
    auto ints = [&](const std::vector<jint>& v) {
        jintArray a = env->NewIntArray(static_cast<jsize>(v.size()));
        if (!v.empty()) env->SetIntArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
        return a;
    };
    auto bools = [&](const std::vector<jboolean>& v) {
        jbooleanArray a = env->NewBooleanArray(static_cast<jsize>(v.size()));
        if (!v.empty()) env->SetBooleanArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
        return a;
    };
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray names = env->NewObjectArray(static_cast<jsize>(fontNames.size()), stringClass, nullptr);
    for (size_t f = 0; f < fontNames.size(); f++) {
        jstring s = env->NewStringUTF(fontNames[f].c_str());
        env->SetObjectArrayElement(names, static_cast<jsize>(f), s);
        env->DeleteLocalRef(s);
    }
    std::vector<jfloat> sizeOut{size.width, size.height};
    jobjectArray result = env->NewObjectArray(13, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(result, 0, floats(sizeOut));
    env->SetObjectArrayElement(result, 1, env->NewString(reinterpret_cast<const jchar*>(text.data()), static_cast<jsize>(text.size())));
    env->SetObjectArrayElement(result, 2, floats(boxes));
    env->SetObjectArrayElement(result, 3, floats(fontSizes));
    env->SetObjectArrayElement(result, 4, ints(weights));
    env->SetObjectArrayElement(result, 5, ints(fontIds));
    env->SetObjectArrayElement(result, 6, ints(colors));
    env->SetObjectArrayElement(result, 7, bools(generated));
    env->SetObjectArrayElement(result, 8, names);
    env->SetObjectArrayElement(result, 9, ints(fontFlags));
    env->SetObjectArrayElement(result, 10, floats(images));
    env->SetObjectArrayElement(result, 11, floats(paths));
    env->SetObjectArrayElement(result, 12, bools(pathFilled));
    return result;
}

// Document information entry such as "Title" or "Author"; null when absent or empty.
JNIEXPORT jstring JNICALL Java_com_flowfolio_core_pdf_pdfium_PdfiumNative_nativeMetaText(JNIEnv* env, jobject, jlong handle, jstring jtag) {
    const char* tag = env->GetStringUTFChars(jtag, nullptr);
    std::vector<unsigned short> buf;
    unsigned long bytes = 0;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        unsigned long n = FPDF_GetMetaText(asDoc(handle)->doc, tag, nullptr, 0);
        if (n > 2) {
            buf.resize(n / 2 + 1);
            bytes = FPDF_GetMetaText(asDoc(handle)->doc, tag, buf.data(), n);
        }
    }
    env->ReleaseStringUTFChars(jtag, tag);
    return bytes > 2 ? utf16(env, buf, bytes) : nullptr;
}

}  // extern "C"
