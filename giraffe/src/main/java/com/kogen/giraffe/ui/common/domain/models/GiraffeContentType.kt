package com.kogen.giraffe.ui.common.domain.models

/**
 * How a logged message body should be rendered in the UI, as classified by
 * [com.kogen.giraffe.analizer.GiraffeMessageAnalyzer]. [Unknown] covers both plain unstructured
 * text and unrecognized binary media - [PlainText] isn't currently produced by the analyzer.
 */
internal enum class GiraffeContentType {
    PlainText,
    Json,
    Image,
    Audio,
    Video,
    Pdf,
    Unknown,
}