package com.dauntehnusantara.pos

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.core.navigation.PosApp
import com.dauntehnusantara.pos.feature.foundation.presentation.FoundationViewModel
import com.dauntehnusantara.pos.feature.catalog.presentation.CatalogViewModel
import com.dauntehnusantara.pos.feature.pos.presentation.PosViewModel
import com.dauntehnusantara.pos.feature.auth.presentation.AuthRoot
import com.dauntehnusantara.pos.feature.auth.presentation.AuthViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val catalogEditorViewModel: com.dauntehnusantara.pos.feature.catalog.presentation.CatalogEditorViewModel by viewModels()
    private val reportsViewModel: com.dauntehnusantara.pos.feature.reports.presentation.ReportViewModel by viewModels()
    private val staffViewModel: com.dauntehnusantara.pos.feature.staff.presentation.StaffViewModel by viewModels()
    private val recoveryViewModel: com.dauntehnusantara.pos.feature.recovery.presentation.RecoveryViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val authenticatedPosViewModel: com.dauntehnusantara.pos.feature.pos.presentation.AuthenticatedPosViewModel by viewModels()
    private val posViewModel: PosViewModel by viewModels()
    private val catalogViewModel: CatalogViewModel by viewModels()
    private val viewModel: FoundationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DaunTehTheme {
            AuthRoot(authViewModel, authenticated = { destination, expanded ->
                when(destination) {
                    com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.CATALOG -> com.dauntehnusantara.pos.feature.catalog.presentation.CatalogEditorRoute(catalogEditorViewModel)
                    com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.OVERVIEW,
                    com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.REPORTS -> com.dauntehnusantara.pos.feature.reports.presentation.ReportRoute(reportsViewModel,expanded)
                    com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.STAFF -> com.dauntehnusantara.pos.feature.staff.presentation.StaffRoute(staffViewModel) {
                        val state by authViewModel.state.collectAsStateWithLifecycle()
                        com.dauntehnusantara.pos.feature.auth.presentation.StaffDevicesPanel(state,authViewModel)
                    }
                    else -> com.dauntehnusantara.pos.feature.pos.presentation.AuthenticatedPosRoute(authenticatedPosViewModel,destination,expanded)
                }
            }, recovery = { com.dauntehnusantara.pos.feature.recovery.presentation.RecoveryPanel(recoveryViewModel) }) {
                PosApp(viewModel,catalogViewModel,posViewModel)
            }
        } }
    }
}
