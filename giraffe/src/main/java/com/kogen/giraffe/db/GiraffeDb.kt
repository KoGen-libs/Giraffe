package com.kogen.giraffe.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kogen.giraffe.db.converter.GiraffeConverters
import com.kogen.giraffe.db.dao.GiraffeLogDao
import com.kogen.giraffe.db.dao.GiraffeRestLogDao
import com.kogen.giraffe.db.entity.GiraffeChatEntity
import com.kogen.giraffe.db.entity.GiraffeHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeMessageEntity
import com.kogen.giraffe.db.entity.GiraffeRestCallEntity
import com.kogen.giraffe.db.entity.GiraffeRestHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeRestMessageEntity
import kz.evko.kogen_di.annotations.KoGenBean

/** Room database backing Giraffe's own traffic log (gRPC chats and REST calls, each with their headers/messages) - internal storage, never exposed to the host app. */
@Database(
    entities = [
        GiraffeChatEntity::class,
        GiraffeHeaderEntity::class,
        GiraffeMessageEntity::class,
        GiraffeRestCallEntity::class,
        GiraffeRestHeaderEntity::class,
        GiraffeRestMessageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(GiraffeConverters::class)
abstract class GiraffeDb : RoomDatabase() {
    abstract fun giraffeLogDao(): GiraffeLogDao
    abstract fun giraffeRestLogDao(): GiraffeRestLogDao
}

/**
 * Adds the REST tables (`giraffe_rest_call`/`giraffe_rest_headers`/`giraffe_rest_messages`)
 * introduced alongside the gRPC ones, leaving all existing data untouched. `exportSchema` is
 * `false` for this database (no committed schema-history JSON to run this against
 * [androidx.room.testing.MigrationTestHelper]), so the exact column/FK/index syntax below was
 * copied from the KSP-generated `GiraffeDb_Impl` for the version-1 tables rather than verified
 * against a real migration test - double-check it against a fresh KSP output if Room's generated
 * SQL conventions ever change.
 */
private val MIGRATION_1_2 = Migration(1, 2) { db: SupportSQLiteDatabase ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `giraffe_rest_call` (`callId` TEXT NOT NULL, `url` TEXT NOT NULL, `httpMethod` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `httpStatusCode` INTEGER, PRIMARY KEY(`callId`))"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `giraffe_rest_headers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `callId` TEXT NOT NULL, `isResponse` INTEGER NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL, FOREIGN KEY(`callId`) REFERENCES `giraffe_rest_call`(`callId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_giraffe_rest_headers_callId` ON `giraffe_rest_headers` (`callId`)")
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `giraffe_rest_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `callId` TEXT NOT NULL, `isIncoming` INTEGER NOT NULL, `contentType` TEXT NOT NULL, `textContent` TEXT, `filePath` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`callId`) REFERENCES `giraffe_rest_call`(`callId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_giraffe_rest_messages_callId` ON `giraffe_rest_messages` (`callId`)")
}

/** DI factory for [GiraffeDb]; multi-instance invalidation lets multiple processes embedding Giraffe see each other's writes. */
@KoGenBean(true)
internal fun provideDB(context: Context): GiraffeDb = Room.databaseBuilder(
    context.applicationContext,
    GiraffeDb::class.java,
    "giraffe_traffic_logs.db"
).enableMultiInstanceInvalidation()
    .addMigrations(MIGRATION_1_2)
    // Kept as a safety net for anything MIGRATION_1_2 doesn't cover (e.g. a pre-1.0 install that
    // somehow predates version 1) rather than removed outright - see the TODO this used to carry
    // above. A destructive fallback silently wiping a user's local traffic log is still not
    // something to ship long-term, but it's no longer the *only* path for this specific bump now
    // that a real migration exists for it.
    .fallbackToDestructiveMigration(true)
    .build()
