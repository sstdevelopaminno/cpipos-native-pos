package com.cpipos.pos.next.core.webpos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Encode display + thumbnail as real JPEG bytes within CpIPOS product-media quotas. */
internal data class NativeEncodedCatalogPhoto(
    val display: ByteArray,
    val displayWidth: Int,
    val displayHeight: Int,
    val thumbnail: ByteArray,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int
)

internal object NativeCatalogPhotoEncoder {
    private const val MAX_SOURCE_EDGE = 2048
    private const val DISPLAY_EDGE = 1200
    private const val THUMB_EDGE = 400
    private const val DISPLAY_LIMIT = 1_572_864
    private const val THUMB_LIMIT = 524_288

    fun encode(context: Context, uri: Uri): NativeEncodedCatalogPhoto {
        val resolver = context.contentResolver
        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, info) }
            ?: throw IllegalArgumentException("product_photo_unreadable")
        require(info.outWidth in 1..20_000 && info.outHeight in 1..20_000) {
            "product_photo_invalid_dimensions"
        }
        var scale = 1
        while (max(info.outWidth, info.outHeight) / scale > MAX_SOURCE_EDGE) scale *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = scale
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val original = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("product_photo_decode_failed")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
        }
        val oriented = if (matrix.isIdentity) original else
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        if (oriented !== original) original.recycle()
        try {
            val display = shrink(oriented, DISPLAY_EDGE)
            val thumbnail = shrink(oriented, THUMB_EDGE)
            try {
                return NativeEncodedCatalogPhoto(
                    display = jpeg(display, DISPLAY_LIMIT),
                    displayWidth = display.width,
                    displayHeight = display.height,
                    thumbnail = jpeg(thumbnail, THUMB_LIMIT),
                    thumbnailWidth = thumbnail.width,
                    thumbnailHeight = thumbnail.height
                )
            } finally {
                if (display !== oriented) display.recycle()
                if (thumbnail !== oriented && thumbnail !== display) thumbnail.recycle()
            }
        } finally {
            oriented.recycle()
        }
    }

    private fun shrink(bitmap: Bitmap, longestEdge: Int): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= longestEdge) return bitmap
        val width = max(1, (bitmap.width.toLong() * longestEdge / edge).toInt())
        val height = max(1, (bitmap.height.toLong() * longestEdge / edge).toInt())
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun jpeg(bitmap: Bitmap, maxBytes: Int): ByteArray {
        for (quality in listOf(85, 75, 65, 55, 45)) {
            val out = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) && out.size() <= maxBytes) {
                return out.toByteArray()
            }
        }
        throw IllegalArgumentException("product_photo_too_large")
    }
}
