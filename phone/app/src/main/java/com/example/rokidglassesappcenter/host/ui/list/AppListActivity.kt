package com.example.rokidglassesappcenter.host.ui.list

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.rokidglassesappcenter.host.Constants
import com.example.rokidglassesappcenter.host.R
import com.example.rokidglassesappcenter.host.ui.components.ActionButtonGroup
import com.example.rokidglassesappcenter.host.ui.components.PRIMARY_BUTTON_WIDTH
import com.example.rokidglassesappcenter.host.ui.components.ScreenShell
import com.example.rokidglassesappcenter.host.ui.components.StatusPanel
import com.example.rokidglassesappcenter.host.ui.components.statusLines
import com.example.rokidglassesappcenter.host.ui.theme.HostTheme

class AppListActivity : ComponentActivity() {
    private val viewModel by viewModels<AppListViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val token = intent.getStringExtra(Constants.EXTRA_TOKEN)
        setContent {
            HostTheme {
                AppListScreen(viewModel = viewModel)
            }
        }
        viewModel.init(this, token)
    }

    override fun onDestroy() {
        viewModel.release()
        super.onDestroy()
    }
}

@Composable
private fun AppListScreen(viewModel: AppListViewModel) {
    val ctx = LocalContext.current
    val connection by viewModel.connection.collectAsState()
    val clientState by viewModel.clientState.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val showSystem by viewModel.showSystemApps.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val toast by viewModel.toast.collectAsState()

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.installApk(uri)
    }

    val connectionLine = stringResource(
        id = R.string.list_connection_status,
        if (connection) stringResource(id = R.string.list_connection_ok)
        else stringResource(id = R.string.list_connection_waiting),
    )
    val clientLine = stringResource(
        id = R.string.list_client_status,
        when (clientState) {
            AppListViewModel.ClientState.Idle -> stringResource(id = R.string.list_client_idle)
            AppListViewModel.ClientState.Installing,
            AppListViewModel.ClientState.Starting -> stringResource(id = R.string.list_client_installing)
            AppListViewModel.ClientState.Ready -> stringResource(id = R.string.list_client_ready)
            AppListViewModel.ClientState.Failed -> stringResource(id = R.string.list_client_failed)
        },
    )

    ScreenShell(title = stringResource(id = R.string.screen_title_list)) {
        StatusPanel(
            lines = statusLines(
                connectionLine,
                clientLine,
                if (loading) stringResource(id = R.string.list_loading) else null,
                if (uploading) stringResource(id = R.string.list_uploading) else null,
            ),
        )

        ActionButtonGroup {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(PRIMARY_BUTTON_WIDTH),
                onClick = {
                    pickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                },
                enabled = clientState == AppListViewModel.ClientState.Ready && !uploading,
            ) { Text(stringResource(id = R.string.list_install_apk)) }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(PRIMARY_BUTTON_WIDTH),
                onClick = { viewModel.refresh() },
                enabled = clientState == AppListViewModel.ClientState.Ready,
            ) { Text(stringResource(id = R.string.list_refresh)) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = showSystem, onCheckedChange = { viewModel.toggleShowSystem() })
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = stringResource(id = R.string.list_filter_show_system),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val visible = if (showSystem) apps else apps.filterNot { it.system }
        if (visible.isEmpty()) {
            Text(
                text = stringResource(id = R.string.list_no_apps),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { app ->
                    AppRow(
                        app = app,
                        onStart = { viewModel.startApp(app) },
                        onStop = { viewModel.stopApp(app) },
                        onUninstall = { viewModel.uninstallApp(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: GlassApp,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = app.label.ifBlank { app.pkg },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (app.system) SystemBadge()
            }
            // Compact subtext: package + version (when present), one line each, secondary color.
            Text(
                text = app.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (app.versionName.isNotBlank()) {
                Text(
                    text = stringResource(id = R.string.row_version, app.versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStart,
                    enabled = app.launchable,
                ) { Text(stringResource(id = R.string.row_action_start)) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onStop,
                ) { Text(stringResource(id = R.string.row_action_stop)) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onUninstall,
                    enabled = !app.system,
                ) { Text(stringResource(id = R.string.row_action_uninstall)) }
            }
        }
    }
}

@Composable
private fun SystemBadge() {
    Text(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        text = stringResource(id = R.string.row_system_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
