package com.example.crewmate.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ScreenshotHelper {

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    suspend fun captureComposableToBitmap(
        context: Context,
        widthPx: Int,
        backgroundColor: Int? = null,
        content: @Composable () -> Unit
    ): Bitmap? {
        val activity = findActivity(context) ?: return null
        val decorView = activity.window?.decorView as? ViewGroup ?: return null

        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            setContent {
                content()
            }
        }

        // Wrapping ComposeView inside a ScrollView ensures Android's Choreographer traversal
        // measures the ComposeView with MeasureSpec.UNSPECIFIED vertically instead of capping
        // it at the screen's FrameLayout height.
        val scrollContainer = android.widget.ScrollView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            visibility = View.INVISIBLE
            isFillViewport = false
            addView(composeView)
        }

        decorView.addView(scrollContainer)
        return try {
            // Allow Compose to execute composition and layout phases with unbounded height
            withFrameNanos { }
            withFrameNanos { }
            delay(150)
            withFrameNanos { }

            val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpec)

            val measuredWidth = composeView.measuredWidth.coerceAtLeast(1)
            val measuredHeight = composeView.measuredHeight.coerceAtLeast(1)

            composeView.layout(0, 0, measuredWidth, measuredHeight)

            val bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (backgroundColor != null) {
                canvas.drawColor(backgroundColor)
            } else {
                canvas.drawColor(Color.TRANSPARENT)
            }
            composeView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotHelper", "Failed to capture composable to bitmap", e)
            null
        } finally {
            decorView.removeView(scrollContainer)
        }
    }

    fun captureViewToBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    suspend fun captureFullScrollableComposeView(
        view: View,
        scrollState: ScrollState
    ): Bitmap {
        val originalScroll = scrollState.value
        val maxScroll = scrollState.maxValue

        if (maxScroll <= 0) {
            return captureViewToBitmap(view)
        }

        val viewportWidth = view.width.coerceAtLeast(1)
        val viewportHeight = view.height.coerceAtLeast(1)
        val totalHeight = viewportHeight + maxScroll

        val fullBitmap = Bitmap.createBitmap(viewportWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(fullBitmap)

        var currentScroll = 0
        while (currentScroll <= maxScroll) {
            scrollState.scrollTo(currentScroll)
            withFrameNanos { }

            val slice = captureViewToBitmap(view)
            fullCanvas.drawBitmap(slice, 0f, currentScroll.toFloat(), null)

            if (currentScroll == maxScroll) break
            currentScroll = (currentScroll + viewportHeight).coerceAtMost(maxScroll)
        }

        scrollState.scrollTo(originalScroll)
        withFrameNanos { }

        return fullBitmap
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, prefix: String = "CrewMate_근무"): Boolean {
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT))
        val filename = "${prefix}_${nowStr}.png"

        return try {
            val outputStream: OutputStream?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CrewMate")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                outputStream = imageUri?.let { resolver.openOutputStream(it) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/CrewMate"
                val fileDir = File(imagesDir)
                if (!fileDir.exists()) fileDir.mkdirs()
                val imageFile = File(fileDir, filename)
                outputStream = FileOutputStream(imageFile)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                it.flush()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotHelper", "Failed to save screenshot to gallery", e)
            false
        }
    }
}
