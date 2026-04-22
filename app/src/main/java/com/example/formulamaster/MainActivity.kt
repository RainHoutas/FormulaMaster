package com.example.formulamaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.formulamaster.ui.theme.FormulaMasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FormulaMasterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ThemePreviewPlaceholder(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Formula Master",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        Text(
            text = "Sprint 1 · Task 1.2 主题配置完成",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "切换系统深浅色模式后重启 App，色彩会自动跟随。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "secondaryContainer",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "errorContainer（错误/危险场景保留）",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
