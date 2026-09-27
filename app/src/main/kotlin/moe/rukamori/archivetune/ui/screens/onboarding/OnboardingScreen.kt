/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.onboarding.OnboardingCommunityActionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingEvent
import moe.rukamori.archivetune.onboarding.OnboardingPageId
import moe.rukamori.archivetune.onboarding.OnboardingPermissionAction
import moe.rukamori.archivetune.onboarding.OnboardingPermissionStatus
import moe.rukamori.archivetune.onboarding.OnboardingPermissionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingScreenState
import moe.rukamori.archivetune.onboarding.OnboardingUiState
import moe.rukamori.archivetune.onboarding.OnboardingViewModel

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onPermissionResult()
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onPermissionResult()
        }

    LaunchedEffect(context, viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.RequestPermission -> {
                    permissionLauncher.launch(event.permission)
                }

                OnboardingEvent.OpenInstallPackagesSettings -> {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .setData("package:${context.packageName}".toUri())
                    runCatching {
                        settingsLauncher.launch(intent)
                    }
                }

                is OnboardingEvent.OpenUri -> {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, event.url.toUri()))
                    }
                }
            }
        }
    }

    OnboardingScreen(
        state = state,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onComplete = viewModel::complete,
        onPermissionAction = viewModel::onPermissionAction,
        onCommunityAction = viewModel::onCommunityAction,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingScreenState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OnboardingScrim,
    ) { padding ->
        when (state) {
            OnboardingScreenState.Loading -> {
                LoadingContent(contentPadding = padding)
            }

            OnboardingScreenState.Empty -> {
                MessageContent(
                    title = stringResource(R.string.onboarding_empty_title),
                    subtitle = stringResource(R.string.onboarding_empty_subtitle),
                    actionLabel = stringResource(R.string.onboarding_finish),
                    onAction = onComplete,
                    contentPadding = padding,
                )
            }

            is OnboardingScreenState.Error -> {
                MessageContent(
                    title = stringResource(state.messageResId),
                    subtitle = stringResource(R.string.onboarding_empty_subtitle),
                    actionLabel = stringResource(R.string.onboarding_finish),
                    onAction = onComplete,
                    contentPadding = padding,
                )
            }

            is OnboardingScreenState.Success -> {
                OnboardingSuccessContent(
                    uiState = state.uiState,
                    onNext = onNext,
                    onBack = onBack,
                    onPermissionAction = onPermissionAction,
                    onCommunityAction = onCommunityAction,
                    contentPadding = padding,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun MessageContent(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = OnboardingContentMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun OnboardingSuccessContent(
    uiState: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    contentPadding: PaddingValues,
) {
    val pagerState =
        rememberPagerState(
            initialPage = uiState.currentPage,
            pageCount = { uiState.pages.size },
        )

    LaunchedEffect(uiState.currentPage, uiState.pages.size) {
        val targetPage = uiState.currentPage.coerceIn(0, uiState.pages.lastIndex)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Full-bleed: the glow runs under the status and navigation bars,
        // content keeps the Scaffold insets.
        OnboardingAmbientBackground(showMesh = uiState.currentPage > 0)
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) { pageIndex ->
        when (uiState.pages[pageIndex].id) {
            OnboardingPageId.WELCOME -> {
                WelcomePage(
                    uiState = uiState,
                    pageIndex = pageIndex,
                    onBack = onBack,
                    onNext = onNext,
                )
            }

            OnboardingPageId.PERMISSIONS -> {
                PermissionsPage(
                    uiState = uiState,
                    pageIndex = pageIndex,
                    onBack = onBack,
                    onNext = onNext,
                    onPermissionAction = onPermissionAction,
                )
            }

            OnboardingPageId.COMMUNITY -> {
                CommunityPage(
                    uiState = uiState,
                    pageIndex = pageIndex,
                    onBack = onBack,
                    onNext = onNext,
                    onCommunityAction = onCommunityAction,
                )
            }
        }
        }
    }
}

@Composable
private fun WelcomePage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val page = uiState.pages[pageIndex]

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = OnboardingContentMaxWidth)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(page.titleResId),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(page.subtitleResId),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.72f),
            )
            OnboardingMetadataPills(uiState = uiState)
        }
        OnboardingInlineActions(
            currentPage = pageIndex,
            pageCount = uiState.pages.size,
            onBack = onBack,
            onNext = onNext,
        )
    }
}

/** Sunset glow shared by every onboarding page: maroon base, red-orange halo, amber core. */
@Composable
private fun OnboardingAmbientBackground(
    showMesh: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(OnboardingScrim),
    ) {
        val density = LocalDensity.current
        val haloCenter =
            with(density) {
                Offset(maxWidth.toPx() * 0.62f, maxHeight.toPx() * 0.14f)
            }
        val haloRadius = with(density) { maxWidth.toPx() * 1.05f }
        val coreCenter =
            with(density) {
                Offset(maxWidth.toPx() * 0.5f, maxHeight.toPx() * 0.30f)
            }
        val coreRadius = with(density) { maxWidth.toPx() * 0.62f }
        val lowCenter =
            with(density) {
                Offset(maxWidth.toPx() * 0.08f, maxHeight.toPx() * 0.94f)
            }
        val lowRadius = with(density) { maxWidth.toPx() * 0.8f }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val meshAlpha by animateFloatAsState(
            targetValue = if (showMesh) 1f else 0f,
            animationSpec = tween(durationMillis = 600),
            label = "onboardingMesh",
        )

        Box(
            modifier =
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0f to OnboardingHalo.copy(alpha = 0.85f),
                        0.45f to OnboardingHalo.copy(alpha = 0.32f),
                        1f to Color.Transparent,
                        center = haloCenter,
                        radius = haloRadius,
                    ),
                ),
        )
        Box(
            modifier =
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0f to OnboardingCore.copy(alpha = 0.9f),
                        0.5f to OnboardingCore.copy(alpha = 0.28f),
                        1f to Color.Transparent,
                        center = coreCenter,
                        radius = coreRadius,
                    ),
                ),
        )
        Box(
            modifier =
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0f to OnboardingHalo.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                        center = lowCenter,
                        radius = lowRadius,
                    ),
                ),
        )
        // Dense blob mesh, faded in from the second page on: overlapping
        // shapes melting into the dark, same recipe as the base glow.
        if (meshAlpha > 0.01f) {
            val meshBlobs =
                listOf(
                    Triple(0.28f to 0.10f, 0.55f, OnboardingHalo),
                    Triple(0.74f to 0.05f, 0.50f, OnboardingCore),
                    Triple(0.55f to 0.24f, 0.60f, OnboardingEmber),
                    Triple(0.10f to 0.30f, 0.45f, OnboardingHalo),
                )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = meshAlpha },
            ) {
                meshBlobs.forEach { (position, radiusFraction, color) ->
                    val center = Offset(widthPx * position.first, heightPx * position.second)
                    val radius = widthPx * radiusFraction
                    Box(
                        modifier =
                            Modifier.fillMaxSize().background(
                                Brush.radialGradient(
                                    0f to color,
                                    0.55f to color.copy(alpha = 0.6f),
                                    1f to Color.Transparent,
                                    center = center,
                                    radius = radius,
                                ),
                            ),
                    )
                }
            }
        }
        // Darken the bottom so body copy stays legible over the glow.
        Box(
            modifier =
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                    ),
                ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingMetadataPills(uiState: OnboardingUiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PassivePill(text = stringResource(uiState.variantLabelResId))
        PassivePill(
            text =
                stringResource(
                    R.string.onboarding_version_label,
                    uiState.versionName,
                ),
        )
    }
}

@Composable
private fun PassivePill(text: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White.copy(alpha = 0.88f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionsPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val page = uiState.pages[pageIndex]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = OnboardingPagePadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = page.id.name, contentType = "header") {
            ExpressivePageHeader(
                iconResId = page.iconResId,
                titleResId = page.titleResId,
                subtitleResId = page.subtitleResId,
            )
        }
        itemsIndexed(
            items = uiState.permissions,
            key = { _, item -> item.id.name },
            contentType = { _, item -> "permission-${item.id.name}" },
        ) { index, item ->
            PermissionRow(
                permission = item,
                index = index,
                count = uiState.permissions.size,
                onPermissionAction = onPermissionAction,
            )
        }
        item(key = "permission-actions", contentType = "actions") {
            OnboardingInlineActions(
                currentPage = pageIndex,
                pageCount = uiState.pages.size,
                onBack = onBack,
                onNext = onNext,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommunityPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val page = uiState.pages[pageIndex]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = OnboardingPagePadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = page.id.name, contentType = "header") {
            ExpressivePageHeader(
                iconResId = page.iconResId,
                titleResId = page.titleResId,
                subtitleResId = page.subtitleResId,
            )
        }
        item(key = "community-spotlight", contentType = "spotlight") {
            CommunitySpotlight(actions = uiState.communityActions)
        }
        itemsIndexed(
            items = uiState.communityActions,
            key = { _, item -> item.id },
            contentType = { _, item -> "community-${item.id}" },
        ) { index, item ->
            CommunityRow(
                action = item,
                index = index,
                count = uiState.communityActions.size,
                onCommunityAction = onCommunityAction,
            )
        }
        item(key = "community-actions", contentType = "actions") {
            OnboardingInlineActions(
                currentPage = pageIndex,
                pageCount = uiState.pages.size,
                onBack = onBack,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun CommunitySpotlight(actions: ImmutableList<OnboardingCommunityActionUiModel>) {
    Surface(
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.White.copy(alpha = 0.1f),
        contentColor = Color.White,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            actions.forEach { action ->
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    shape = MaterialTheme.shapes.large,
                    color = OnboardingCore,
                    contentColor = OnboardingEmber,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(action.iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressivePageHeader(
    iconResId: Int,
    titleResId: Int,
    subtitleResId: Int,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = MaterialShapes.Sunny.toShape(0),
            color = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        LargePageTitle(titleResId = titleResId, subtitleResId = subtitleResId)
    }
}

@Composable
private fun LargePageTitle(
    titleResId: Int,
    subtitleResId: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(subtitleResId),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionRow(
    permission: OnboardingPermissionUiModel,
    index: Int,
    count: Int,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val onClick =
        remember(permission.action, onPermissionAction) {
            {
                val action = permission.action
                if (action != null) {
                    onPermissionAction(action)
                }
            }
        }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .heightIn(min = 88.dp),
        colors = ListItemDefaults.segmentedColors(containerColor = Color.White.copy(alpha = 0.1f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        leadingContent = {
            PermissionIcon(permission = permission)
        },
        supportingContent = {
            Text(
                text = stringResource(permission.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        },
        trailingContent = {
            PermissionStatusAction(
                permission = permission,
                onPermissionAction = onPermissionAction,
            )
        },
    ) {
        Text(
            text = stringResource(permission.titleResId),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun PermissionIcon(permission: OnboardingPermissionUiModel) {
    val containerColor =
        when (permission.status) {
            OnboardingPermissionStatus.ALLOWED -> Color.White
            OnboardingPermissionStatus.NEEDS_ACTION -> OnboardingCore
            OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> Color.White.copy(alpha = 0.14f)
            OnboardingPermissionStatus.UNAVAILABLE -> Color.White.copy(alpha = 0.08f)
        }
    val contentColor =
        when (permission.status) {
            OnboardingPermissionStatus.ALLOWED -> OnboardingEmber
            OnboardingPermissionStatus.NEEDS_ACTION -> OnboardingEmber
            OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> Color.White
            OnboardingPermissionStatus.UNAVAILABLE -> Color.White.copy(alpha = 0.5f)
        }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(permission.iconResId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PermissionStatusAction(
    permission: OnboardingPermissionUiModel,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val action = permission.action

    if (action != null) {
        FilledTonalButton(
            onClick = { onPermissionAction(action) },
            shapes = ButtonDefaults.shapes(),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF221206),
                ),
            contentPadding = ButtonDefaults.SmallContentPadding,
        ) {
            Text(text = stringResource(R.string.allow))
        }
    } else {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White.copy(alpha = 0.88f),
        ) {
            Text(
                text = stringResource(permission.status.labelResId()),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommunityRow(
    action: OnboardingCommunityActionUiModel,
    index: Int,
    count: Int,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val onClick = remember(action, onCommunityAction) { { onCommunityAction(action) } }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .heightIn(min = 88.dp),
        colors = ListItemDefaults.segmentedColors(containerColor = Color.White.copy(alpha = 0.1f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        leadingContent = {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = OnboardingCore,
                contentColor = OnboardingEmber,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(action.iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = stringResource(action.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
            )
        },
    ) {
        Text(
            text = stringResource(action.titleResId),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun OnboardingInlineActions(
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val showBack = currentPage > 0
    val isLastPage = currentPage >= pageCount - 1
    val nextLabel =
        if (isLastPage) {
            stringResource(R.string.onboarding_finish)
        } else {
            stringResource(R.string.next)
        }

    Column(
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 24.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        OnboardingPageDots(
            currentPage = currentPage,
            pageCount = pageCount,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(
            visible = !showBack,
            enter =
                expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit =
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            OnboardingNextButton(
                text = nextLabel,
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            visible = showBack,
            enter =
                expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit =
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingGhostButton(
                    text = stringResource(R.string.back_button_desc),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                OnboardingNextButton(
                    text = nextLabel,
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageDots(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 3.dp)
                        .then(
                            if (active) {
                                Modifier.size(width = 22.dp, height = 6.dp)
                            } else {
                                Modifier.size(6.dp)
                            },
                        ).background(
                            color =
                                if (active) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.3f)
                                },
                            shape = CircleShape,
                        ),
            )
        }
    }
}

@Composable
private fun OnboardingNextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF221206),
            ),
        contentPadding = OnboardingActionButtonPadding,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OnboardingGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
            ),
        contentPadding = OnboardingActionButtonPadding,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun OnboardingPermissionStatus.labelResId(): Int =
    when (this) {
        OnboardingPermissionStatus.ALLOWED -> R.string.permission_status_allowed
        OnboardingPermissionStatus.NEEDS_ACTION -> R.string.allow
        OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> R.string.onboarding_permission_allowed_by_install
        OnboardingPermissionStatus.UNAVAILABLE -> R.string.onboarding_permission_unavailable
    }

private val OnboardingContentMaxWidth = 680.dp
private val OnboardingPagePadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
private val OnboardingActionButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
private val OnboardingScrim = Color(0xFF260600)
private val OnboardingHalo = Color(0xFFE8480C)
private val OnboardingCore = Color(0xFFFFC53D)
private val OnboardingEmber = Color(0xFF7A1500)
