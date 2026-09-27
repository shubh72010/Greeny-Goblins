/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.GlassComponent
import moe.rukamori.archivetune.ui.component.LocalGlassEffectConfig
import moe.rukamori.archivetune.ui.component.isGlassAllowed
import moe.rukamori.archivetune.ui.component.liquidGlass
import moe.rukamori.archivetune.ui.screens.Screens

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    onMusicTogetherClick: (() -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val toolbarContainerColor = if (useGlass) Color.Transparent else floatingToolbarContainerColor(pureBlack = pureBlack)
    val toolbarColors =
        FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = toolbarContainerColor,
        )
    val hasOverflowAction = onShuffleClick != null && shuffleIconRes != null
    val refractedConfig = remember(glassConfig) { glassConfig.copy(lensHeight = 1f, lensAmount = 1f, chromaticAberration = true, depthEffect = true, surfaceOpacity = 0.3f, vibrancy = 1f) }
    val glassModifier = if (useGlass) Modifier.liquidGlass(refractedConfig, shape = RoundedCornerShape(percent = 50), blurRadiusDp = 2f, highlightAlpha = 0.85f) else Modifier

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val showSelectedLabels = maxWidth >= 360.dp

        if (hasOverflowAction) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarOverflowAction(
                        pureBlack = pureBlack,
                        onShuffleClick = onShuffleClick,
                        shuffleIconRes = shuffleIconRes,
                        shuffleContentDescription = shuffleContentDescription,
                        onMusicRecognitionClick = onMusicRecognitionClick,
                        musicRecognitionContentDescription = musicRecognitionContentDescription,
                        onMusicTogetherClick = onMusicTogetherClick,
                    )
                },
                modifier = Modifier.widthIn(max = 480.dp).shadow(12.dp, RoundedCornerShape(percent = 50)).then(glassModifier),
                colors = toolbarColors,
                scrollBehavior = scrollBehavior,
                animationSpec = FloatingToolbarDefaults.animationSpec(),
            ) {
                ToolbarItemsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    showSelectedLabels = showSelectedLabels,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    onSearchItemDoubleClick = onSearchItemDoubleClick,
                )
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.widthIn(max = 420.dp).shadow(12.dp, RoundedCornerShape(percent = 50)).then(glassModifier),
                colors = toolbarColors,
                scrollBehavior = scrollBehavior,
            ) {
                ToolbarItemsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    showSelectedLabels = showSelectedLabels,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    onSearchItemDoubleClick = onSearchItemDoubleClick,
                )
            }
        }
    }
}

@Composable
private fun ToolbarItemsContainer(
    items: List<Screens>,
    pureBlack: Boolean,
    showSelectedLabels: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEach { screen ->
            val selected = isSelected(screen)
            val onClick =
                remember(screen, selected, onItemClick) {
                    { onItemClick(screen, selected) }
                }
            val onDoubleClick =
                remember(screen, onSearchItemDoubleClick) {
                    if (screen == Screens.Search) {
                        onSearchItemDoubleClick
                    } else {
                        null
                    }
                }
            FloatingNavigationToolbarItem(
                screen = screen,
                selected = selected,
                showLabel = selected && showSelectedLabels,
                pureBlack = pureBlack,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            )
        }
    }
}

@Composable
private fun FloatingToolbarOverflowAction(
    pureBlack: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
    onMusicTogetherClick: (() -> Unit)?,
) {
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val glassConfig = LocalGlassEffectConfig.current
    val useFabGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val fabGlassConfig = remember(glassConfig) { glassConfig.copy(lensHeight = 1f, lensAmount = 1f, chromaticAberration = true, depthEffect = true, surfaceOpacity = 0.3f, vibrancy = 1f) }

    Box {
        Box(
            modifier = if (useFabGlass) Modifier.liquidGlass(fabGlassConfig, shape = CircleShape, blurRadiusDp = 2f, highlightAlpha = 0.85f) else Modifier
        ) {
        FloatingToolbarDefaults.VibrantFloatingActionButton(
            onClick = { fabMenuExpanded = !fabMenuExpanded },
            containerColor = if (useFabGlass) Color.Transparent else floatingToolbarFabContainerColor(),
            contentColor = floatingToolbarFabContentColor(),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription =
                    shuffleContentDescription.ifEmpty {
                        stringResource(R.string.more)
                    },
            )
        }
        }

        DropdownMenu(
            expanded = fabMenuExpanded,
            onDismissRequest = { fabMenuExpanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_recognition)) },
                onClick = {
                    fabMenuExpanded = false
                    onMusicRecognitionClick?.invoke()
                },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                        contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.mic),
                                contentDescription =
                                    musicRecognitionContentDescription.ifEmpty {
                                        stringResource(R.string.music_recognition)
                                    },
                            )
                        }
                    }
                },
                enabled = onMusicRecognitionClick != null,
                colors =
                    MenuDefaults.itemColors(
                        textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = if (pureBlack) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        disabledLeadingIconColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                    ),
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_together)) },
                onClick = {
                    fabMenuExpanded = false
                    onMusicTogetherClick?.invoke()
                },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                        contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.multi_user),
                                contentDescription = null,
                            )
                        }
                    }
                },
                enabled = onMusicTogetherClick != null,
                colors =
                    MenuDefaults.itemColors(
                        textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = if (pureBlack) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        disabledLeadingIconColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                    ),
            )

            if (onShuffleClick != null && shuffleIconRes != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    onClick = {
                        fabMenuExpanded = false
                        onShuffleClick()
                    },
                    leadingIcon = {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                            contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(shuffleIconRes),
                                    contentDescription =
                                        shuffleContentDescription.ifEmpty {
                                            stringResource(R.string.shuffle)
                                        },
                                )
                            }
                        }
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                            leadingIconColor =
                                if (pureBlack) {
                                    Color.White.copy(
                                        alpha = 0.82f,
                                    )
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        ),
                )
            }
        }
    }
}

@Composable
private fun FloatingNavigationToolbarItem(
    screen: Screens,
    selected: Boolean,
    showLabel: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = selected, label = "navItem_${screen.route}")

    val contentColor by transition.animateColor(
        transitionSpec = { spring(stiffness = Spring.StiffnessMedium) },
        label = "contentColor",
    ) { isSelected ->
        if (isSelected) {
            floatingToolbarSelectedItemContentColor(pureBlack)
        } else {
            floatingToolbarItemContentColor(pureBlack)
        }
    }

    val containerColor by transition.animateColor(
        transitionSpec = { spring(stiffness = Spring.StiffnessMedium) },
        label = "containerColor",
    ) { isSelected ->
        if (isSelected) {
            floatingToolbarSelectedItemContainerColor(pureBlack)
        } else {
            Color.Transparent
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "pressScale",
    )

    Row(
        modifier =
            modifier
                .scale(pressScale)
                .clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Tab,
                    onClick = onClick,
                    onDoubleClick = onDoubleClick,
                ).widthIn(min = 68.dp)
                .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(
            targetState = selected,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "iconCrossfade",
        ) { isSelected ->
            Icon(
                painter = painterResource(if (isSelected) screen.iconIdActive else screen.iconIdInactive),
                contentDescription = stringResource(screen.titleId),
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
        }

        AnimatedVisibility(
            visible = showLabel,
            enter =
                fadeIn(
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) +
                    expandHorizontally(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        expandFrom = Alignment.Start,
                    ),
            exit =
                fadeOut(
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) +
                    shrinkHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        shrinkTowards = Alignment.Start,
                    ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(screen.titleId),
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun floatingToolbarContainerColor(pureBlack: Boolean): Color =
    if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

@Composable
private fun floatingToolbarFabContainerColor(): Color = MaterialTheme.colorScheme.primary

@Composable
private fun floatingToolbarFabContentColor(): Color = MaterialTheme.colorScheme.onPrimary

@Composable
private fun floatingToolbarSelectedItemContainerColor(pureBlack: Boolean): Color {
    if (navGlassActive()) {
        // Light frost segment floating on the glass bar, like bright reflective glass.
        return Color.White.copy(alpha = 0.25f)
    }
    return if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun floatingToolbarSelectedItemContentColor(pureBlack: Boolean): Color {
    if (navGlassActive()) {
        return Color.White
    }
    return if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun floatingToolbarItemContentColor(pureBlack: Boolean): Color {
    if (navGlassActive()) {
        return Color.White.copy(alpha = 0.65f)
    }
    if (pureBlack) {
        return Color.White.copy(alpha = 0.82f)
    }
    return MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun navGlassActive(): Boolean =
    LocalGlassEffectConfig.current.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()

@Composable
private fun floatingToolbarMenuIconContainerColor(pureBlack: Boolean): Color =
    if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer

@Composable
private fun floatingToolbarMenuIconContentColor(pureBlack: Boolean): Color =
    if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
