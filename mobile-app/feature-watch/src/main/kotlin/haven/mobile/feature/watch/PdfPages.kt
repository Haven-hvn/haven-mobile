/*
 * Superseded by PdfDocument.kt.
 *
 * This file held a renderer that took the whole decrypted document as a `ByteArray`, wrote it to a
 * temp file, unlinked the file to keep plaintext off disk, then rendered every page up front (capped
 * at 40) into ARGB_8888 bitmaps.
 *
 * Two problems, both fatal on a low-end device:
 *   - it required the entire document resident in memory before rendering could start;
 *   - eagerly rendering N pages at 1080px wide is ~4MB of bitmap per page.
 *
 * Decrypted content is now staged to a file by `PlaintextSpool`, so `PdfDocument` opens that file
 * directly and renders one page at a time in RGB_565. No temp copy, no unlink trick, no page cap.
 *
 * Safe to delete after review.
 */
package haven.mobile.feature.watch
