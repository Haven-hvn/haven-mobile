package haven.mobile.core.cache.mirror

import androidx.room.Entity

/** One parsed ID3 chapter, cached so global shuffle need not re-read every album tag. */
@Entity(tableName = "cached_chapters", primaryKeys = ["albumId", "chapterIndex"])
data class CachedChapterEntity(
    val albumId: String,
    /** -1 is the sentinel row for a chapterless album: parsed, found none. */
    val chapterIndex: Int,
    val startMs: Long,
    val title: String,
    /** Content fingerprint: chapters are trusted while the piece is the same piece. */
    val pieceCid: String,
    val fileSizeBytes: Long,
)

/** Chapter as the cache hands it out, mirror-local so this module needs no UI types. */
data class CachedChapter(val startMs: Long, val title: String)
