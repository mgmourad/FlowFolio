package com.flowfolio.core.data.ocr

import com.flowfolio.core.conversion.ocr.OnnxMetadata
import com.flowfolio.core.conversion.ocr.OnnxModel
import com.flowfolio.core.conversion.ocr.Tensor
import com.flowfolio.core.voice.OrtBridge
import java.io.File

/**
 * [OnnxModel] on the phone (ADR-0054): the ONNX Runtime sherpa-onnx ships, through [OrtBridge]. Metadata is read from
 * the file itself ([OnnxMetadata]), which keeps PaddleOCR's alphabets whole.
 */
class AndroidOnnxModel(file: File, threads: Int) : OnnxModel {
    private val bridge = OrtBridge(file.path, threads)

    override val metadata: Map<String, String> = OnnxMetadata.read(file)

    override fun run(input: FloatArray, shape: LongArray): Tensor {
        val (data, outShape) = bridge.run(input, shape)
        return Tensor(data, outShape)
    }

    override fun close() = bridge.close()
}
