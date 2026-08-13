package com.kogen.giraffe.analizer.parsers

import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/**
 * A [ContentParser]'s successful match: the media's [contentType], the [bytes] that were
 * identified as that media (used by the caller to locate and cut them out of the message's text
 * representation), and the [filePath] they were saved to (`null` if the save itself failed).
 */
internal data class ParserResult(
    val contentType: GiraffeContentType,
    val filePath: String?,
    val bytes: ByteArray,
)
