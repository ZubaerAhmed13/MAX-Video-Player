package com.zubaer.maxvideoplayer.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step9DatabaseMigrationTest {
    @Test
    fun migration7To8PreservesExistingRowsAndAddsOpaquePrivateIndexOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "step9-migration-7-8.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `cloud_accounts` (`id` TEXT NOT NULL, `provider` TEXT NOT NULL, `providerAccountId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `emailHint` TEXT, `authReference` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, `lastUsedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE UNIQUE INDEX `index_cloud_accounts_provider_providerAccountId` ON `cloud_accounts` (`provider`, `providerAccountId`)")
                        db.execSQL("CREATE TABLE `media_history` (`stableMediaId` TEXT NOT NULL, `uri` TEXT NOT NULL, `title` TEXT NOT NULL, PRIMARY KEY(`stableMediaId`))")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO cloud_accounts VALUES('g:1','GOOGLE_DRIVE','1','Drive','u***@example.com','keystore-ref',100,200)")
            db.execSQL("INSERT INTO media_history VALUES('old-media','content://media/1','Existing title')")

            MaxDatabase.MIGRATION_7_8.migrate(db)

            assertEquals("keystore-ref", stringValue(db, "SELECT authReference FROM cloud_accounts WHERE id='g:1'"))
            assertEquals("Existing title", stringValue(db, "SELECT title FROM media_history WHERE stableMediaId='old-media'"))
            assertTrue(tableExists(db, "private_media"))
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`private_media`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            }
            assertEquals(
                setOf("vaultId", "containerLocation", "encryptedSizeBytes", "originalSizeBytes", "createdAtMs", "importedAtMs", "formatVersion", "status"),
                columns,
            )
            listOf("title", "fileName", "originalDisplayName", "path", "uri", "mimeType").forEach { sensitiveColumn ->
                assertFalse("private_media leaked plaintext metadata column $sensitiveColumn", sensitiveColumn in columns)
            }
            db.execSQL("INSERT INTO private_media VALUES('opaque-id','opaque-id.maxvault',1234,1000,1,1,1,'AVAILABLE')")
            assertEquals("opaque-id.maxvault", stringValue(db, "SELECT containerLocation FROM private_media WHERE vaultId='opaque-id'"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }

    private fun stringValue(db: SupportSQLiteDatabase, sql: String): String? =
        db.query(sql).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null }
}
