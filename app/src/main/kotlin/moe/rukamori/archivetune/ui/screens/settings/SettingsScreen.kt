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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
    var query by remember { mutableStateOf("") }
    val settingsGroups = buildSettingsGroups(navController, isAndroid12OrLater, hasUpdate, context)
    val settingsItems =
        remember(settingsGroups) {
            settingsGroups.flatMap { it.items }
        }
    val filteredItems = remember(settingsItems, query) {
        if (query.isBlank()) settingsItems
        else {
            val q = query.trim().lowercase()
            settingsItems.filter { item ->
                item.title.lowercase().contains(q) ||
                    (item.subtitle?.lowercase()?.contains(q) == true) ||
                    item.keywords.any { it.lowercase().contains(q) } ||
                    item.key.lowercase().contains(q)
            }
        }
    }
    val easterEggEmoji = remember(query) { findEmojiEasterEgg(query) }

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

            item(key = "search", contentType = "settings_search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                        .padding(bottom = SettingsDimensions.SectionSpacing),
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
                            androidx.compose.material3.IconButton(onClick = { query = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                )
            }

            if (easterEggEmoji != null) {
                item(key = "easteregg", contentType = "settings_easteregg") {
                    Box(Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)) {
                        EmojiEasterEggItem(emoji = easterEggEmoji)
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item(key = "no_results", contentType = "settings_empty") {
                    Text(
                        text = stringResource(R.string.settings_search_no_results, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 16.dp),
                    )
                }
            } else {
                itemsIndexed(
                    items = filteredItems,
                    key = { _, item -> item.key },
                    contentType = { _, _ -> "settings_segment" },
                ) { index, settingsItem ->
                    SettingsSegmentedItem(
                        item = settingsItem,
                        index = index,
                        count = filteredItems.size,
                        modifier = Modifier.padding(horizontal = 26.dp),
                    )
                }
            }
        }
    }
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
