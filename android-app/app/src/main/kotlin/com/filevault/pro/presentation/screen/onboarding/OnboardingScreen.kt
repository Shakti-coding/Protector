package com.filevault.pro.presentation.screen.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            "Deep File Scanner",
            "Every file. Every folder.",
            "Scans your entire device storage — photos, videos, documents, and hidden files — in real-time.",
            Icons.Default.Search,
            listOf(Color(0xFF1848C4), Color(0xFF7C9EF7))
        ),
        OnboardingPage(
            "Smart Catalog",
            "Permanent metadata storage",
            "Stores file metadata permanently with rich details: dimensions, camera info, GPS, duration, and more.",
            Icons.Default.Storage,
            listOf(Color(0xFF8B009C), Color(0xFFE8B4FF))
        ),
        OnboardingPage(
            "Auto Sync",
            "Telegram & Email integration",
            "Automatically syncs your files to Telegram or Email on a schedule you control.",
            Icons.Default.Sync,
            listOf(Color(0xFF006A4E), Color(0xFF74DFB0))
        ),
        OnboardingPage(
            "Advanced UI",
            "Sort. Filter. Discover.",
            "Search by any field, filter by type or date, detect duplicates, and browse by folder — all in one place.",
            Icons.Default.Dashboard,
            listOf(Color(0xFFB94E00), Color(0xFFFFB87D))
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val page = pages[pageIndex]
            OnboardingPage(page)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index ->
                    val width by animateDpAsState(
                        targetValue = if (pagerState.currentPage == index) 24.dp else 8.dp,
                        animationSpec = tween(300)
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Back")
                    }
                } else {
                    TextButton(onClick = onComplete) { Text("Skip") }
                }
                if (pagerState.currentPage < pages.size - 1) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Get Started")
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: OnboardingPage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(
                colors = listOf(page.gradientColors[0].copy(alpha = 0.12f), Color.Transparent),
                radius = 800f
            )),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp).offset(y = (-60).dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(page.gradientColors.map { it.copy(alpha = 0.25f) })),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = page.gradientColors[0]
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(page.title, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(page.subtitle, style = MaterialTheme.typography.titleMedium,
                color = page.gradientColors[0], fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(page.description, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, lineHeight = 24.sp)
        }
    }
}
