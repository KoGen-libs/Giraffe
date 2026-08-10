package com.kogen.giraffe.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kogen.giraffe.db.converter.GiraffeConverters
import com.kogen.giraffe.db.dao.GiraffeLogDao
import com.kogen.giraffe.db.entity.GiraffeChatEntity
import com.kogen.giraffe.db.entity.GiraffeHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeMessageEntity
import kz.evko.kogen_di.annotations.KoGenBean

/** Room database backing Giraffe's own traffic log (chats, headers, messages) - internal storage, never exposed to the host app. */
@Database(
    entities = [
        GiraffeChatEntity::class,
        GiraffeHeaderEntity::class,
        GiraffeMessageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(GiraffeConverters::class)
abstract class GiraffeDb : RoomDatabase() {
    abstract fun giraffeLogDao(): GiraffeLogDao
}

/** DI factory for [GiraffeDb]; multi-instance invalidation lets multiple processes embedding Giraffe see each other's writes. */
@KoGenBean(true)
internal fun provideDB(context: Context): GiraffeDb = Room.databaseBuilder(
    context.applicationContext,
    GiraffeDb::class.java,
    "giraffe_traffic_logs.db"
).enableMultiInstanceInvalidation()
    .fallbackToDestructiveMigration(true)//TODO: delete to prod release
    .build()