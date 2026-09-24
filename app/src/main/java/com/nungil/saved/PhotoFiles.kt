package com.nungil.saved

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Photos of saved people and items in the app's private files. Blocking: call on an IO thread. */
object PhotoFiles {
    /** Saves a JPEG in filesDir/[folder]/<uuid>.jpg and returns its absolute path, or null on failure. */
    fun save(context: Context, folder: String, bitmap: Bitmap): String? = try {
        val dir = File(context.filesDir, folder).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        file.absolutePath
    } catch (e: IOException) {
        Log.i("Nungil", "Photo not saved: ${e.message}")
        null
    }

    fun load(path: String?): Bitmap? = path?.let { BitmapFactory.decodeFile(it) }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    private const val JPEG_QUALITY = 90
}
