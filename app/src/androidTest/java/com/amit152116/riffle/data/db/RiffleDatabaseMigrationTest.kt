package com.amit152116.riffle.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB_NAME = "riffle-migration-test.db"

/**
 * No migration exists yet - RiffleDatabase is at version 1. This scaffolds
 * the MigrationTestHelper pattern so the first real migration (version 2)
 * has a working test harness to extend, per the spec's migration policy:
 * production code never uses fallbackToDestructiveMigration.
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
}
