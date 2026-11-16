package com.dauntehnusantara.pos

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dauntehnusantara.pos.feature.printing.data.PrinterSelectionStore
import com.dauntehnusantara.pos.feature.printing.domain.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrinterSelectionStoreTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun selectionSurvivesReopenAndIsIsolatedByProjectAccountAndOutlet()=runBlocking {
        val file=File(temp.root,"printer.preferences_pb")
        val scope=PrinterScope("https://example.invalid","user","outlet")
        val job=SupervisorJob()
        val first=PrinterSelectionStore(PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+job)){file})
        val device=PrinterDevice("00:11:22:33:44:55","Printer\n")
        first.write(scope,device)
        for(other in listOf(scope.copy(project="other"),scope.copy(user="other"),scope.copy(outlet="other")))assertNull(first.read(other))
        job.cancelAndJoin()
        val reopenedJob=SupervisorJob()
        try {
            val next=PrinterSelectionStore(PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+reopenedJob)){file})
            assertEquals(device.copy(name="Printer"),next.read(scope))
            next.write(scope,null);assertNull(next.read(scope))
        }finally{reopenedJob.cancelAndJoin()}
    }
}
