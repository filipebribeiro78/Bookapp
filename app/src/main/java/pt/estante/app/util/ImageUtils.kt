package pt.estante.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
    /**
     * An upright JPEG no larger than [maxLongEdge] pixels on its long side. Listings don't need more,
     * and smaller uploads are faster on mobile data. The full-size original stays on the phone.
     */
    fun prepareForUpload(file: File, maxLongEdge: Int = 2000, quality: Int = 88): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longEdge / (sampleSize * 2) >= maxLongEdge) sampleSize *= 2

        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: error("Couldn't read the photo ${file.name}")
        val rotation = ExifInterface(file.path).rotationDegrees
        val scale = min(1f, maxLongEdge.toFloat() / max(decoded.width, decoded.height))
        val output = if (rotation != 0 || scale < 1f) {
            val matrix = Matrix().apply {
                if (rotation != 0) postRotate(rotation.toFloat())
                if (scale < 1f) postScale(scale, scale)
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        val bytes = ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
        if (output !== decoded) output.recycle()
        decoded.recycle()
        return bytes
    }
}
