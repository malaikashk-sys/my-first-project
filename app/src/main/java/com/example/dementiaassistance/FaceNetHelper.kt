package com.example.dementiaassistance

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceNetHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val INPUT_SIZE  = 160   // FaceNet expects 160x160
    private val EMBEDDING_SIZE = 128

    init {
        try {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(loadModelFile(), options)
        } catch (e: Exception) {
            android.util.Log.e("FaceNetHelper", "Model load failed: ${e.message}")
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("facenet.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    // ── Main function: Bitmap → 128-float embedding ───────────────────────
    fun getEmbedding(bitmap: Bitmap): FloatArray? {
        val interp = interpreter ?: return null
        return try {
            val resized    = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuffer = bitmapToByteBuffer(resized)
            val output     = Array(1) { FloatArray(EMBEDDING_SIZE) }
            interp.run(inputBuffer, output)
            val embedding = output[0]
            normalize(embedding)
            embedding
        } catch (e: Exception) {
            android.util.Log.e("FaceNetHelper", "Inference failed: ${e.message}")
            null
        }
    }

    // ── Preprocess: Bitmap → normalized float ByteBuffer ─────────────────
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (px in pixels) {
            val r = ((px shr 16) and 0xFF).toFloat()
            val g = ((px shr 8)  and 0xFF).toFloat()
            val b = (px          and 0xFF).toFloat()
            // Normalize to [-1, 1]
            buf.putFloat((r - 127.5f) / 127.5f)
            buf.putFloat((g - 127.5f) / 127.5f)
            buf.putFloat((b - 127.5f) / 127.5f)
        }
        return buf
    }

    // ── L2 Normalize the embedding ────────────────────────────────────────
    private fun normalize(embedding: FloatArray) {
        var sum = 0f
        for (v in embedding) sum += v * v
        val norm = sqrt(sum)
        if (norm > 0f) {
            for (i in embedding.indices) embedding[i] /= norm
        }
    }

    // ── Compare two embeddings: cosine similarity → 0..1 ─────────────────
    fun cosineSimilarity(e1: FloatArray, e2: FloatArray): Float {
        if (e1.size != e2.size) return 0f
        var dot = 0f
        for (i in e1.indices) dot += e1[i] * e2[i]
        // Both are L2-normalized so denominator = 1
        // Map from [-1,1] to [0,1]
        return ((dot + 1f) / 2f).coerceIn(0f, 1f)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}