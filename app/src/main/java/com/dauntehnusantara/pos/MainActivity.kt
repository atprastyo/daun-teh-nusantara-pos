package com.dauntehnusantara.pos

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
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
    private var exitHint: android.widget.Toast? = null
    private val doubleBackExit = com.dauntehnusantara.pos.core.navigation.DoubleBackExitCallback(
        now = android.os.SystemClock::elapsedRealtime,
        showHint = {
            exitHint?.cancel()
            exitHint = android.widget.Toast.makeText(this, R.string.press_back_again_to_exit,
                android.widget.Toast.LENGTH_SHORT).also { it.show() }
        },
        exit = { exitHint?.cancel(); finish() },
    )

    override fun onStop() {
        doubleBackExit.reset()
        exitHint?.cancel()
        super.onStop()
    }

    private val printerViewModel:com.dauntehnusantara.pos.feature.printing.presentation.PrinterViewModel by viewModels()
    private val appearanceViewModel:com.dauntehnusantara.pos.feature.settings.presentation.AppearanceViewModel by viewModels()
    private val productPhotosViewModel: com.dauntehnusantara.pos.feature.catalog.photos.presentation.ProductPhotosViewModel by viewModels()
    private val catalogEditorViewModel: com.dauntehnusantara.pos.feature.catalog.presentation.CatalogEditorViewModel by viewModels()
    private val reportsViewModel: com.dauntehnusantara.pos.feature.reports.presentation.ReportViewModel by viewModels()
    private val staffViewModel: com.dauntehnusantara.pos.feature.staff.presentation.StaffViewModel by viewModels()
    private val settingsViewModel: com.dauntehnusantara.pos.feature.settings.presentation.SettingsViewModel by viewModels()
    private val syncStatusViewModel: com.dauntehnusantara.pos.feature.sync.presentation.SyncStatusViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val historyViewModel: com.dauntehnusantara.pos.feature.history.presentation.HistoryViewModel by viewModels()
    private val authenticatedPosViewModel: com.dauntehnusantara.pos.feature.pos.presentation.AuthenticatedPosViewModel by viewModels()
    private val posViewModel: PosViewModel by viewModels()
    private val catalogViewModel: CatalogViewModel by viewModels()
    private val viewModel: FoundationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register before Compose so feature/navigation Back handlers keep priority.
        onBackPressedDispatcher.addCallback(this, doubleBackExit)
        if (android.os.Build.VERSION.SDK_INT >= 29) window.decorView.isForceDarkAllowed = false
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val appearance by appearanceViewModel.state.collectAsStateWithLifecycle()
            if(appearance.mode==null)return@setContent
            val dark=appearance.mode==com.dauntehnusantara.pos.feature.settings.domain.ThemeMode.DARK
            androidx.compose.runtime.LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
                    navigationBarStyle=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
            }
            val photoPicker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) {
                catalogEditorViewModel.photoPicked(it?.toString())
            }
            val access by productPhotosViewModel.access.collectAsStateWithLifecycle()
            androidx.compose.runtime.CompositionLocalProvider(com.dauntehnusantara.pos.feature.catalog.photos.presentation.LocalProductPhotos provides
                com.dauntehnusantara.pos.feature.catalog.photos.presentation.PhotoAccess(productPhotosViewModel.repository,access)) {
            DaunTehTheme(darkTheme=dark) {
            AuthRoot(authViewModel, authenticated = { destination, expanded ->
                when(destination) {
                    com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.CATALOG -> com.dauntehnusantara.pos.feature.catalog.presentation.CatalogEditorRoute(catalogEditorViewModel) { product ->
                        if(catalogEditorViewModel.beginPhotoPick(product)) photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.OVERVIEW,
                    com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.REPORTS -> com.dauntehnusantara.pos.feature.reports.presentation.ReportRoute(reportsViewModel,expanded,overview=destination==com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.OVERVIEW)
                    com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.STAFF -> run {
                        val state by authViewModel.state.collectAsStateWithLifecycle()
                        com.dauntehnusantara.pos.feature.staff.presentation.StaffRoute(staffViewModel,reviewerBusy=state.busy) { enabled ->
                            com.dauntehnusantara.pos.feature.auth.presentation.StaffDevicesPanel(state,authViewModel,enabled)
                        }
                    }
                    else -> com.dauntehnusantara.pos.feature.pos.presentation.AuthenticatedPosRoute(authenticatedPosViewModel,destination,expanded) { historyExpanded ->
                        com.dauntehnusantara.pos.feature.history.presentation.HistoryRoute(historyViewModel,historyExpanded)
                    }
                }
            }, settings = { com.dauntehnusantara.pos.feature.settings.presentation.SettingsRoute(settingsViewModel,syncStatusViewModel,appearance,appearanceViewModel::change,appearanceViewModel::retry,printerModel=printerViewModel) }) {
                PosApp(viewModel,catalogViewModel,posViewModel)
            }
        } } }
    }
}
