/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
    onClearUpdateBadge: () -> Unit = {},
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()

    val storagePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    var isStorageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            notificationPermission == null ||
                ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            isStorageGranted = result[storagePermission] == true || isStorageGranted
            if (notificationPermission != null) {
                isNotificationGranted = result[notificationPermission] == true || isNotificationGranted
            }
        }

    val scrollBehavior = appBarScrollBehavior()
    val shouldShowPermissionHint = !isStorageGranted || !isNotificationGranted
    val hasUpdate =
        BuildConfig.UPDATER_AVAILABLE &&
            Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
    var isUpdateDismissed by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val recentQueries = remember { mutableStateListOf<String>() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    // ponytail: recent queries in-memory only, DataStore persistence if needed later
    val settingsGroups = buildSettingsGroups(navController, isAndroid12OrLater, hasUpdate, context)
    val settingsItems =
        remember(settingsGroups) {
            settingsGroups.flatMap { it.items }
        }
    val deepItems = buildDeepSettingsSearchItems(navController)
    val itemGroupMap = remember(settingsGroups, deepItems) {
        buildMap<String, String> {
            settingsGroups.forEach { g -> g.items.forEach { put(it.key, g.title) } }
            // deep items already carry section in subtitle, but keep map for fallback
            deepItems.forEach { put(it.key, it.subtitle ?: "") }
        }
    }
    // pool for search: top-level + deep toggleables
    val searchPool = remember(settingsItems, deepItems) { settingsItems + deepItems }
    val trimmedQuery = query.trim()
    val tokens = remember(trimmedQuery) {
        trimmedQuery.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    }
    val rankedItems = remember(searchPool, tokens, itemGroupMap, settingsItems) {
        if (tokens.isEmpty()) {
            settingsItems.map { ScoredItem(it, 0, itemGroupMap[it.key]) }
        } else {
            searchPool.mapNotNull { item ->
                val score = scoreSettingsItem(item, tokens, itemGroupMap[item.key])
                if (score > 0) ScoredItem(item, score, itemGroupMap[item.key]) else null
            }.sortedByDescending { it.score }
        }
    }
    val filteredItems = rankedItems.map { it.item }
    val queryLower = trimmedQuery.lowercase()
    val easterEggEmoji = remember(query) { findEmojiEasterEgg(query) }
    fun pushRecent(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        recentQueries.remove(t.lowercase())
        recentQueries.add(0, t)
        // keep cap + de-dup case-insensitive
        val seen = mutableSetOf<String>()
        val deduped = recentQueries.filter { seen.add(it.lowercase()) }
        recentQueries.clear()
        deduped.take(5).forEach { recentQueries.add(it) }
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
        ) {
            if (hasUpdate && !isUpdateDismissed) {
                item(key = "update", contentType = "settings_banner") {
                    SettingsUpdateBanner(
                        latestVersion = latestVersionName,
                        onClick = { navController.navigate("settings/update") },
                        onDismiss = { isUpdateDismissed = true },
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .padding(bottom = SettingsDimensions.SectionSpacing),
                    )
                }
            }

            if (shouldShowPermissionHint) {
                item(key = "permission", contentType = "settings_banner") {
                    SettingsPermissionBanner(
                        onRequestPermission = {
                            val toRequest =
                                buildList {
                                    if (!isStorageGranted) add(storagePermission)
                                    if (!isNotificationGranted && notificationPermission != null) {
                                        add(notificationPermission)
                                    }
                                }
                            if (toRequest.isNotEmpty()) {
                                permissionLauncher.launch(toRequest.toTypedArray())
                            }
                        },
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .padding(bottom = SettingsDimensions.SectionSpacing),
                    )
                }
            }

            stickyHeader(key = "search") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = if (isSearchFocused || query.isNotBlank()) 2.dp else 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                            .padding(top = 4.dp, bottom = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    androidx.compose.material3.IconButton(onClick = {
                                        query = ""
                                        searchFocusRequester.requestFocus()
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = stringResource(R.string.settings_search_clear_desc),
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (trimmedQuery.isNotBlank()) pushRecent(trimmedQuery)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        )
                        // result count — body handles empty state, so header only shows count when has hits
                        AnimatedVisibility(visible = tokens.isNotEmpty() && filteredItems.isNotEmpty()) {
                            Text(
                                text = "${filteredItems.size} ${if (filteredItems.size == 1) "result" else "results"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                            )
                        }
                        @OptIn(ExperimentalLayoutApi::class)
                        AnimatedVisibility(visible = tokens.isEmpty() && recentQueries.isNotEmpty() && isSearchFocused) {
                            FlowRow(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                recentQueries.forEach { recent ->
                                    AssistChip(
                                        onClick = { query = recent },
                                        label = { Text(recent, maxLines = 1) },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.history),
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 2.dp),
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        ),
                                    )
                                }
                                AssistChip(
                                    onClick = { recentQueries.clear() },
                                    label = { Text(stringResource(R.string.settings_search_clear_recent)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (easterEggEmoji != null) {
                item(key = "easteregg", contentType = "settings_easteregg") {
                    Box(Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)) {
                        EmojiEasterEggItem(emoji = easterEggEmoji)
                    }
                }
            }

            if (filteredItems.isEmpty() && tokens.isNotEmpty()) {
                item(key = "no_results", contentType = "settings_empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "🔍",
                            fontSize = 32.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_search_no_results, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_search_try_different),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = rankedItems,
                    key = { _, scored -> scored.item.key },
                    contentType = { _, _ -> "settings_segment" },
                ) { index, scored ->
                    val onClickWithRecent = {
                        if (trimmedQuery.isNotBlank()) pushRecent(trimmedQuery)
                        scored.item.onClick()
                    }
                    SettingsSegmentedItem(
                        item = scored.item.copy(onClick = onClickWithRecent),
                        index = index,
                        count = rankedItems.size,
                        query = queryLower,
                        groupLabel = scored.groupTitle,
                        modifier = Modifier
                            .padding(horizontal = 26.dp)
                            .animateItem(),
                    )
                }
            }
        }
    }
}

internal data class ScoredItem(
    val item: SettingsItem,
    val score: Int,
    val groupTitle: String?,
)

internal fun scoreSettingsItem(item: SettingsItem, tokens: List<String>, groupTitle: String?): Int {
    val title = item.title.lowercase()
    val subtitle = item.subtitle?.lowercase() ?: ""
    val keywords = item.keywords.joinToString(" ").lowercase()
    val key = item.key.lowercase()
    val group = groupTitle?.lowercase() ?: ""
    var total = 0
    for (tok in tokens) {
        var tokScore = 0
        if (title == tok) tokScore += 120
        else if (title.startsWith(tok)) tokScore += 90
        else if (Regex("\\b${Regex.escape(tok)}\\b").containsMatchIn(title)) tokScore += 70
        else if (title.contains(tok)) tokScore += 50

        if (subtitle.contains(tok)) tokScore += 25
        if (keywords.contains(tok)) tokScore += 35
        if (key.contains(tok)) tokScore += 15
        if (group.contains(tok)) tokScore += 10

        // fuzzy: token is prefix of any word in title/keywords
        if (tokScore == 0) {
            val words = (title.split(Regex("\\W+")) + keywords.split(Regex("\\W+"))).filter { it.isNotBlank() }
            if (words.any { it.startsWith(tok) || levenshtein(it, tok) <= 1 }) tokScore += 12
        }
        if (tokScore == 0) return 0 // AND semantics: all tokens must match somewhere
        total += tokScore
    }
    // boost exact multi-token phrase in title
    val phrase = tokens.joinToString(" ")
    if (title.contains(phrase)) total += 40
    return total
}

internal fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]; dp[0] = i
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = if (a[i - 1] == b[j - 1]) prev else minOf(prev, dp[j], dp[j - 1]) + 1
            prev = tmp
        }
    }
    return dp[b.length]
}

private val EmojiEasterEggs: Map<String, String> =
    mapOf(
        "sandwich" to "🥪",
        "sandwitch" to "🥪",
        "burger" to "🍔",
        "pizza" to "🍕",
        "taco" to "🌮",
        "sushi" to "🍣",
        "ramen" to "🍜",
        "cookie" to "🍪",
        "donut" to "🍩",
        "doughnut" to "🍩",
        "cake" to "🍰",
        "coffee" to "☕",
        "banana" to "🍌",
        "watermelon" to "🍉",
        "apple" to "🍎",
        "egg" to "🥚",
        "easter egg" to "🥚",
        "cat" to "🐱",
        "kitty" to "🐱",
        "meow" to "🐱",
        "dog" to "🐶",
        "doggo" to "🐶",
        "woof" to "🐶",
        "frog" to "🐸",
        "duck" to "🦆",
        "penguin" to "🐧",
        "octopus" to "🐙",
        "crab" to "🦀",
        "unicorn" to "🦄",
        "dragon" to "🐉",
        "dino" to "🦖",
        "dinosaur" to "🦖",
        "ghost" to "👻",
        "boo" to "👻",
        "alien" to "👽",
        "robot" to "🤖",
        "ninja" to "🥷",
        "wizard" to "🧙",
        "mage" to "🧙",
        "vampire" to "🧛",
        "zombie" to "🧟",
        "fairy" to "🧚",
        "mermaid" to "🧜",
        "genie" to "🧞",
        "troll" to "🧌",
        "goblin" to "👺",
        "greeny" to "👺",
        "clown" to "🤡",
        "skull" to "💀",
        "dead" to "💀",
        "poop" to "💩",
        "eyes" to "👀",
        "party" to "🎉",
        "fire" to "🔥",
        "lit" to "🔥",
        "rocket" to "🚀",
        "star" to "⭐",
        "moon" to "🌙",
        "sun" to "☀️",
        "rainbow" to "🌈",
        "snowman" to "⛄",
        "heart" to "❤️",
        "love" to "❤️",
        "music" to "🎵",
        "musicnote" to "🎵",
        "guitar" to "🎸",
        "headphone" to "🎧",
        "headphones" to "🎧",
        "microphone" to "🎤",
        "mic" to "🎤",
    )

internal fun findEmojiEasterEgg(rawQuery: String): String? {
    val q = rawQuery.trim().lowercase().replace(Regex("\\s+"), " ")
    if (q.isEmpty()) return null
    return sequenceOf(q, q.removeSuffix("es"), q.removeSuffix("s"))
        .firstNotNullOfOrNull { EmojiEasterEggs[it] }
}

@Composable
private fun EmojiEasterEggItem(emoji: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val infiniteTransition = rememberInfiniteTransition(label = "emojiEgg")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "emojiEggScale",
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "emojiEggRotation",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable {
                    clipboard.setText(AnnotatedString(emoji))
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.settings_easteregg_copied, emoji),
                            Toast.LENGTH_SHORT,
                        ).show()
                }.padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = emoji,
            fontSize = 56.sp,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_easteregg_found),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
