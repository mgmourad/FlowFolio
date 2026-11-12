// FlowFolio's bridge to Tesseract (ADR-0054), for Arabic OCR: init an engine on a tessdata directory, read a page of
// raw pixels, and return each text line with its box and confidence. Uses Tesseract's C API with SetImage (raw
// grayscale bytes), so no image codec library is involved; Leptonica is only linked because libtesseract needs it.
#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include <tesseract/capi.h>

namespace {

// Throws a Java RuntimeException carrying [message]; returns true so callers can `return on error`.
bool Fail(JNIEnv* jni, const std::string& message) {
    jclass error = jni->FindClass("java/lang/RuntimeException");
    jni->ThrowNew(error, ("tesseract: " + message).c_str());
    return true;
}

jstring ToJava(JNIEnv* jni, const char* text) {
    return jni->NewStringUTF(text == nullptr ? "" : text);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL Java_com_flowfolio_core_ocr_TesseractBridge_nativeCreate(
    JNIEnv* jni, jclass, jstring datapath, jstring lang) {
    const char* path = jni->GetStringUTFChars(datapath, nullptr);
    const char* language = jni->GetStringUTFChars(lang, nullptr);
    TessBaseAPI* api = TessBaseAPICreate();
    // LSTM-only: the tessdata_best model used is LSTM, so the legacy engine is never exercised.
    int rc = TessBaseAPIInit3(api, path, language);
    jni->ReleaseStringUTFChars(datapath, path);
    jni->ReleaseStringUTFChars(lang, language);
    if (rc != 0) {
        TessBaseAPIDelete(api);
        Fail(jni, "TessBaseAPIInit3 failed (is ara.traineddata present?)");
        return 0;
    }
    // Automatic page segmentation (columns, tables) and keep the spaces between words.
    TessBaseAPISetVariable(api, "tessedit_pageseg_mode", "3");
    TessBaseAPISetVariable(api, "preserve_interword_spaces", "1");
    return reinterpret_cast<jlong>(api);
}

// Reads [pixels] (ARGB, row by row) into grayscale, recognises the page, and returns its text lines as a
// three-element Object[]: String[] text, int[] boxes (left, top, right, bottom per line), float[] confidence (0..1).
extern "C" JNIEXPORT jobjectArray JNICALL Java_com_flowfolio_core_ocr_TesseractBridge_nativeRead(
    JNIEnv* jni, jclass, jlong handle, jintArray pixels, jint width, jint height) {
    auto* api = reinterpret_cast<TessBaseAPI*>(handle);
    jsize count = jni->GetArrayLength(pixels);
    std::vector<jint> argb(static_cast<size_t>(count));
    jni->GetIntArrayRegion(pixels, 0, count, argb.data());

    // Luminance grayscale, one byte per pixel, no alpha.
    std::vector<uint8_t> gray(static_cast<size_t>(width) * height);
    for (int i = 0; i < count; ++i) {
        uint32_t p = static_cast<uint32_t>(argb[static_cast<size_t>(i)]);
        uint32_t r = (p >> 16) & 0xffu;
        uint32_t g = (p >> 8) & 0xffu;
        uint32_t b = p & 0xffu;
        gray[static_cast<size_t>(i)] = static_cast<uint8_t>((r * 299u + g * 587u + b * 114u) / 1000u);
    }
    TessBaseAPISetImage(api, gray.data(), width, height, 1, width);
    if (TessBaseAPIRecognize(api, nullptr) != 0) {
        Fail(jni, "Recognize failed");
        return nullptr;
    }

    TessResultIterator* it = TessBaseAPIGetIterator(api);
    std::vector<std::string> texts;
    std::vector<jint> boxes;
    std::vector<jfloat> confidences;
    constexpr auto kLine = tesseract::RIL_TEXTLINE;
    do {
        char* text = TessResultIteratorGetUTF8Text(it, kLine);
        if (text != nullptr && text[0] != '\0') {
            texts.emplace_back(text);
            int x1 = 0, y1 = 0, x2 = 0, y2 = 0;
            TessPageIteratorBoundingBox(it, kLine, &x1, &y1, &x2, &y2);
            boxes.insert(boxes.end(), {x1, y1, x2, y2});
            confidences.push_back(TessResultIteratorConfidence(it, kLine) / 100.0f);
        }
        if (text != nullptr) TessDeleteText(text);
    } while (TessResultIteratorNext(it, kLine));
    TessResultIteratorDelete(it);

    jclass stringClass = jni->FindClass("java/lang/String");
    jobjectArray textsOut = jni->NewObjectArray(static_cast<jsize>(texts.size()), stringClass, nullptr);
    for (size_t i = 0; i < texts.size(); ++i) {
        jni->SetObjectArrayElement(textsOut, static_cast<jsize>(i), ToJava(jni, texts[i].c_str()));
    }
    jintArray boxesOut = jni->NewIntArray(static_cast<jsize>(boxes.size()));
    jni->SetIntArrayRegion(boxesOut, 0, static_cast<jsize>(boxes.size()), boxes.data());
    jfloatArray confidencesOut = jni->NewFloatArray(static_cast<jsize>(confidences.size()));
    jni->SetFloatArrayRegion(confidencesOut, 0, static_cast<jsize>(confidences.size()), confidences.data());

    jclass objectClass = jni->FindClass("java/lang/Object");
    jobjectArray out = jni->NewObjectArray(3, objectClass, nullptr);
    jni->SetObjectArrayElement(out, 0, textsOut);
    jni->SetObjectArrayElement(out, 1, boxesOut);
    jni->SetObjectArrayElement(out, 2, confidencesOut);
    return out;
}

extern "C" JNIEXPORT void JNICALL Java_com_flowfolio_core_ocr_TesseractBridge_nativeClose(
    JNIEnv*, jclass, jlong handle) {
    auto* api = reinterpret_cast<TessBaseAPI*>(handle);
    if (api == nullptr) return;
    TessBaseAPIEnd(api);
    TessBaseAPIDelete(api);
}
