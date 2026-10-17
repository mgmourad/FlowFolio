// JNI bridge to PDFium (ADR-0010). Kotlin calls these from one dedicated thread; the mutex is a second line of
// defence because PDFium is not thread-safe. Documents are read through a dup'ed file descriptor so files from the
// Storage Access Framework (content:// URIs) work the same as app-private files.

#include <android/bitmap.h>
#include <android/log.h>
#include <errno.h>
#include <jni.h>
#include <sys/stat.h>
#include <unistd.h>

#include <mutex>
#include <string>
#include <vector>

#include "fpdf_doc.h"
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
                                                                                          jint offsetX, jint offsetY) {
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

}  // extern "C"
