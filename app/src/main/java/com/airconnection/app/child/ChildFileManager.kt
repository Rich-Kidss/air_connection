package com.airconnection.app.child

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Base64
import java.io.ByteArrayOutputStream

object ChildFileManager {

    fun scanRecentPhotos(context: Context, maxCount: Int = 12): List<String> {
        val photosList = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                while (it.moveToNext() && count < maxCount) {
                    val id = it.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    try {
                        val inputStream = context.contentResolver.openInputStream(contentUri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()

                        if (bitmap != null) {
                            val scaled = Bitmap.createScaledBitmap(bitmap, 300, 300, true)
                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                            val base64Str = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            photosList.add(base64Str)
                            bitmap.recycle()
                            scaled.recycle()
                            count++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return photosList
    }
}
