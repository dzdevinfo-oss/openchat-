package com.openchat.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun processImageToBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return@withContext null

            // Resize if larger than 1024x1024 while maintaining aspect ratio
            val maxDim = 1024
            var width = originalBitmap.width
            var height = originalBitmap.height

            if (width > maxDim || height > maxDim) {
                val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
                width = (width * ratio).toInt()
                height = (height * ratio).toInt()
            }

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
            
            val outputStream = ByteArrayOutputStream()
            // Compress as JPEG
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()

            if (originalBitmap != resizedBitmap) {
                originalBitmap.recycle()
            }
            resizedBitmap.recycle()

            Base64.encodeToString(byteArray, Base64.DEFAULT) // returns the base64 string
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
