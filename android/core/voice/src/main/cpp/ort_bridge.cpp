// FlowFolio's bridge to the ONNX Runtime that sherpa-onnx ships (ADR-0054), for OCR: load a model, run it on one
// float tensor, return its first output. ORT's own Java API cannot load against this runtime (its symbols are
// versioned 1.28.2, the Java glue published for Android asks for 1.28.0), so this small C API bridge stands in.
#include <jni.h>

#include <string>
#include <vector>

#include "onnxruntime_c_api.h"

namespace {

const OrtApi* Api() {
    static const OrtApi* api = OrtGetApiBase()->GetApi(ORT_API_VERSION);
    return api;
}

OrtEnv* Env() {
    static OrtEnv* env = [] {
        OrtEnv* e = nullptr;
        OrtStatus* status = Api()->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "flowfolio-ocr", &e);
        if (status != nullptr) Api()->ReleaseStatus(status);
        return e;
    }();
    return env;
}

struct Model {
    OrtSession* session = nullptr;
    std::string input;
    std::string output;
};

// Throws a Java RuntimeException carrying ORT's message; true when there was an error.
bool Failed(JNIEnv* jni, OrtStatus* status) {
    if (status == nullptr) return false;
    std::string message = Api()->GetErrorMessage(status);
    Api()->ReleaseStatus(status);
    jclass error = jni->FindClass("java/lang/RuntimeException");
    jni->ThrowNew(error, ("onnxruntime: " + message).c_str());
    return true;
}

std::string Name(JNIEnv* jni, OrtSession* session, bool input) {
    OrtAllocator* allocator = nullptr;
    if (Failed(jni, Api()->GetAllocatorWithDefaultOptions(&allocator))) return {};
    char* name = nullptr;
    OrtStatus* status = input ? Api()->SessionGetInputName(session, 0, allocator, &name)
                              : Api()->SessionGetOutputName(session, 0, allocator, &name);
    if (Failed(jni, status)) return {};
    std::string out = name;
    allocator->Free(allocator, name);
    return out;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL Java_com_flowfolio_core_voice_OrtBridge_nativeCreate(
    JNIEnv* jni, jclass, jstring path, jint threads) {
    OrtSessionOptions* options = nullptr;
    if (Failed(jni, Api()->CreateSessionOptions(&options))) return 0;
    if (Failed(jni, Api()->SetIntraOpNumThreads(options, threads)) ||
        Failed(jni, Api()->SetSessionGraphOptimizationLevel(options, ORT_ENABLE_ALL))) {
        Api()->ReleaseSessionOptions(options);
        return 0;
    }
    const char* file = jni->GetStringUTFChars(path, nullptr);
    auto* model = new Model();
    OrtStatus* status = Api()->CreateSession(Env(), file, options, &model->session);
    jni->ReleaseStringUTFChars(path, file);
    Api()->ReleaseSessionOptions(options);
    if (Failed(jni, status)) {
        delete model;
        return 0;
    }
    model->input = Name(jni, model->session, true);
    model->output = Name(jni, model->session, false);
    return reinterpret_cast<jlong>(model);
}

// Runs the model on [input] shaped [shape]; writes the output's rank and dimensions into [shapeOut] (rank first).
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_flowfolio_core_voice_OrtBridge_nativeRun(
    JNIEnv* jni, jclass, jlong handle, jfloatArray input, jlongArray shape, jlongArray shapeOut) {
    auto* model = reinterpret_cast<Model*>(handle);
    jsize count = jni->GetArrayLength(input);
    std::vector<float> data(count);
    jni->GetFloatArrayRegion(input, 0, count, data.data());
    jsize rank = jni->GetArrayLength(shape);
    std::vector<int64_t> dims(rank);
    jni->GetLongArrayRegion(shape, 0, rank, reinterpret_cast<jlong*>(dims.data()));

    OrtMemoryInfo* memory = nullptr;
    if (Failed(jni, Api()->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memory))) return nullptr;
    OrtValue* tensor = nullptr;
    OrtStatus* status = Api()->CreateTensorWithDataAsOrtValue(
        memory, data.data(), data.size() * sizeof(float), dims.data(), dims.size(),
        ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &tensor);
    Api()->ReleaseMemoryInfo(memory);
    if (Failed(jni, status)) return nullptr;

    const char* in = model->input.c_str();
    const char* out = model->output.c_str();
    OrtValue* result = nullptr;
    status = Api()->Run(model->session, nullptr, &in, &tensor, 1, &out, 1, &result);
    Api()->ReleaseValue(tensor);
    if (Failed(jni, status)) return nullptr;

    OrtTensorTypeAndShapeInfo* info = nullptr;
    if (Failed(jni, Api()->GetTensorTypeAndShape(result, &info))) {
        Api()->ReleaseValue(result);
        return nullptr;
    }
    size_t outRank = 0;
    size_t elements = 0;
    std::vector<int64_t> outDims;
    bool failed = Failed(jni, Api()->GetDimensionsCount(info, &outRank));
    if (!failed) {
        outDims.resize(outRank);
        failed = Failed(jni, Api()->GetDimensions(info, outDims.data(), outRank)) ||
                 Failed(jni, Api()->GetTensorShapeElementCount(info, &elements));
    }
    Api()->ReleaseTensorTypeAndShapeInfo(info);
    if (failed) {
        Api()->ReleaseValue(result);
        return nullptr;
    }

    float* values = nullptr;
    if (Failed(jni, Api()->GetTensorMutableData(result, reinterpret_cast<void**>(&values)))) {
        Api()->ReleaseValue(result);
        return nullptr;
    }
    jfloatArray array = jni->NewFloatArray(static_cast<jsize>(elements));
    jni->SetFloatArrayRegion(array, 0, static_cast<jsize>(elements), values);
    Api()->ReleaseValue(result);

    std::vector<jlong> shaped(outRank + 1);
    shaped[0] = static_cast<jlong>(outRank);
    for (size_t i = 0; i < outRank; ++i) shaped[i + 1] = outDims[i];
    jni->SetLongArrayRegion(shapeOut, 0, static_cast<jsize>(shaped.size()), shaped.data());
    return array;
}

extern "C" JNIEXPORT void JNICALL Java_com_flowfolio_core_voice_OrtBridge_nativeClose(JNIEnv*, jclass, jlong handle) {
    auto* model = reinterpret_cast<Model*>(handle);
    if (model == nullptr) return;
    Api()->ReleaseSession(model->session);
    delete model;
}
