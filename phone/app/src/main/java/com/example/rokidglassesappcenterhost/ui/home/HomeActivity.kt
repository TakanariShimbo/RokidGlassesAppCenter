package com.example.rokidglassesappcenterhost.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.rokidglassesappcenterhost.Constants
import com.example.rokidglassesappcenterhost.R
import com.example.rokidglassesappcenterhost.ui.components.ActionButtonGroup
import com.example.rokidglassesappcenterhost.ui.components.PRIMARY_BUTTON_WIDTH
import com.example.rokidglassesappcenterhost.ui.components.ScreenShell
import com.example.rokidglassesappcenterhost.ui.components.StatusPanel
import com.example.rokidglassesappcenterhost.ui.components.statusLines
import com.example.rokidglassesappcenterhost.ui.theme.HostTheme
import com.example.rokidglassesappcenterhost.util.SmartMarketLauncher

class HomeActivity : ComponentActivity() {
    private val viewModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HostTheme {
                HomeScreen(
                    viewModel = viewModel,
                    launchAPKInstall = {
                        SmartMarketLauncher.launchMarket(
                            this@HomeActivity,
                            "com.rokid.sprite.global.aiapp",
                        )
                    },
                    recheckAPPInstall = { viewModel.checkRokidAIAppInstalled(this@HomeActivity) },
                    requestAuthorization = {
                        viewModel.requestAuthorization(this@HomeActivity, Constants.AUTH_REQUEST_CODE)
                    },
                    startAppList = { viewModel.startAppList(this@HomeActivity) },
                )
            }
        }
        viewModel.checkRokidAIAppInstalled(this)
    }

    @Deprecated("Hi Rokid SDK still returns auth via onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.AUTH_REQUEST_CODE) {
            viewModel.checkAuthorizationResult(resultCode, data)
        }
    }
}

@Composable
private fun HomeScreen(
    viewModel: HomeViewModel,
    launchAPKInstall: () -> Unit,
    recheckAPPInstall: () -> Unit,
    requestAuthorization: () -> Unit,
    startAppList: () -> Unit,
) {
    val isRokidAIAppInstalled by viewModel.isRokidAIAppInstalled.collectAsState()
    val isAuthorized by viewModel.isAuthorized.collectAsState()
    val tokenResult by viewModel.tokenResult.collectAsState()

    val installLine = stringResource(
        id = R.string.home_install_status,
        stringResource(
            id = if (isRokidAIAppInstalled) R.string.home_install_state_installed
            else R.string.home_install_state_not_installed,
        ),
    )
    val authLine = stringResource(
        id = R.string.home_auth_status,
        stringResource(
            id = if (isAuthorized) R.string.home_auth_state_authorized
            else R.string.home_auth_state_unauthorized,
        ),
    )

    ScreenShell(title = stringResource(id = R.string.screen_title_home)) {
        StatusPanel(
            lines = statusLines(
                installLine,
                authLine,
                tokenResult.takeIf { it.isNotBlank() },
            ),
        )
        ActionButtonGroup {
            if (isRokidAIAppInstalled) {
                if (isAuthorized) {
                    Button(
                        modifier = Modifier.fillMaxWidth(PRIMARY_BUTTON_WIDTH),
                        onClick = startAppList,
                    ) { Text(stringResource(id = R.string.home_open_app_list)) }
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(PRIMARY_BUTTON_WIDTH),
                        onClick = requestAuthorization,
                    ) { Text(stringResource(id = R.string.home_request_authorization)) }
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(PRIMARY_BUTTON_WIDTH),
                    onClick = launchAPKInstall,
                ) { Text(stringResource(id = R.string.home_install_rokid_ai_app)) }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(PRIMARY_BUTTON_WIDTH),
                    onClick = recheckAPPInstall,
                ) { Text(stringResource(id = R.string.home_recheck_install)) }
            }
        }
    }
}
