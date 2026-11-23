package com.dragolabs.able.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dragolabs.able.core.database.dao.ActionExecutionDao
import com.dragolabs.able.core.database.dao.AppArchiveDao
import com.dragolabs.able.core.database.dao.BatteryDao
import com.dragolabs.able.core.database.dao.BatteryProfileDao
import com.dragolabs.able.core.database.dao.CustomTileDao
import com.dragolabs.able.core.database.dao.CustomWidgetDao
import com.dragolabs.able.core.database.dao.DnsDao
import com.dragolabs.able.core.database.dao.GpsDao
import com.dragolabs.able.core.database.dao.HomeCardDao
import com.dragolabs.able.core.database.dao.ShortcutDao
import com.dragolabs.able.core.database.entity.ActionExecutionEntity
import com.dragolabs.able.core.database.entity.AppArchiveEntity
import com.dragolabs.able.core.database.entity.BatteryNoteEntity
import com.dragolabs.able.core.database.entity.BatteryProfileEntity
import com.dragolabs.able.core.database.entity.BatterySessionEntity
import com.dragolabs.able.core.database.entity.CustomTileMappingEntity
import com.dragolabs.able.core.database.entity.CustomWidgetConfigEntity
import com.dragolabs.able.core.database.entity.DnsProfileEntity
import com.dragolabs.able.core.database.entity.GpsPointEntity
import com.dragolabs.able.core.database.entity.HomeCardConfigEntity
import com.dragolabs.able.core.database.entity.ShortcutCollectionEntity
import com.dragolabs.able.core.database.entity.ShortcutDefinitionEntity

@Database(
    entities = [
        ActionExecutionEntity::class,
        BatterySessionEntity::class,
        BatteryNoteEntity::class,
        ShortcutDefinitionEntity::class,
        ShortcutCollectionEntity::class,
        HomeCardConfigEntity::class,
        GpsPointEntity::class,
        DnsProfileEntity::class,
        CustomTileMappingEntity::class,
        CustomWidgetConfigEntity::class,
        AppArchiveEntity::class,
        BatteryProfileEntity::class,
    ],
    version = 6,
    exportSchema = true
)
abstract class AbleDatabase : RoomDatabase() {
    abstract fun actionExecutionDao(): ActionExecutionDao
    abstract fun batteryDao(): BatteryDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun homeCardDao(): HomeCardDao
    abstract fun gpsDao(): GpsDao
    abstract fun dnsDao(): DnsDao
    abstract fun customTileDao(): CustomTileDao
    abstract fun customWidgetDao(): CustomWidgetDao
    abstract fun appArchiveDao(): AppArchiveDao
    abstract fun batteryProfileDao(): BatteryProfileDao

    companion object {
        private const val DATABASE_NAME = "able_toolbox.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE battery_sessions ADD COLUMN isPartial INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE battery_sessions ADD COLUMN avgVoltageMillivolts REAL")
                database.execSQL("ALTER TABLE battery_sessions ADD COLUMN avgPowerMilliWatts REAL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE battery_sessions ADD COLUMN screenOnDropPercent INTEGER")
                database.execSQL("ALTER TABLE battery_sessions ADD COLUMN screenOffDropPercent INTEGER")
            }
        }

        /** Adds the Able app archive table (APP-004). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_archive` (`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`action` TEXT NOT NULL, `timestampEpochMs` INTEGER NOT NULL, `priorEnabled` INTEGER NOT NULL, " +
                        "`priorInstalled` INTEGER NOT NULL, `isSystem` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
                )
            }
        }

        /**
         * Adds the user-editable battery profiles table (owner decision 2026-11-22). The five
         * built-in profiles are seeded from the existing DataStore preferences (thresholds, Notte
         * schedule/delay, trigger switches, per-app rules) by `BatteryProfileRepository` on first
         * access, because those preferences live in DataStore and not in this database.
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(CREATE_BATTERY_PROFILES)
            }
        }

        internal const val CREATE_BATTERY_PROFILES =
            "CREATE TABLE IF NOT EXISTS `battery_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`iconKey` TEXT NOT NULL, `colorKey` TEXT NOT NULL, `builtInType` TEXT, `enabled` INTEGER NOT NULL, " +
                "`manualActive` INTEGER NOT NULL, `triggersJson` TEXT NOT NULL, `appRulesJson` TEXT NOT NULL, " +
                "`offerBatterySaver` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"

        @Volatile
        private var INSTANCE: AbleDatabase? = null

        fun getInstance(context: Context): AbleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AbleDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AbleDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
        }
    }
}
