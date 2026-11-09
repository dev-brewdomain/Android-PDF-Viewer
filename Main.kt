package com.example.recipereader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// Recipe Data Model
data class Recipe(
    val id: String,
    val title: String,
    val category: String,
    val assetFileName: String,
    val isFavorite: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Ensures the app launches straight into the dynamic recipe viewer UI
                    RecipeAppRoot()
                }
            }
        }
    }
}

@Composable
fun RecipeAppRoot() {
    val context = LocalContext.current
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }
    var isLoadingAssets by remember { mutableStateOf(true) }

    // Dynamically scan the assets/recipes/ folder on startup
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val discoveredRecipes = loadRecipesFromAssets(context)
            withContext(Dispatchers.Main) {
                recipes = discoveredRecipes
                isLoadingAssets = false
            }
        }
    }

    val currentRecipe = selectedRecipe
    if (currentRecipe == null) {
        if (isLoadingAssets) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            RecipeListScreen(
                recipes = recipes,
                onRecipeClick = { selectedRecipe = it },
                onToggleFavorite = { id ->
                    recipes = recipes.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
                }
            )
        }
    } else {
        RecipePdfViewerScreen(
            recipe = currentRecipe,
            onBack = { selectedRecipe = null }
        )
    }
}

/**
 * Scans app/src/main/assets/recipes/ for PDF files dynamically.
 */
fun loadRecipesFromAssets(context: Context): List<Recipe> {
    val recipeList = mutableListOf<Recipe>()
    try {
        val assetFiles = context.assets.list("recipes") ?: arrayOf()
        
        assetFiles.filter { it.endsWith(".pdf", ignoreCase = true) }.forEachIndexed { index, fileName ->
            val cleanName = fileName.substringBeforeLast(".")
            val formattedTitle = cleanName
                .replace("_", " ")
                .replace("-", " ")
                .split(" ")
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

            val category = when {
                cleanName.contains("breakfast", true) || cleanName.contains("pancake", true) -> "Breakfast"
                cleanName.contains("dinner", true) || cleanName.contains("taco", true) || cleanName.contains("pasta", true) -> "Dinner"
                cleanName.contains("dessert", true) || cleanName.contains("cookie", true) || cleanName.contains("cake", true) -> "Dessert"
                else -> "General"
            }

            recipeList.add(
                Recipe(
                    id = index.toString(),
                    title = formattedTitle,
                    category = category,
                    assetFileName = "recipes/$fileName"
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return recipeList
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    recipes: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    val categories = listOf("All") + recipes.map { it.category }.distinct()

    val filteredRecipes = recipes.filter { recipe ->
        val matchesSearch = recipe.title.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "All" || recipe.category.equals(selectedCategory, ignoreCase = true)
        val matchesFavorite = !showFavoritesOnly || recipe.isFavorite
        matchesSearch && matchesCategory && matchesFavorite
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipe Book (${recipes.size})") },
                actions = {
                    IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                        Icon(
                            imageVector = if (showFavoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Filter Favorites",
                            tint = if (showFavoritesOnly) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search recipes...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            if (categories.size > 2) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredRecipes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recipes found.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                    items(filteredRecipes) { recipe ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { onRecipeClick(recipe) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = recipe.title, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = recipe.category, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { onToggleFavorite(recipe.id) }) {
                                    Icon(
                                        imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (recipe.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePdfViewerScreen(recipe: Recipe, onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(recipe) {
        withContext(Dispatchers.IO) {
            val renderer = PdfRecipeRenderer(context, recipe.assetFileName)
            val bitmaps = mutableListOf<Bitmap>()
            val renderWidth = 1080

            for (i in 0 until renderer.pageCount) {
                renderer.renderPage(i, renderWidth)?.let { bitmaps.add(it) }
            }
            renderer.close()

            withContext(Dispatchers.Main) {
                pages = bitmaps
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(pages) { bitmap ->
                        ZoomablePdfPage(bitmap = bitmap)
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomablePdfPage(bitmap: Bitmap) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF Page",
            modifier = Modifier.fillMaxSize()
        )
    }
}

class PdfRecipeRenderer(context: Context, assetFileName: String) {
    private val fileDescriptor: ParcelFileDescriptor
    private val pdfRenderer: PdfRenderer

    init {
        val cacheFile = File(context.cacheDir, assetFileName.substringAfterLast("/"))
        if (!cacheFile.exists()) {
            context.assets.open(assetFileName).use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }
        }
        fileDescriptor = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor)
    }

    val pageCount: Int get() = pdfRenderer.pageCount

    fun renderPage(pageIndex: Int, width: Int): Bitmap? {
        if (pageIndex < 0 || pageIndex >= pdfRenderer.pageCount) return null
        val page = pdfRenderer.openPage(pageIndex)
        val height = (width.toFloat() * (page.height.toFloat() / page.width.toFloat())).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    fun close() {
        pdfRenderer.close()
        fileDescriptor.close()
    }
}
