package haven.mobile.core.design.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.domain.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decode width for row/bar thumbnails: a 48dp box at xxxhdpi is ~192px; 144 samples cleanly. */
const val ARTWORK_THUMB_TARGET_PX = 144

/** Decode width for the 16:9 grid-card plate, which renders much larger than a row glyph. */
const val ARTWORK_CARD_TARGET_PX = 512

/**
 * Row leading visual: the item's extracted cover art when its slot holds a decodable file,
 * else the kind glyph. Existence and decode run off-main per composition — callers pass the
 * slot file unconditionally and never gate on it.
 */
@Composable
fun ArtworkImage(
    file: File?,
    kind: MediaKind,
    modifier: Modifier = Modifier,
    size: Dp = HavenSpacing.glyph,
    targetPx: Int = ARTWORK_THUMB_TARGET_PX,
) {
    val art = rememberDecodedArtwork(file, targetPx)
    if (art == null) {
        MediaKindGlyph(kind = kind, modifier = modifier, size = size)
    } else {
        Image(
            bitmap = art.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(MaterialTheme.shapes.small),
        )
    }
}

/**
 * Decoded cover art for [file], or null when the slot is empty or undecodable. Off-main by
 * construction — BitmapFactory on the composition thread would jank every scroll frame.
 */
@Composable
fun rememberDecodedArtwork(file: File?, targetPx: Int): Bitmap? {
    val art by produceState<Bitmap?>(initialValue = null, file, targetPx) {
        value = withContext(Dispatchers.IO) { decodeArtworkFile(file, targetPx) }
    }
    return art
}

/** Two-pass file decode: measure, pick a power-of-two sample size, then decode at that size. */
fun decodeArtworkFile(file: File?, targetPx: Int): Bitmap? {
    if (file == null || !file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, targetPx)
    }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
}

/** Same two passes over bytes (live-player artwork), for callers that never touch disk. */
fun decodeArtworkBytes(bytes: ByteArray, targetPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, targetPx)
    }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
}

/**
 * Largest power-of-two downsample keeping the width at or above [targetPx].
 * Pure so the memory math stays unit-tested without a device.
 */
fun sampleSizeFor(sourceWidthPx: Int, targetPx: Int): Int {
    if (sourceWidthPx <= 0 || targetPx <= 0) return 1
    var sample = 1
    while (sourceWidthPx / (sample * 2) >= targetPx) {
        sample *= 2
    }
    return sample
}
