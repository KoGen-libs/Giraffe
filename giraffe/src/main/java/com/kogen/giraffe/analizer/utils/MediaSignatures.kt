package com.kogen.giraffe.analizer.utils

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Magic-byte signatures and byte-scanning helpers used by the content parsers to identify and bound embedded media inside a message. */
internal object MediaSignatures {
    val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    val PNG = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    val GIF = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte())
    val WEBP =
        byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
    val WEBP_TAG = byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte())

    val WAV =
        byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
    val WAVE_TAG = byteArrayOf(0x57.toByte(), 0x41.toByte(), 0x56.toByte(), 0x45.toByte())

    val MP4_FTYP = byteArrayOf(0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte())
    private val THREE_GP = byteArrayOf(0x33.toByte(), 0x67.toByte(), 0x70.toByte())

    // "%PDF-" / "%%EOF"
    val PDF = byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte(), 0x2D.toByte())
    val PDF_EOF = byteArrayOf(0x25.toByte(), 0x25.toByte(), 0x45.toByte(), 0x4F.toByte(), 0x46.toByte())

    /** Decodes a (optionally `data:...;base64,`-prefixed) base64 string, or `null` if it isn't valid base64. */
    fun tryDecodeBase64(str: String): ByteArray? {
        val cleaned = str.substringAfter("base64,").trim()
        if (cleaned.length < 8 || cleaned.length % 4 != 0) return null
        return try {
            Base64.decode(cleaned, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Checks whether [bytes] round-trip cleanly through UTF-8 decode/re-encode and contain no
     * control characters below tab - used to rule out treating genuine text payloads as binary
     * media leaves. Deliberately generic - callers with a more specific reason to override this
     * (see [ProtoWireScanner.collectLeaves][com.kogen.giraffe.analizer.utils.ProtoWireScanner]
     * special-casing PDF) should layer that on top rather than baking it in here, since this
     * function is also relied on elsewhere (e.g. [PcmAudioHeuristics.looksLikePcm16]) to mean
     * exactly "not text", full stop.
     */
    fun isLikelyUtf8Text(bytes: ByteArray): Boolean {
        return try {
            val decoded = String(bytes, Charsets.UTF_8)
            val reEncoded = decoded.toByteArray(Charsets.UTF_8)
            reEncoded.contentEquals(bytes) &&
                    decoded.none { it.code < 0x09 }
        } catch (_: Exception) {
            false
        }
    }

    val PNG_END = byteArrayOf(
        0x49.toByte(),
        0x45.toByte(),
        0x4E.toByte(),
        0x44.toByte(),
        0xAE.toByte(),
        0x42.toByte(),
        0x60.toByte(),
        0x82.toByte()
    )
    val JPEG_END = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    val GIF_END = byteArrayOf(0x3B.toByte())

    /** Returns the offset just past the first occurrence of [signatureEnd] at or after [start], or `-1` if not found. */
    fun findEndOfMedia(bytes: ByteArray, start: Int, signatureEnd: ByteArray): Int {
        for (i in start until bytes.size - signatureEnd.size) {
            var match = true
            for (j in signatureEnd.indices) {
                if (bytes[i + j] != signatureEnd[j]) {
                    match = false
                    break
                }
            }
            if (match) return i + signatureEnd.size
        }
        return -1
    }

    /** Checks the signature at an exact offset — used to confirm a leaf *starts with* a
     * container's magic bytes, instead of scanning for a coincidental match anywhere inside it. */
    fun matchesAt(bytes: ByteArray, offset: Int, signature: ByteArray): Boolean {
        if (offset < 0 || offset + signature.size > bytes.size) return false
        for (i in signature.indices) {
            if (bytes[offset + i] != signature[i]) return false
        }
        return true
    }

    /** Returns the offset of the first occurrence of [signature] in [bytes] at or after [from], or `-1` if not found. */
    fun indexOf(bytes: ByteArray, signature: ByteArray, from: Int = 0): Int {
        for (i in from..bytes.size - signature.size) {
            var match = true
            for (j in signature.indices) {
                if (bytes[i + j] != signature[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    /** Reads a RIFF container's little-endian chunk-size field at [riffStart] and returns the offset just past it, or `-1` if that would run past the end of [bytes]. */
    fun findRiffEnd(bytes: ByteArray, riffStart: Int): Int {
        if (riffStart + 8 > bytes.size) return -1
        val chunkSize = (bytes[riffStart + 4].toInt() and 0xFF) or
                ((bytes[riffStart + 5].toInt() and 0xFF) shl 8) or
                ((bytes[riffStart + 6].toInt() and 0xFF) shl 16) or
                ((bytes[riffStart + 7].toInt() and 0xFF) shl 24)
        val end = riffStart + 8 + chunkSize
        return if (end in (riffStart + 8)..bytes.size) end else -1
    }

    /**
     * Walks MP4's top-level box structure (each box: 4-byte big-endian size + 4-byte type) forward
     * from the `ftyp` box preceding [ftypIndex] until the boxes stop covering the buffer, and
     * returns the offset just past the last well-formed box - i.e. the end of the MP4 container.
     */
    fun findMp4End(bytes: ByteArray, ftypIndex: Int): Int {
        var pos = ftypIndex - 4
        if (pos < 0) return -1

        while (pos + 8 <= bytes.size) {
            val boxSize = ((bytes[pos].toInt() and 0xFF) shl 24) or
                    ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 3].toInt() and 0xFF)
            if (boxSize < 8) break

            val next = pos + boxSize
            if (next <= pos || next > bytes.size) break
            pos = next
        }

        return if (pos > ftypIndex) pos else -1
    }

    /**
     * Like [findEndOfMedia] but returns the offset past the *last* occurrence of [signatureEnd] -
     * used for JPEG, whose EOI marker (`FF D9`) can also appear inside embedded thumbnail/EXIF
     * data before the real end of the file.
     */
    fun findLastEndOfMedia(bytes: ByteArray, start: Int, signatureEnd: ByteArray): Int {
        var result = -1
        var from = start
        while (true) {
            val idx = indexOf(bytes, signatureEnd, from)
            if (idx == -1) break
            result = idx + signatureEnd.size
            from = idx + 1
        }
        return result
    }
}

/** Writes [bytes] to a uniquely-named file under `cacheDir/giraffe_media`, returning its absolute path, or `null` on I/O failure. */
fun saveMediaToCache(
    context: Context,
    bytes: ByteArray,
    prefix: String,
    extension: String,
): String? {
    return try {
        val folder = File(context.cacheDir, "giraffe_media").apply { mkdirs() }
        val file = File(folder, "${prefix}_${UUID.randomUUID()}.$extension")
        FileOutputStream(file).use { it.write(bytes) }


        file.absolutePath
    } catch (_: Exception) {
        null
    }
}