package com.dauntehnusantara.pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.core.navigation.PosShell
import com.dauntehnusantara.pos.feature.foundation.presentation.StorageState
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destinationsNavigateAndErrorCanRetry() {
        var retries = 0
        compose.setContent { DaunTehTheme { PosShell(StorageState.Error, { retries++ }) } }
        compose.onNodeWithText("Coba lagi").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, retries) }
        compose.onNode(hasText("Riwayat") and hasClickAction()).performClick()
        compose.onNode(hasText("Riwayat") and !hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Pengaturan") and hasClickAction()).performClick()
        compose.onNode(hasText("Pengaturan") and !hasClickAction()).assertIsDisplayed()
    }
}
