package com.kogen.giraffe.analizer

import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/**
 * Outcome of [GiraffeMessageAnalyzer.analyze]-ing one gRPC message: what kind of content it
 * carries, its text representation (with any embedded media already replaced by a placeholder),
 * and the file path where extracted media (if any) was cached to disk.
 */
internal data class AnalysisResult(
    val contentType: GiraffeContentType,
    val textContent: String?,
    val filePath: String?,
)
