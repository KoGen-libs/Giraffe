package com.kogen.giraffe.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kogen.giraffe.db.GiraffeDb
import com.kogen.giraffe.db.entity.GiraffeRestCallEntity
import com.kogen.giraffe.db.entity.GiraffeRestHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeRestMessageEntity
import com.kogen.giraffe.db.entity.RestCallWithDetails
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import kotlinx.coroutines.flow.Flow
import kz.evko.kogen_di.annotations.KoGenBean

/** Room DAO for Giraffe's REST traffic log - the HTTP counterpart to [GiraffeLogDao]; call rows cascade-delete their headers/messages (see the entities' foreign keys). */
@Dao
interface GiraffeRestLogDao {
    @Transaction
    @Query("SELECT * FROM giraffe_rest_call ORDER BY timestamp DESC")
    fun getAllRestCallsWithDetails(): Flow<List<RestCallWithDetails>>

    @Transaction
    @Query("SELECT * FROM giraffe_rest_call WHERE callId = :callId")
    fun getRestCallDetailsById(callId: String): Flow<RestCallWithDetails?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRestCall(call: GiraffeRestCallEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRestHeaders(headers: List<GiraffeRestHeaderEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRestMessage(message: GiraffeRestMessageEntity)

    /**
     * Records one finished REST call in a single shot: request and response are already both
     * known by the time an HTTP client interceptor gets to call this (unlike gRPC, which streams
     * request/response messages in over the lifetime of a call), so unlike [GiraffeLogDao] there's
     * no separate "start" step racing a later "message" step - unless the row was deleted between
     * insert and now, this can't hit a FOREIGN KEY failure the way gRPC's could.
     */
    @Transaction
    suspend fun recordRestCall(
        call: GiraffeRestCallEntity,
        headers: List<GiraffeRestHeaderEntity>,
        messages: List<GiraffeRestMessageEntity>,
    ) {
        insertRestCall(call)
        insertRestHeaders(headers)
        messages.forEach { insertRestMessage(it) }
    }

    /** Marks any call left in [activeStatus] (normally [GiraffeChatStatus.InProgress]) as [targetStatus] - run on startup to clean up calls that never reached [recordRestCall] because the process died mid-request. Mirrors [GiraffeLogDao.sanitizeStuckChats]. */
    @Query("UPDATE giraffe_rest_call SET status = :targetStatus WHERE status = :activeStatus")
    suspend fun sanitizeStuckRestCalls(
        activeStatus: GiraffeChatStatus = GiraffeChatStatus.InProgress,
        targetStatus: GiraffeChatStatus = GiraffeChatStatus.Interrupted,
    )

    @Query("SELECT filePath FROM giraffe_rest_messages WHERE callId IN (:callIds) AND filePath IS NOT NULL")
    suspend fun getFilePathsByRestCallIds(callIds: List<String>): List<String>

    @Query("DELETE FROM giraffe_rest_call WHERE callId IN (:callIds)")
    suspend fun deleteRestCallsByIds(callIds: List<String>)
}

/** DI factory exposing [GiraffeDb]'s REST DAO as its own injectable component. */
@KoGenBean(true)
internal fun provideGiraffeRestLogDao(db: GiraffeDb) = db.giraffeRestLogDao()
