package com.kogen.giraffe.ui.features.restCallList.data.service

import com.kogen.giraffe.db.dao.GiraffeRestLogDao
import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.common.domain.models.toDomain
import com.kogen.giraffe.ui.features.restCallList.domain.service.RestCallListService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.evko.kogen_di.annotations.KoGenComponent
import java.io.File

@KoGenComponent(true)
internal class RestCallListServiceImpl(
    val dao: GiraffeRestLogDao,
) : RestCallListService {
    override suspend fun loadRestCallList(): Flow<List<GiraffeRestCall>> {
        return dao.getAllRestCallsWithDetails().map {
            it.map { details ->
                details.toDomain()
            }
        }
    }

    /** Deletes the given calls' DB rows and best-effort cleans up their cached media files - a missing/already-deleted file is not treated as an error. */
    override suspend fun deleteRestCalls(callIds: List<String>) {
        val filePaths = dao.getFilePathsByRestCallIds(callIds)
        dao.deleteRestCallsByIds(callIds)
        try {
            filePaths.forEach { path ->
                File(path).delete()
            }
        } catch (_: Exception) {
        }
    }
}
