package com.xxx.carelorie.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Takes or picks a food photo and hands back base64 JPEG, ready for the recogniser.
 *
 * [takePhoto] opens the camera, [pickPhoto] the gallery. Neither needs a runtime permission: the
 * camera writes through a FileProvider Uri we own, and the photo picker returns a single item the
 * user explicitly chose.
 */
class FoodPhotoCapture(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit
)

/**
 * Wires up both launchers.
 *
 * [onImage] receives base64 JPEG. [onError] fires when the user's pick could not be read — a
 * cancelled capture is silent, because cancelling is not an error.
 */
@Composable
fun rememberFoodPhotoCapture(
    onImage: (String) -> Unit,
    onError: (String) -> Unit = {}
): FoodPhotoCapture {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Held across recomposition so the callback can still find the file the camera wrote to.
    val pendingPhoto = remember { arrayOfNulls<Uri>(1) }

    fun decodeInBackground(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val encoded = withContext(Dispatchers.IO) { encodeForUpload(context, uri) }
            if (encoded == null) onError("That image could not be read. Try another one.")
            else onImage(encoded)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingPhoto[0]
        pendingPhoto[0] = null
        if (saved) decodeInBackground(uri) else uri?.let { deleteTempFile(context, it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> decodeInBackground(uri) }

    return remember {
        FoodPhotoCapture(
            takePhoto = {
                val uri = newPhotoUri(context)
                if (uri == null) {
                    onError("Could not open the camera.")
                } else {
                    pendingPhoto[0] = uri
                    cameraLauncher.launch(uri)
                }
            },
            pickPhoto = {
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
        )
    }
}

private const val TAG = "FoodPhotoCapture"

/** Longest edge sent to the model. Beyond this, detail stops helping and upload time grows. */
private const val MAX_EDGE = 1024

/** JPEG quality. High enough to keep a dish recognisable, low enough to upload on mobile data. */
private const val JPEG_QUALITY = 85

private fun newPhotoUri(context: Context): Uri? = try {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File.createTempFile("food_", ".jpg", dir)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
} catch (e: Exception) {
    Log.e(TAG, "Could not create a file for the camera", e)
    null
}

private fun deleteTempFile(context: Context, uri: Uri) {
    runCatching { context.contentResolver.delete(uri, null, null) }
}

/**
 * Reads [uri], scales it down and returns base64 JPEG.
 *
 * Downscaling happens during decode via [BitmapFactory.Options.inSampleSize] rather than after,
 * so a 12-megapixel camera photo never has to fit in memory at full size.
 */
private fun encodeForUpload(context: Context, uri: Uri): String? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
    }
    val decoded = context.contentResolver.openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it, null, options) }

    if (decoded == null) {
        null
    } else {
        // Phone cameras record orientation in EXIF rather than rotating the pixels, so a photo
        // taken in portrait arrives on its side. A sideways plate is measurably harder to read.
        // android.media.ExifInterface reads from a stream on API 24+, and minSdk here is 28,
        // so this needs no extra dependency.
        val upright = applyExifRotation(context, uri, decoded)
        val output = ByteArrayOutputStream()
        upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        upright.recycle()
        if (upright !== decoded) decoded.recycle()
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
} catch (e: Exception) {
    Log.e(TAG, "Could not encode the image", e)
    null
} finally {
    // The capture file has served its purpose. Gallery picks are not ours to delete, and the
    // delete simply fails for them.
    if (uri.authority?.endsWith(".fileprovider") == true) deleteTempFile(context, uri)
}

private fun sampleSizeFor(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2
    return sample
}

private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val degrees = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (e: Exception) {
        0f
    }

    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
