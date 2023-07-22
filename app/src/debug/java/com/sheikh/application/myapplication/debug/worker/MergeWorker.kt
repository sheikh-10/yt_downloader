package com.sheikh.application.myapplication.debug.worker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chaquo.python.Python
import com.sheikh.application.myapplication.R
import com.sheikh.application.myapplication.debug.utility.Mp4ParserAudioMuxer
import com.sheikh.application.myapplication.debug.utility.makeStatusNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class MergeWorker(ctx: Context, params: WorkerParameters): CoroutineWorker(ctx, params){

    companion object {
        private const val TAG = "MergeWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting file merging")

        makeStatusNotification(
            message = "Merging files",
            context = applicationContext)

        return withContext(Dispatchers.IO) {
            return@withContext try {
                val mp4ParserAudioMuxer = Mp4ParserAudioMuxer(applicationContext)
                mp4ParserAudioMuxer.startMerging()
                saveMediaToStorage(
                    filePath = File(applicationContext.filesDir, "/output/output.mp4").path,
                    isVideo = true,
                    fileName = "output_${UUID.randomUUID()}.mp4"
                )
                makeStatusNotification("Video saved successfully", applicationContext)
                Result.success()
            }catch (throwable: Throwable) {
                makeStatusNotification("Merging failed", applicationContext)
                Log.e(TAG, "Merge failed", throwable)
                Result.failure()
            }
        }
    }

    private fun saveMediaToStorage(filePath: String?, isVideo: Boolean, fileName: String) {
        filePath?.let {
            val values = ContentValues().apply {
                val folderName = if (isVideo) {
                    Environment.DIRECTORY_MOVIES
                } else {
                    Environment.DIRECTORY_PICTURES
                }
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    folderName + "/${applicationContext.getString(R.string.app_name)}/"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val fileUri = applicationContext.contentResolver.insert(collection, values)

            fileUri?.let {
                if (isVideo) {
                    applicationContext.contentResolver.openFileDescriptor(fileUri, "w").use { descriptor ->
                        descriptor?.let {
                            FileOutputStream(descriptor.fileDescriptor).use { out ->
                                val videoFile = File(filePath)
                                FileInputStream(videoFile).use { inputStream ->
                                    val buf = ByteArray(8192)
                                    while (true) {
                                        val sz = inputStream.read(buf)
                                        if (sz <= 0) break
                                        out.write(buf, 0, sz)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    applicationContext.contentResolver.openOutputStream(fileUri).use { out ->
                        val bmOptions = BitmapFactory.Options()
                        val bmp = BitmapFactory.decodeFile(filePath, bmOptions)
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        bmp.recycle()
                    }
                }
                values.clear()
                values.put(
                    if (isVideo) MediaStore.Video.Media.IS_PENDING else MediaStore.Images.Media.IS_PENDING,
                    0
                )
                applicationContext.contentResolver.update(fileUri, values, null, null)
            }
        }
    }
}