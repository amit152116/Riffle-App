package com.amit152116.riffle.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB_NAME = "riffle-migration-test.db"

/**
 * MigrationTestHelper harness, per the spec's migration policy: production
 * code never uses fallbackToDestructiveMigration.
 */
@RunWith(AndroidJUnit4::class)
class RiffleDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RiffleDatabase::class.java
    )

    @Test
    fun version1_createsSuccessfully() {
        // createDatabase runs RiffleDatabase's version-1 schema against a
        // fresh file-backed DB and validates it against the exported schema
        // JSON in app/schemas/ - this alone catches an entity/schema-export
        // mismatch even before migration 2 exists.
        helper.createDatabase(TEST_DB_NAME, 1).close()
    }

    @Test
    fun migrate1To2_addsDateAddedColumn() {
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO Track (path, size, mtime, audioContentId, mediaStoreId, albumId, " +
                    "durationMs, bitrate, tagTitle, tagArtist, tagAlbum, tagGenre, " +
                    "tagCompleteness, lastSeenScanId, present, absentReason, scannedAt) VALUES " +
                    "('/music/a.mp3', 4000000, 1700000000.0, NULL, 42, 7, 180000, 320, " +
                    "'Title', 'Artist', 'Album', 'Genre', 4, NULL, 1, NULL, 1000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 2, true, RiffleDatabase.MIGRATION_1_2
        )
        migrated.query("SELECT dateAdded FROM Track").use { cursor ->
            assertEquals(1, cursor.count)
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.isNull(0))
        }
        migrated.close()
    }

    @Test
    fun migrate2To3_addsDisplayNameAndRelativePathColumns() {
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO Track (path, size, mtime, audioContentId, mediaStoreId, albumId, " +
                    "durationMs, bitrate, tagTitle, tagArtist, tagAlbum, tagGenre, " +
                    "tagCompleteness, lastSeenScanId, present, absentReason, scannedAt) VALUES " +
                    "('/music/a.mp3', 4000000, 1700000000.0, NULL, 42, 7, 180000, 320, " +
                    "'Title', 'Artist', 'Album', 'Genre', 4, NULL, 1, NULL, 1000)"
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, RiffleDatabase.MIGRATION_1_2).close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 3, true, RiffleDatabase.MIGRATION_2_3
        )
        migrated.query("SELECT displayName, relativePath FROM Track").use { cursor ->
            assertEquals(1, cursor.count)
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.isNull(0))
            assertEquals(true, cursor.isNull(1))
        }
        migrated.close()
    }

    @Test
    fun migrate3To4_replacesTrackSimilarity_andExtendsClusterTables() {
        helper.createDatabase(TEST_DB_NAME, 3).apply {
            execSQL("INSERT INTO ClusterRun (nClusters, nTracks, scalerParams, createdAt) VALUES (5, 100, NULL, 1000)")
            execSQL("INSERT INTO ClusterCentroid (runId, clusterId, label, centroid, nTracks) VALUES (1, 0, 'Old', NULL, 10)")
            close()
        }

        // validateDroppedTables = true: fails if TrackSimilarity is still there, and validates every
        // column, foreign key and index of the migrated schema against 4.json.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 4, true, RiffleDatabase.MIGRATION_3_4
        )

        migrated.query("SELECT nClusters, nextClusterId, configHash, inputHash, silhouette FROM ClusterRun").use { c ->
            assertEquals(1, c.count)
            assertEquals(true, c.moveToFirst())
            assertEquals(5, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertEquals(true, c.isNull(2))
            assertEquals(true, c.isNull(3))
            assertEquals(true, c.isNull(4))
        }
        migrated.query("SELECT label, tempoBand, energyBand, mode, timbre, matchDistance FROM ClusterCentroid").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("Old", c.getString(0))
            for (i in 1..5) assertEquals(true, c.isNull(i))
        }
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('TrackSimilarity', 'ContentSimilarity')"
        ).use { c ->
            assertEquals(1, c.count)
            assertEquals(true, c.moveToFirst())
            assertEquals("ContentSimilarity", c.getString(0))
        }
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'ContentSimilarity' AND name LIKE 'index_%'"
        ).use { c -> assertEquals(2, c.count) }
        migrated.close()
    }
}
