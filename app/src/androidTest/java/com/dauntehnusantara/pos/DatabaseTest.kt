package com.dauntehnusantara.pos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dauntehnusantara.pos.core.database.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @Test fun metadataSurvivesDatabaseReopen(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "metadata-test.db"
        context.deleteDatabase(name)
        try {
            val database = Room.databaseBuilder(context, PosDatabase::class.java, name).build()
            try {
                assertEquals(0, database.metadataDao().observeEntryCount().first())
                database.metadataDao().put(AppMetadata("test", "first"))
                database.metadataDao().put(AppMetadata("test", "updated"))
                assertEquals(1, database.metadataDao().observeEntryCount().first())
            } finally { database.close() }
            val reopened = Room.databaseBuilder(context, PosDatabase::class.java, name).build()
            try {
                assertEquals(1, reopened.metadataDao().observeEntryCount().first())
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
