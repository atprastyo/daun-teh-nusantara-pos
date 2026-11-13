package com.dauntehnusantara.pos

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.sync.domain.SyncStatus
import com.dauntehnusantara.pos.feature.sync.presentation.SyncStatusContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SyncStatusTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyQueueAndRetryAreExplicit() {
        var retried = false
        compose.setContent { DaunTehTheme { Column { SyncStatusContent(SyncStatus(), false) { retried = true } } } }
        compose.onNodeWithText("Belum ada penjualan", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Coba sinkronkan lagi").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
    @Test fun reviewNeverClaimsServerAcceptanceAndPreservesOriginalReceiptNotice() {
        compose.setContent { DaunTehTheme { Column { SyncStatusContent(SyncStatus(review = 1), false) {} } } }
        compose.onNodeWithText("Perlu ditinjau: 1", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Harga dan struk asli tidak diubah.", substring = true).assertIsDisplayed()
    }
}
