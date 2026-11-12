package com.flowfolio.core.conversion.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

/** [OnnxModel] on ONNX Runtime's JVM build, for tests (the phone's is the same API in `:core:data`). */
class JvmOnnxModel(file: File) : OnnxModel {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(file.path, OrtSession.SessionOptions())

    override val metadata: Map<String, String> = session.metadata.customMetadata

    override fun run(input: FloatArray, shape: LongArray): Tensor {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val out = result.get(0) as OnnxTensor
                val buffer = out.floatBuffer
                val data = FloatArray(buffer.remaining()).also { buffer.get(it) }
                return Tensor(data, out.info.shape)
            }
        }
    }

    override fun close() = session.close()
}
