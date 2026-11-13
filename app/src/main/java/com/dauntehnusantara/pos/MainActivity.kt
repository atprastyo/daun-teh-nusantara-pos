package com.dauntehnusantara.pos

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
    private val authViewModel: AuthViewModel by viewModels()
    private val posViewModel: PosViewModel by viewModels()
    private val catalogViewModel: CatalogViewModel by viewModels()
    private val viewModel: FoundationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DaunTehTheme { AuthRoot(authViewModel) { PosApp(viewModel, catalogViewModel, posViewModel) } } }
    }
}
