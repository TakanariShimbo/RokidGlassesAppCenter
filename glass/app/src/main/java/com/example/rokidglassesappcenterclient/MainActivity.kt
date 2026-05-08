package com.example.rokidglassesappcenterclient

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rokidglassesappcenterclient.ui.theme.ClientTheme

/**
 * Glasses-side MainActivity. Single screen, single column, vertical scroll.
 *
 * Glasses display is small and overlays the user's real-world view, so legibility
 * trumps decoration: pure black background, bright green monospaced text.
 */
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.init(this)
        setContent {
            ClientTheme { ClientScreen(viewModel) }
        }
    }
}

@Composable
private fun ClientScreen(viewModel: MainViewModel) {
    val connection by viewModel.connection.collectAsState()
    val lastRequest by viewModel.lastRequest.collectAsState()
    val lastResponse by viewModel.lastResponse.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Header("Rokid Glasses App Center Client")
        Body(connection)
        Label("req")
        Body(lastRequest?.take(MAX_PREVIEW_CHARS) ?: "-")
        Label("res")
        Body(lastResponse?.take(MAX_PREVIEW_CHARS) ?: "-")
    }
}

@Composable
private fun Header(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        color = TEXT,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        text = text,
        color = TEXT_DIM,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun Body(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        color = TEXT,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
    )
}

private val TEXT = Color(0xFF00FF66)
private val TEXT_DIM = Color(0xFF008833)
private const val MAX_PREVIEW_CHARS = 1000
