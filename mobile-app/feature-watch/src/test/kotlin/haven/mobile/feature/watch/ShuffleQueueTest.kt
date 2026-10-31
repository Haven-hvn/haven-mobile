package haven.mobile.feature.watch

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShuffleQueueTest {
    private fun album(id: String, tracks: Int) = AlbumTracks(
        albumId = id,
        albumTitle = id,
        filePath = "/music/$id.mp3",
        chapters = (0 until tracks).map { AudioChapter(startMs = it * 1_000L, title = "$id-$it") },
    )

    @Test fun albumShuffleIsPermutationWithEnds() {
        val queue = buildAlbumQueue(album("a", 3), shuffled = true, random = Random(7), durationMs = 3_500L)
        assertEquals(listOf(0, 1, 2), queue.map { it.chapterIndex }.sorted())
        assertEquals(3_500L, queue.maxBy { it.chapterIndex }.endMs)
        assertEquals(1_000L, queue.first { it.chapterIndex == 0 }.endMs)
    }

    @Test fun sequentialAlbumQueueKeepsAuthoredOrder() {
        val queue = buildAlbumQueue(album("a", 3), shuffled = false, random = Random(7), durationMs = null)
        assertEquals(listOf(0, 1, 2), queue.map { it.chapterIndex })
    }

    @Test fun globalShuffleCoversEveryTrackOnce() {
        val queue = buildGlobalQueue(listOf(album("a", 2), album("b", 3)), random = Random(3), durationsMs = mapOf("a" to 2_000L, "b" to 3_000L))
        assertEquals(5, queue.size)
        assertEquals(5, queue.map { it.albumId to it.chapterIndex }.toSet().size)
    }

    @Test fun chapterlessAlbumIsSingleWholeFileTrack() {
        val queue = buildAlbumQueue(album("a", 0), shuffled = true, random = Random(1), durationMs = 9_000L)
        assertEquals(1, queue.size)
        assertEquals(-1, queue[0].chapterIndex)
        assertEquals(9_000L, queue[0].endMs)
    }

    @Test fun reshuffleAvoidsImmediateRepeatWhenPossible() {
        val first = buildGlobalQueue(listOf(album("a", 3)), random = Random(5), durationsMs = emptyMap())
        val second = reshuffled(first, random = Random(5))
        assertTrue(first.last() != second.first())
        assertEquals(first.toSet(), second.toSet())
    }
}
