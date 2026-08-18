/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AodThumbnailShape
import moe.rukamori.archivetune.constants.ArtistThumbnailShapeKey
import moe.rukamori.archivetune.constants.RandomThumbnailShapeKey
import moe.rukamori.archivetune.constants.SongThumbnailShapeKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

enum class ThumbnailShapeKind {
    SONG,
    ARTIST,
}

/**
 * A single app-wide "bucket" clock for random thumbnail shapes.
 *
 * The bucket ticks every [BUCKET_MS] (10 minutes). Every artwork derives its random shape from
 * `(stableItemSeed, bucket)`, so:
 * - an item scrolled out of view and back recomputes the SAME shape (no cache/storage needed)
 * - when the bucket flips, every artwork changes shape together (one shared refresh)
 * - the derivation is a pure function, so it survives process restarts and screen transitions
 */
object ThumbnailShapeClock {
    private const val BUCKET_MS = 10 * 60 * 1000L

    private val _bucket = mutableStateOf(System.currentTimeMillis() / BUCKET_MS)
    val bucket: State<Long> = _bucket

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val nextBoundary = ((now / BUCKET_MS) + 1) * BUCKET_MS
                delay(nextBoundary - now)
                _bucket.value = System.currentTimeMillis() / BUCKET_MS
            }
        }
    }

    /**
     * Deterministically picks a shape for `(seed, bucket)`. Same inputs => same output.
     */
    fun stableShapeIndex(seed: String, bucket: Long): Int {
        val seedHash = seed.hashCode().toLong()
        val mixed = seedHash * 31L + bucket * 2654435761L
        return Random(mixed).nextInt(AodThumbnailShape.entries.size)
    }
}

@Composable
fun rememberThumbnailShape(
    kind: ThumbnailShapeKind,
    cornerRadius: Float,
    seed: String? = null,
): Shape {
    val (randomShapes) =
        rememberPreference(
            RandomThumbnailShapeKey,
            defaultValue = true,
        )
    if (randomShapes) {
        LaunchedEffect(Unit) { ThumbnailShapeClock.ensureStarted() }
        val bucket = ThumbnailShapeClock.bucket.value
        val randomShape =
            remember(seed, bucket) {
                if (seed != null) {
                    AodThumbnailShape.entries[ThumbnailShapeClock.stableShapeIndex(seed, bucket)]
                } else {
                    AodThumbnailShape.entries.random()
                }
            }
        return randomShape.toComposeShape(cornerRadius = cornerRadius, startAngle = 0)
    }
    val (shapeType) =
        when (kind) {
            ThumbnailShapeKind.SONG ->
                rememberEnumPreference(
                    SongThumbnailShapeKey,
                    defaultValue = AodThumbnailShape.ROUNDED,
                )

            ThumbnailShapeKind.ARTIST ->
                rememberEnumPreference(
                    ArtistThumbnailShapeKey,
                    defaultValue = AodThumbnailShape.CIRCLE,
                )
        }
    return shapeType.toComposeShape(cornerRadius = cornerRadius, startAngle = 0)
}

fun AodThumbnailShape.supportsArtworkGlowShadow(): Boolean =
    when (this) {
        AodThumbnailShape.FLOWER,
        AodThumbnailShape.CLOVER_4,
        AodThumbnailShape.COOKIE_6,
        AodThumbnailShape.COOKIE_9,
        AodThumbnailShape.SUNNY,
        AodThumbnailShape.SOFT_BURST,
        -> false

        else -> true
    }

@Composable
fun AodThumbnailShape.toComposeShape(
    cornerRadius: Float,
    startAngle: Int,
): Shape =
    when (this) {
        AodThumbnailShape.ROUNDED -> {
            remember(cornerRadius) {
                RoundedCornerShape(cornerRadius.coerceIn(0f, 128f).dp)
            }
        }

        AodThumbnailShape.SQUARE -> {
            MaterialShapes.Square.toShape(startAngle)
        }

        AodThumbnailShape.CIRCLE -> {
            MaterialShapes.Circle.toShape(startAngle)
        }

        AodThumbnailShape.PILL -> {
            MaterialShapes.Pill.toShape(startAngle)
        }

        AodThumbnailShape.ARCH -> {
            MaterialShapes.Arch.toShape(startAngle)
        }

        AodThumbnailShape.SLANTED -> {
            MaterialShapes.Slanted.toShape(startAngle)
        }

        AodThumbnailShape.DIAMOND -> {
            MaterialShapes.Diamond.toShape(startAngle)
        }

        AodThumbnailShape.PENTAGON -> {
            MaterialShapes.Pentagon.toShape(startAngle)
        }

        AodThumbnailShape.TRIANGLE -> {
            MaterialShapes.Triangle.toShape(startAngle)
        }

        AodThumbnailShape.HEART -> {
            MaterialShapes.Heart.toShape(startAngle)
        }

        AodThumbnailShape.FLOWER -> {
            MaterialShapes.Flower.toShape(startAngle)
        }

        AodThumbnailShape.CLOVER_4 -> {
            MaterialShapes.Clover4Leaf.toShape(startAngle)
        }

        AodThumbnailShape.COOKIE_6 -> {
            MaterialShapes.Cookie6Sided.toShape(startAngle)
        }

        AodThumbnailShape.COOKIE_9 -> {
            MaterialShapes.Cookie9Sided.toShape(startAngle)
        }

        AodThumbnailShape.SUNNY -> {
            MaterialShapes.Sunny.toShape(startAngle)
        }

        AodThumbnailShape.SOFT_BURST -> {
            MaterialShapes.SoftBurst.toShape(startAngle)
        }

        AodThumbnailShape.GHOSTISH -> {
            MaterialShapes.Ghostish.toShape(startAngle)
        }

        AodThumbnailShape.PIXEL_CIRCLE -> {
            MaterialShapes.PixelCircle.toShape(startAngle)
        }
    }
