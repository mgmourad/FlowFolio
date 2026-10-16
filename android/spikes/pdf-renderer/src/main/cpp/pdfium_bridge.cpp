// Spike JNI bridge to PDFium: open, render, outline, page labels, and a Stage B extraction probe.
// PDFium is not thread-safe, so every entry point takes one process-wide lock.

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "fpdf_doc.h"
#include "fpdf_edit.h"
#include "fpdf_text.h"
#include "fpdfview.h"

#define LOG_TAG "FFPdfium"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

FPDF_DOCUMENT asDoc(jlong handle) { return reinterpret_cast<FPDF_DOCUMENT>(handle); }

// PDFium returns UTF-16LE with a trailing NUL; byteCount includes it.
jstring utf16(JNIEnv* env, const std::vector<unsigned short>& buf, unsigned long byteCount) {
    jsize len = byteCount >= 2 ? static_cast<jsize>(byteCount / 2 - 1) : 0;
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
        item.titleBytes = FPDFBookmark_GetTitle(bm, item.title.data(), n);
        FPDF_DEST dest = FPDFBookmark_GetDest(doc, bm);
        if (dest == nullptr) {
            FPDF_ACTION action = FPDFBookmark_GetAction(bm);
            if (action != nullptr && FPDFAction_GetType(action) == PDFACTION_GOTO) dest = FPDFAction_GetDest(doc, action);
        }
        if (dest != nullptr) item.page = FPDFDest_GetDestPageIndex(doc, dest);
        out.push_back(std::move(item));
        if (depth < 32) walkOutline(doc, bm, depth + 1, out, budget);  // depth guard against malformed trees
        bm = FPDFBookmark_GetNextSibling(doc, bm);
    }
}

struct ObjStats {
    int text = 0, path = 0, image = 0, shading = 0, form = 0, pathSegments = 0;
};

void countObject(FPDF_PAGEOBJECT obj, ObjStats& s, int depth) {
    switch (FPDFPageObj_GetType(obj)) {
        case FPDF_PAGEOBJ_TEXT: s.text++; break;
        case FPDF_PAGEOBJ_PATH:
            s.path++;
            s.pathSegments += FPDFPath_CountSegments(obj);
            break;
        case FPDF_PAGEOBJ_IMAGE: s.image++; break;
        case FPDF_PAGEOBJ_SHADING: s.shading++; break;
        case FPDF_PAGEOBJ_FORM: {
            s.form++;
            if (depth < 16) {
                int n = FPDFFormObj_CountObjects(obj);
                for (int i = 0; i < n; i++) countObject(FPDFFormObj_GetObject(obj, i), s, depth + 1);
            }
            break;
        }
        default: break;
    }
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_flowfolio_spike_pdf_engine_PdfiumNative_nativeOpen(JNIEnv* env, jobject, jstring jpath) {
    ensureInit();
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    std::lock_guard<std::mutex> guard(g_lock);
    FPDF_DOCUMENT doc = FPDF_LoadDocument(path, nullptr);
    if (doc == nullptr) LOGE("FPDF_LoadDocument failed: %lu", FPDF_GetLastError());
    env->ReleaseStringUTFChars(jpath, path);
    return reinterpret_cast<jlong>(doc);
}

JNIEXPORT void JNICALL Java_com_flowfolio_spike_pdf_engine_PdfiumNative_nativeClose(JNIEnv*, jobject, jlong doc) {
    std::lock_guard<std::mutex> guard(g_lock);
    FPDF_CloseDocument(asDoc(doc));
}

JNIEXPORT jint JNICALL Java_com_flowfolio_spike_pdf_engine_PdfiumNative_nativePageCount(JNIEnv*, jobject, jlong doc) {
    std::lock_guard<std::mutex> guard(g_lock);
    return FPDF_GetPageCount(asDoc(doc));
}

JNIEXPORT jfloatArray JNICALL Java_com_flowfolio_spike_pdf_engine_PdfiumNative_nativePageSize(JNIEnv* env, jobject, jlong doc, jint index) {
    FS_SIZEF size{0, 0};
    {
        std::lock_guard<std::mutex> guard(g_lock);
        FPDF_GetPageSizeByIndexF(asDoc(doc), index, &size);
    }
    jfloat values[2] = {size.width, size.height};
    jfloatArray result = env->NewFloatArray(2);
    env->SetFloatArrayRegion(result, 0, 2, values);
    return result;
}

JNIEXPORT jboolean JNICALL Java_com_flowfolio_spike_pdf_engine_PdfiumNative_nativeRender(JNIEnv* env, jobject, jlong doc, jint index, jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    bool ok = false;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        FPDF_PAGE page = FPDF_LoadPage(asDoc(doc), index);
        if (page != nullptr) {
            int w = static_cast<int>(info.width), h = static_cast<int>(info.height);
            FPDF_BITMAP target = FPDFBitmap_CreateEx(w, h, FPDFBitmap_BGRA, pixels, static_cast<int>(info.stride));
            FPDFBitmap_FillRect(target, 0, 0, w, h, 0xFFFFFFFF);
            // REVERSE_BYTE_ORDER writes RGBA, which is Android's ARGB_8888 memory layout.
            FPDF_RenderPageBitmap(target, page, 0, 0, w, h, 0, FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER);
            FPDFBitmap_Destroy(target);
            FPDF_ClosePage(page);
            ok = true;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Returns "depth|pageIndex|title" strings in document order.
JNIEXPORT jobjectArray JNICALL Java_com_flowfolio_spike_pdf_engine_PdfiumNative_nativeOutline(JNIEnv* env, jobject, jlong doc) {
    std::vector<OutlineItem> items;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        int budget = 20000;
        walkOutline(asDoc(doc), nullptr, 0, items, budget);
    }
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(items.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < items.size(); i++) {
        jstring title = utf16(env, items[i].title, items[i].titleBytes);
        std::string prefix = std::to_string(items[i].depth) + "|" + std::to_string(items[i].page) + "|";
        jstring jprefix = env->NewStringUTF(prefix.c_str());
        jclass sb = env->FindClass("java/lang/StringBuilder");
        jobject builder = env->NewObject(sb, env->GetMethodID(sb, "<init>", "(Ljava/lang/String;)V"), jprefix);
        jmethodID append = env->GetMethodID(sb, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        env->CallObjectMethod(builder, append, title);
        jstring line = static_cast<jstring>(env->CallObjectMethod(builder, env->GetMethodID(sb, "toString", "()Ljava/lang/String;")));
        env->SetObjectArrayElement(result, static_cast<jsize>(i), line);
        env->DeleteLocalRef(title);
        env->DeleteLocalRef(jprefix);
        env->DeleteLocalRef(builder);
        env->DeleteLocalRef(line);
        env->DeleteLocalRef(sb);
    }
    return result;
}

JNIEXPORT jstring JNICALL Java_com_flowfolio_spike_pdf_engine_PdfiumNative_nativePageLabel(JNIEnv* env, jobject, jlong doc, jint index) {
    std::vector<unsigned short> buf;
    unsigned long bytes;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        unsigned long n = FPDF_GetPageLabel(asDoc(doc), index, nullptr, 0);
        if (n == 0) return nullptr;
        buf.resize(n / 2 + 1);
        bytes = FPDF_GetPageLabel(asDoc(doc), index, buf.data(), n);
    }
    return utf16(env, buf, bytes);
}

// Stage B probe. Returns Object[] {
//   float[] per char: left, top, right, bottom (PDF user space, origin bottom-left), fontSize, fontWeight
//   int[]   per char: codepoint, fontId, fillARGB
//   String[] font base names indexed by fontId
//   int[]   object stats: text, path, image, shading, form, pathSegments
// }
JNIEXPORT jobjectArray JNICALL Java_com_flowfolio_spike_pdf_engine_PdfiumNative_nativeExtract(JNIEnv* env, jobject, jlong doc, jint index) {
    std::vector<float> geom;
    std::vector<int> ints;
    std::vector<std::string> fontNames;
    ObjStats stats;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        FPDF_PAGE page = FPDF_LoadPage(asDoc(doc), index);
        if (page == nullptr) return nullptr;
        int objects = FPDFPage_CountObjects(page);
        for (int i = 0; i < objects; i++) countObject(FPDFPage_GetObject(page, i), stats, 0);

        FPDF_TEXTPAGE text = FPDFText_LoadPage(page);
        int count = FPDFText_CountChars(text);
        geom.reserve(static_cast<size_t>(count) * 6);
        ints.reserve(static_cast<size_t>(count) * 3);
        std::unordered_map<FPDF_FONT, int> fontIds;
        for (int i = 0; i < count; i++) {
            double l = 0, r = 0, b = 0, t = 0;
            FPDFText_GetCharBox(text, i, &l, &r, &b, &t);
            unsigned int cr = 0, cg = 0, cb = 0, ca = 255;
            FPDFText_GetFillColor(text, i, &cr, &cg, &cb, &ca);
            int fontId = -1;
            FPDF_PAGEOBJECT textObj = FPDFText_GetTextObject(text, i);
            if (textObj != nullptr) {
                FPDF_FONT font = FPDFTextObj_GetFont(textObj);
                auto it = fontIds.find(font);
                if (it == fontIds.end()) {
                    size_t n = FPDFFont_GetBaseFontName(font, nullptr, 0);
                    std::string name(n, '\0');
                    if (n > 0) FPDFFont_GetBaseFontName(font, name.data(), n);
                    if (!name.empty() && name.back() == '\0') name.pop_back();
                    // NewStringUTF needs modified UTF-8; font names are ASCII in practice, so mask anything else.
                    for (char& c : name) {
                        if (static_cast<unsigned char>(c) >= 0x80 || c == '\0') c = '?';
                    }
                    fontId = static_cast<int>(fontNames.size());
                    fontIds.emplace(font, fontId);
                    fontNames.push_back(name);
                } else {
                    fontId = it->second;
                }
            }
            geom.insert(geom.end(), {static_cast<float>(l), static_cast<float>(t), static_cast<float>(r), static_cast<float>(b),
                                     static_cast<float>(FPDFText_GetFontSize(text, i)), static_cast<float>(FPDFText_GetFontWeight(text, i))});
            int argb = static_cast<int>((ca << 24) | (cr << 16) | (cg << 8) | cb);
            ints.insert(ints.end(), {static_cast<int>(FPDFText_GetUnicode(text, i)), fontId, argb});
        }
        FPDFText_ClosePage(text);
        FPDF_ClosePage(page);
    }

    jfloatArray jgeom = env->NewFloatArray(static_cast<jsize>(geom.size()));
    env->SetFloatArrayRegion(jgeom, 0, static_cast<jsize>(geom.size()), geom.data());
    jintArray jints = env->NewIntArray(static_cast<jsize>(ints.size()));
    env->SetIntArrayRegion(jints, 0, static_cast<jsize>(ints.size()), ints.data());
    jobjectArray jfonts = env->NewObjectArray(static_cast<jsize>(fontNames.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < fontNames.size(); i++) {
        jstring s = env->NewStringUTF(fontNames[i].c_str());
        env->SetObjectArrayElement(jfonts, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    int statValues[6] = {stats.text, stats.path, stats.image, stats.shading, stats.form, stats.pathSegments};
    jintArray jstats = env->NewIntArray(6);
    env->SetIntArrayRegion(jstats, 0, 6, statValues);

    jobjectArray result = env->NewObjectArray(4, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(result, 0, jgeom);
    env->SetObjectArrayElement(result, 1, jints);
    env->SetObjectArrayElement(result, 2, jfonts);
    env->SetObjectArrayElement(result, 3, jstats);
    return result;
}

}  // extern "C"
