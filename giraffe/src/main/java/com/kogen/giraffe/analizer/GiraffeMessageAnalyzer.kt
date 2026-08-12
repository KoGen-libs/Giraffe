package com.kogen.giraffe.analizer

import android.content.Context
import android.util.Log
import com.google.protobuf.MessageLite
import com.kogen.giraffe.analizer.parsers.ContentParser
import com.kogen.giraffe.analizer.parsers.GiraffeAudioParser
import com.kogen.giraffe.analizer.parsers.GiraffeImageParser
import com.kogen.giraffe.analizer.parsers.GiraffePdfParser
import com.kogen.giraffe.analizer.parsers.GiraffeUnknownBinaryParser
import com.kogen.giraffe.analizer.parsers.GiraffeVideoParser
import com.kogen.giraffe.analizer.parsers.ParserResult
import com.kogen.giraffe.analizer.utils.ProtoWireScanner
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType
import kz.evko.kogen_di.annotations.KoGenComponent
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_DB_TEXT_LENGTH = 500_000

/**
 * Turns a raw gRPC message (a protobuf [com.google.protobuf.MessageLite] or arbitrary [Any]) into
 * an [AnalysisResult]: a human-readable text form for the log viewer, plus any embedded binary
 * media (image/audio/video/unknown) extracted to a cache file and swapped out of the text for a
 * placeholder, since dumping raw media bytes into the DB/notification text would be both huge and
 * unreadable.
 */
@KoGenComponent(true)
class GiraffeMessageAnalyzer(
    private val context: Context,
) {

    // New instances per call: parsers are stateless and cheap, and this avoids sharing mutable
    // state across concurrent analyze() calls from different in-flight RPCs.
    private val allParsers: List<ContentParser>
        get() = listOf(
            GiraffeImageParser(),
            GiraffeAudioParser(),
            GiraffeVideoParser(),
            GiraffePdfParser(),
            GiraffeUnknownBinaryParser(),
        )


    /**
     * Runs the full analysis pipeline on one message: tries each [ContentParser] in turn to
     * find and extract embedded media, then builds a text representation with that media
     * (if found) cut out and replaced by a placeholder naming its content type.
     */
    fun analyze(message: Any): AnalysisResult {
        val originalBytes =
            (message as? MessageLite)?.toByteArray() ?: message.toString().toByteArray()
        val textRepresentation = transformProtobufStringToValues(message)
        var parsingResult: ParserResult? = null

        // Scanned once and handed to every parser below - each parser used to run this same
        // wire-format scan itself, so a single message paid for it once per registered parser.
        val leaves = ProtoWireScanner().findBinaryLeaves(originalBytes)

        for (parser in allParsers) {
            parser.parse(leaves, context)?.let {
                parsingResult = it
                break
            }
        }


        val trimmedStr = textRepresentation.trim()

        val isJson = ((trimmedStr.startsWith("{") && trimmedStr.endsWith("}")) ||
                (trimmedStr.startsWith("[") && trimmedStr.endsWith("]")))

        val readyText = when {
            isJson && parsingResult != null -> {
                transformProtobufStringToValues(
                    cutMediaFromString(
                        fullString = message.toString(),
                        mediaBytes = parsingResult.bytes,
                        placeholder = parsingResult.contentType.name,
                    )
                )
            }

            isJson -> textRepresentation
            else -> null
        }
//        logBytesAsHex(originalBytes)

        return AnalysisResult(
            contentType = parsingResult?.contentType
                ?: if (isJson) GiraffeContentType.Json else GiraffeContentType.Unknown,
            textContent = truncateForDb(readyText) ?: textRepresentation.take(1000),
            filePath = parsingResult?.filePath,
        )
    }

    /** Debug helper: dumps up to [maxBytes] of [bytes] to logcat as a hex grid, 16 bytes per line. */
    fun logBytesAsHex(bytes: ByteArray, tag: String = ">>> raw_bytes_hex", maxBytes: Int = 512) {
        val sb = StringBuilder()
        val limit = minOf(bytes.size, maxBytes)
        for (i in 0 until limit) {
            sb.append(String.format("%02x", bytes[i]))
            if ((i + 1) % 16 == 0) sb.append("\n") else sb.append(" ")
        }
        Log.d(tag, "size=${bytes.size}\n$sb")
    }

    /** Caps [text] at [maxLength] characters so a pathologically large message body can't bloat the SQLite row. */
    fun truncateForDb(text: String?, maxLength: Int = MAX_DB_TEXT_LENGTH): String? {
        return when {
            text == null -> null
            text.length <= maxLength -> text
            else -> text.substring(0, maxLength)
        }
    }

    /**
     * Reformats protobuf's default `toString()` output (`field: value` lines) into pretty-printed
     * JSON, so the log viewer can render structured messages consistently whether they arrived as
     * protobuf or already as JSON. Falls back to the original text untouched if no line looks like
     * a `key: value` pair.
     */
    private fun transformProtobufStringToValues(message: Any): String {
        val text = message.toString()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        if (lines.none { it.contains(":") }) return text

        val jsonObject = JSONObject()

        for (line in lines) {
            if (line.startsWith("#")) continue

            val colonIndex = line.indexOf(":")
            if (colonIndex == -1) continue

            val key = line.substring(0, colonIndex).trim()
            var value = line.substring(colonIndex + 1).trim()

            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.removeSurrounding("\"")
            }

            if (value.contains("\\\"")) {
                value = value.replace("\\\"", "\"")
            }

            val decoded = tryDecodeAsText(value)
            if (decoded != null) {
                value = decoded
            }

            try {
                when {
                    value.startsWith("{") && value.endsWith("}") -> {
                        jsonObject.put(key, JSONObject(value))
                    }

                    value.startsWith("[") && value.endsWith("]") -> {
                        jsonObject.put(key, JSONArray(value))
                    }

                    else -> {
                        jsonObject.put(key, value)
                    }
                }
            } catch (_: Exception) {
                jsonObject.put(key, value)
            }
        }

        return jsonObject.toString(2)
    }

    /**
     * Reverses protobuf's `toString()` escaping of a `bytes` field's value (`\n`, `\"`, octal
     * escapes like `\307`, etc.) back into raw bytes, so escaped binary content can be inspected
     * or decoded as text.
     */
    fun unescapeProtobufString(input: String): ByteArray {
        val result = mutableListOf<Byte>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                when (input[i + 1]) {
                    'n' -> {
                        result.add(0x0A); i += 2
                    }

                    'r' -> {
                        result.add(0x0D); i += 2
                    }

                    't' -> {
                        result.add(0x09); i += 2
                    }

                    '"' -> {
                        result.add(0x22); i += 2
                    }

                    '\'' -> {
                        result.add(0x27); i += 2
                    }

                    '\\' -> {
                        result.add(0x5C); i += 2
                    }

                    in '0'..'7' -> {
                        // до 3 восьмеричных цифр
                        var j = i + 1
                        var octal = ""
                        while (j < input.length && octal.length < 3 && input[j] in '0'..'7') {
                            octal += input[j]
                            j++
                        }
                        result.add(octal.toInt(8).toByte())
                        i = j
                    }

                    else -> {
                        result.add(c.code.toByte()); i++
                    }
                }
            } else {
                result.add(c.code.toByte())
                i++
            }
        }
        return result.toByteArray()
    }

    /**
     * Unescapes [escapedValue] and returns it as UTF-8 text only if that round-trips exactly back
     * to the original bytes - i.e. only if it's genuinely decodable text, not binary that happens
     * to produce *some* string when force-decoded.
     */
    fun tryDecodeAsText(escapedValue: String): String? {
        val bytes = unescapeProtobufString(escapedValue)
        return try {
            val decoded = String(bytes, Charsets.UTF_8)
            val reEncoded = decoded.toByteArray(Charsets.UTF_8)
            if (reEncoded.contentEquals(bytes)) decoded else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encodes [bytes] the same way protobuf's `toString()` escapes a `bytes` field, so extracted
     * media bytes can be located as a substring inside the original protobuf text (see
     * [cutMediaFromString]).
     */
    fun escapeLikeProtobuf(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            when (val v = b.toInt() and 0xFF) {
                0x07 -> sb.append("\\a")
                0x08 -> sb.append("\\b")
                0x0A -> sb.append("\\n")
                0x0B -> sb.append("\\v")
                0x0C -> sb.append("\\f")
                0x0D -> sb.append("\\r")
                0x09 -> sb.append("\\t")
                0x22 -> sb.append("\\\"")
                0x27 -> sb.append("\\'")
                0x5C -> sb.append("\\\\")
                else -> if (v in 0x20..0x7E) {
                    sb.append(v.toChar())
                } else {
                    sb.append('\\')
                    sb.append(String.format("%03o", v))
                }
            }
        }
        return sb.toString()
    }

    /**
     * Replaces the substring of [fullString] that corresponds to [mediaBytes] with [placeholder],
     * so the pretty-printed text shown in the log/notification doesn't contain the raw
     * (escaped, potentially huge) media payload. Locates the span by matching just the first/last
     * [edgeSize] bytes' escaped form rather than escaping and searching for the full payload,
     * which would be far more expensive for large media. Returns [fullString] unchanged if the
     * span can't be found (e.g. the byte count is too small to identify a unique start/end).
     */
    fun cutMediaFromString(
        fullString: String,
        mediaBytes: ByteArray,
        placeholder: String,
        edgeSize: Int = 4
    ): String {
        if (mediaBytes.size < edgeSize * 2) {
            return fullString
        }

        val startBytes = mediaBytes.copyOfRange(0, edgeSize)
        val endBytes = mediaBytes.copyOfRange(mediaBytes.size - edgeSize, mediaBytes.size)

        val startEscaped = escapeLikeProtobuf(startBytes)
        val endEscaped = escapeLikeProtobuf(endBytes)

        val startIdx = fullString.indexOf(startEscaped)
        if (startIdx == -1) {
            return fullString
        }

        val endIdx = fullString.lastIndexOf(endEscaped)
        if (endIdx == -1 || endIdx < startIdx) {
            return fullString
        }

        val cutTo = endIdx + endEscaped.length

        return fullString.substring(0, startIdx) + placeholder + fullString.substring(cutTo)
    }
}