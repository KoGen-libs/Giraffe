package com.kogen.giraffe.analizer.utils

/**
 * Parses arbitrary bytes as protobuf wire format without a `.proto` schema, so embedded media
 * (which has no message definition of its own) can still be located inside a logged message.
 */
class ProtoWireScanner {
    companion object {
        private const val MIN_MESSAGE_BYTE_COVERAGE = 0.9
    }

    /** Decodes [data] as a flat sequence of top-level protobuf fields, stopping (without error) at the first byte that doesn't parse as a valid tag/value. */
    fun scan(data: ByteArray): List<ProtoField> {
        val fields = mutableListOf<ProtoField>()
        var pos = 0

        while (pos < data.size) {
            val tagStart = pos
            val (tag, tagLen) = readVariant(data, pos) ?: break
            pos += tagLen

            val fieldNumber = (tag shr 3).toInt()
            when (val wireType = (tag and 0x7).toInt()) {
                0 -> {
                    val (_, len) = readVariant(data, pos) ?: break
                    pos += len
                    fields.add(ProtoField(fieldNumber, wireType, null, tagStart, pos))
                }
                1 -> {
                    if (pos + 8 > data.size) break
                    pos += 8
                    fields.add(ProtoField(fieldNumber, wireType, null, tagStart, pos))
                }
                2 -> {
                    val (len, lenLen) = readVariant(data, pos) ?: break
                    pos += lenLen
                    if (len < 0 || pos + len > data.size) break
                    val payload = data.copyOfRange(pos, pos + len.toInt())
                    pos += len.toInt()
                    fields.add(ProtoField(fieldNumber, wireType, payload, tagStart, pos))
                }
                5 -> {
                    if (pos + 4 > data.size) break
                    pos += 4
                    fields.add(ProtoField(fieldNumber, wireType, null, tagStart, pos))
                }
                else -> break
            }
        }

        return fields
    }

    /**
     * Returns length-delimited fields that are NOT themselves fully-formed nested messages,
     * descending into any depth of oneof/message wrapping to find the true opaque leaf bytes
     * (e.g. the raw content of a `bytes data` field buried inside several wrapper messages).
     * A payload is treated as a nested message (and recursed into) only if scanning it consumes
     * it in full as valid wire-format fields; otherwise it's reported as a leaf candidate.
     */
    fun findBinaryLeaves(data: ByteArray, minSize: Int = 17): List<ByteArray> {
        val leaves = mutableListOf<ByteArray>()
        collectLeaves(data, minSize, leaves)
        return leaves
    }

    /** Recursive worker for [findBinaryLeaves]: scans [data]'s length-delimited fields, recursing into ones that look like real nested messages and collecting the rest into [out]. */
    private fun collectLeaves(data: ByteArray, minSize: Int, out: MutableList<ByteArray>) {
        for (field in scan(data)) {
            val payload = field.bytes ?: continue
            if (field.wireType != 2) continue
            // A minimal, uncompressed PDF can be pure printable ASCII end to end - unlike every
            // other format detected downstream, whose signature or binary payload data reliably
            // fails the text check. Override just this leaf/text decision for it (rather than
            // isLikelyUtf8Text itself, which other callers rely on to mean "not text", full stop -
            // see PcmAudioHeuristics.looksLikePcm16), or such a PDF would never reach a parser.
            val looksLikePdf = MediaSignatures.matchesAt(payload, 0, MediaSignatures.PDF)
            if (MediaSignatures.isLikelyUtf8Text(payload) && !looksLikePdf) continue

            val nested = scan(payload)
            val consumedAll = nested.isNotEmpty() && nested.last().endOffset == payload.size

            // Protobuf's wire format is loose enough that arbitrary binary (e.g. raw audio) can
            // "fully parse" as a sequence of tiny wireType=0/1/5 fields purely by chance — those
            // carry no bytes and get silently dropped, which would otherwise shred most of a real
            // media payload down to whatever small wireType=2 fragment happened to survive. Only
            // trust the nested-message interpretation if real (bytes-carrying) fields account for
            // nearly all of the payload — a genuine nested message barely wastes any bytes on
            // framing, while a false-positive reinterpretation of noise loses most of them.
            val byteFieldCoverage = nested.filter { it.wireType == 2 }
                .sumOf { it.bytes?.size ?: 0 }
            val looksLikeRealMessage = consumedAll &&
                payload.isNotEmpty() &&
                byteFieldCoverage.toDouble() / payload.size >= MIN_MESSAGE_BYTE_COVERAGE

            if (looksLikeRealMessage) {
                // byteFieldCoverage already ruled out "noise that happens to parse as a message"
                // (that's exactly what the >= MIN_MESSAGE_BYTE_COVERAGE check above is for - a
                // run of zero bytes, for instance, parses as all wireType=0 fields with zero
                // byte-carrying fields, so it never reaches here at all). So if recursing into a
                // *confirmed* real nested message finds no binary leaves, that's not data lost -
                // it means every field inside is genuinely text (a very common case: a nested
                // message that's just a few string fields, like a `{lang, message, recommend}`
                // struct) and correctly resolves to zero leaves. Falling back to reporting the
                // whole thing as one opaque "unknown binary" blob here was the actual bug: it
                // took an all-text nested message and reported it as an unrecognized file.
                collectLeaves(payload, minSize, out)
                continue
            }

            if (payload.size >= minSize) {
                out.add(payload)
            }
        }
    }

    /** Decodes a protobuf base-128 varint starting at [start], returning its value and byte length, or `null` if it runs off the end of [data] or exceeds 64 bits. */
    private fun readVariant(data: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var pos = start
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            pos++
            if (b and 0x80 == 0) return Pair(result, pos - start)
            shift += 7
            if (shift > 63) return null
        }
        return null
    }
}

/** One decoded protobuf wire-format field; [bytes] holds the payload for length-delimited (wireType 2) fields, `null` otherwise. */
data class ProtoField(
    val fieldNumber: Int,
    val wireType: Int,
    val bytes: ByteArray?,
    val startOffset: Int,
    val endOffset: Int
)