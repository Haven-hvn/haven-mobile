package haven.mobile.feature.watch

import kotlin.random.Random

/** An album's tracks as parsed chapters, before a play queue is built from them. */
data class AlbumTracks(
    val albumId: String,
    val albumTitle: String,
    val filePath: String,
    val chapters: List<AudioChapter>,
)

/** One playable track: a chapter clipped out of its album file. Chapterless albums are one whole-file track. */
data class QueueTrack(
    val albumId: String,
    val albumTitle: String,
    val filePath: String,
    /** -1 for a chapterless album (whole file). */
    val chapterIndex: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long?,
)

fun buildAlbumQueue(
    album: AlbumTracks,
    shuffled: Boolean,
    random: Random = Random.Default,
    durationMs: Long? = null,
): List<QueueTrack> {
    val tracks = tracksOf(album, durationMs)
    return if (shuffled) tracks.shuffled(random) else tracks
}

/** Every track of every album exactly once, in random order. */
fun buildGlobalQueue(
    albums: List<AlbumTracks>,
    random: Random = Random.Default,
    durationsMs: Map<String, Long?> = emptyMap(),
): List<QueueTrack> = albums.flatMap { tracksOf(it, durationsMs[it.albumId]) }.shuffled(random)

/** Fresh permutation of an existing queue, never starting with the track that just finished the old one. */
fun reshuffled(queue: List<QueueTrack>, random: Random = Random.Default): List<QueueTrack> {
    if (queue.size < 2) return queue
    var next = queue.shuffled(random)
    while (next.first() == queue.last()) next = queue.shuffled(random)
    return next
}

private fun tracksOf(album: AlbumTracks, durationMs: Long?): List<QueueTrack> {
    if (album.chapters.isEmpty()) {
        return listOf(
            QueueTrack(album.albumId, album.albumTitle, album.filePath, -1, album.albumTitle, 0L, durationMs),
        )
    }
    val ordered = album.chapters.sortedBy { it.startMs }
    return ordered.mapIndexed { index, chapter ->
        QueueTrack(
            albumId = album.albumId,
            albumTitle = album.albumTitle,
            filePath = album.filePath,
            chapterIndex = album.chapters.indexOf(chapter),
            title = chapter.title,
            startMs = chapter.startMs,
            endMs = ordered.getOrNull(index + 1)?.startMs ?: durationMs,
        )
    }
}
