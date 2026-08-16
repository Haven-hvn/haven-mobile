package haven.mobile.feature.watch

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

/**
 * Paginated PDF rendering, one page at a time.
 *
 * `PdfRenderer` needs a seekable file descriptor, which the staged plaintext file already is — so
 * this opens it directly. (An earlier revision decrypted to memory and then had to write a temp file
 * and immediately unlink it to keep plaintext off disk; staging removed the need for that trick.)
 *
 * Pages are rendered on demand and never all at once. A 300-page document at 1080px wide would be
 * ~1.3GB of bitmaps if materialised eagerly; here the cost is one page, and the caller recycles as
 * it scrolls.
 */
internal class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    val pageCount: Int get() = renderer.pageCount

    /**
     * Renders one page scaled to [targetWidthPx].
     *
     * Not thread-safe by design: `PdfRenderer` allows only one open page at a time, so callers must
     * serialise. The viewer does this by rendering from a single coroutine.
     */
    fun renderPage(index: Int, targetWidthPx: Int): Bitmap? {
        if (index < 0 || index >= renderer.pageCount) return null
        return runCatching {
            renderer.openPage(index).use { page ->
                val scale = targetWidthPx.toFloat() / page.width.toFloat()
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                // RGB_565 halves the bitmap footprint versus ARGB_8888 and documents have no alpha.
                // On a low-end device this is the difference between scrolling and thrashing.
                val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.RGB_565)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }.getOrNull()
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(file: File): Result<PdfDocument> = runCatching {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                PdfDocument(descriptor, PdfRenderer(descriptor))
            } catch (e: Throwable) {
                runCatching { descriptor.close() }
                throw e
            }
        }
    }
}
