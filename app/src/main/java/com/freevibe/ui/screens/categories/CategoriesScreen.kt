package com.freevibe.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class WallpaperCategory(
    val id: String,
    val name: String,
    val query: String,
    val gradient: List<Color>,
    val icon: String,
)

private val categories = listOf(
    WallpaperCategory("nature", "Nature", "nature landscape", listOf(Color(0xFF2E7D32), Color(0xFF1B5E20)), "🌿"),
    WallpaperCategory("space", "Space", "space galaxy nebula", listOf(Color(0xFF1A237E), Color(0xFF0D47A1)), "🌌"),
    WallpaperCategory("abstract", "Abstract", "abstract art", listOf(Color(0xFF6A1B9A), Color(0xFF4A148C)), "🎨"),
    WallpaperCategory("minimal", "Minimal", "minimal clean", listOf(Color(0xFF37474F), Color(0xFF263238)), "◻"),
    WallpaperCategory("anime", "Anime", "anime illustration", listOf(Color(0xFFD81B60), Color(0xFF880E4F)), "🎌"),
    WallpaperCategory("cars", "Cars", "car automotive", listOf(Color(0xFFE65100), Color(0xFFBF360C)), "🏎"),
    WallpaperCategory("city", "City", "city skyline urban", listOf(Color(0xFF455A64), Color(0xFF263238)), "🌆"),
    WallpaperCategory("dark", "Dark", "dark amoled black", listOf(Color(0xFF212121), Color(0xFF000000)), "🖤"),
    WallpaperCategory("ocean", "Ocean", "ocean sea water", listOf(Color(0xFF006064), Color(0xFF004D40)), "🌊"),
    WallpaperCategory("mountain", "Mountains", "mountain peak", listOf(Color(0xFF4E342E), Color(0xFF3E2723)), "🏔"),
    WallpaperCategory("sunset", "Sunset", "sunset sunrise sky", listOf(Color(0xFFFF6F00), Color(0xFFE65100)), "🌅"),
    WallpaperCategory("flower", "Flowers", "flower floral", listOf(Color(0xFFC2185B), Color(0xFF880E4F)), "🌸"),
    WallpaperCategory("tech", "Technology", "technology circuit", listOf(Color(0xFF00695C), Color(0xFF004D40)), "💻"),
    WallpaperCategory("animal", "Animals", "animal wildlife", listOf(Color(0xFF558B2F), Color(0xFF33691E)), "🦁"),
    WallpaperCategory("retro", "Retro", "retro vintage", listOf(Color(0xFFFF8F00), Color(0xFFF57F17)), "📺"),
    WallpaperCategory("neon", "Neon", "neon glow cyberpunk", listOf(Color(0xFF7C4DFF), Color(0xFF651FFF)), "💜"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onCategoryClick: (query: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(categories, key = { it.id }) { cat ->
                CategoryCard(category = cat, onClick = { onCategoryClick(cat.query) })
            }
        }
    }
}

@Composable
private fun CategoryCard(category: WallpaperCategory, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(category.gradient))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(category.icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
