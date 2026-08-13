package com.kogen.giraffe.analizer.parsers

import android.content.Context

/**
 * Detects one specific kind of embedded binary media (image, audio, video, ...) inside a message's
 * already-extracted binary leaves and, if found, saves it to disk.
 * [com.kogen.giraffe.analizer.GiraffeMessageAnalyzer] scans the message once, then tries each
 * registered parser in turn against that same [leaves] list until one matches - so a message with
 * N parsers doesn't pay for N redundant wire-format scans.
 */
internal interface ContentParser {
    /** Returns a [ParserResult] if this parser recognizes and extracts media from [leaves], or `null` if it doesn't apply. */
    fun parse(leaves: List<ByteArray>, context: Context): ParserResult?
}