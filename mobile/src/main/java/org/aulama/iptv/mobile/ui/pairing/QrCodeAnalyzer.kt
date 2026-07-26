package org.aulama.iptv.mobile.ui.pairing

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.atomic.AtomicBoolean

internal class QrCodeAnalyzer(
    private val onPairingCode: (PairingCode) -> Unit,
) : ImageAnalysis.Analyzer {
    private val delivered = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8",
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        if (delivered.get()) {
            image.close()
            return
        }

        try {
            val luminance = image.toLuminanceSource() ?: return
            val rawValue = decode(luminance) ?: return
            val pairingCode = PairingCodeParser.fromQr(rawValue) ?: return
            if (delivered.compareAndSet(false, true)) {
                mainHandler.post { onPairingCode(pairingCode) }
            }
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun decode(source: PlanarYUVLuminanceSource): String? {
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: ReaderException) {
            if (!source.isRotateSupported) return null
            try {
                reader.reset()
                reader.decodeWithState(
                    BinaryBitmap(HybridBinarizer(source.rotateCounterClockwise()))
                ).text
            } catch (_: ReaderException) {
                null
            }
        }
    }

    private fun ImageProxy.toLuminanceSource(): PlanarYUVLuminanceSource? {
        val plane = planes.firstOrNull() ?: return null
        val imageWidth = this.width
        val imageHeight = this.height
        val luma = ByteArray(imageWidth * imageHeight)
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        if (pixelStride == 1 && rowStride == imageWidth && buffer.remaining() >= luma.size) {
            buffer.get(luma, 0, luma.size)
        } else {
            val row = ByteArray(rowStride)
            for (y in 0 until imageHeight) {
                val rowStart = y * rowStride
                if (rowStart >= buffer.limit()) return null
                buffer.position(rowStart)
                val rowLength = minOf(rowStride, buffer.remaining())
                buffer.get(row, 0, rowLength)
                for (x in 0 until imageWidth) {
                    val sourceIndex = x * pixelStride
                    if (sourceIndex >= rowLength) return null
                    luma[y * imageWidth + x] = row[sourceIndex]
                }
            }
        }

        return PlanarYUVLuminanceSource(
            luma,
            imageWidth,
            imageHeight,
            0,
            0,
            imageWidth,
            imageHeight,
            false,
        )
    }
}
