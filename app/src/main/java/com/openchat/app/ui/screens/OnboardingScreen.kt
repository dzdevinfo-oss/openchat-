package com.openchat.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openchat.app.ui.theme.PrimaryTeal
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            "OpenChat",
            "AI in your pocket. High performance, native, and always ready.",
            Icons.Default.AutoAwesome,
            PrimaryTeal
        ),
        OnboardingPage(
            "Connect Any AI",
            "Bring your own keys. Support for OpenAI, Anthropic, Google, and more.",
            Icons.Default.Api,
            MaterialTheme.colorScheme.secondary
        ),
        OnboardingPage(
            "Full Workspace IDE",
            "The AI can see and edit your project files. Build apps on the go.",
            Icons.Default.Terminal,
            MaterialTheme.colorScheme.tertiary
        ),
        OnboardingPage(
            "Let's Get Started",
            "Ready to experience the fastest AI interaction on Android?",
            Icons.Default.RocketLaunch,
            PrimaryTeal
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { position ->
            val page = pages[position]
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = page.color
                )
                Spacer(Modifier.height(32.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page Indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) PrimaryTeal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                }
            }

            if (pagerState.currentPage == pages.size - 1) {
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Get Started")
                }
            } else {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Text("Next", color = PrimaryTeal)
                }
            }
        }
    }
}
