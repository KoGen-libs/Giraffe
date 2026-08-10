package com.kogen.giraffe.analizer.parsers

import android.content.Context
import com.kogen.giraffe.analizer.AnalysisResult

/**
 * Detects one specific kind of embedded binary media (image, audio, video, ...) inside a raw
 * message payload and, if found, saves it to disk. [com.kogen.giraffe.analizer.GiraffeMessageAnalyzer]
 * tries each registered parser in turn until one matches.
 */
interface ContentParser {
    /** Returns a [ParserResult] if this parser recognizes and extracts media from [originalBytes], or `null` if it doesn't apply. */
    fun parse(originalBytes: ByteArray, context: Context): ParserResult?
}