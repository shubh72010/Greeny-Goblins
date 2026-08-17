/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encodes an MP4 from frames produced by [renderFrame] plus a clipped section of an
 * audio stream, using MediaCodec + MediaMuxer directly (no Media3 dependency).
 *
 * Frames are drawn into a Canvas at video-time [Long] microseconds; audio is decoded
 * from [audioStreamUrl] between [audioStartUs] and [audioStartUs] + [durationUs], then
 * re-encoded to AAC so it can live in the MP4 container.
 */
object LyricsVideoExporter {
    private const val FRAME_RATE = 24
    private const val VIDEO_BIT_RATE = 8_000_000
    private const val IFRAME_INTERVAL = 2
    private const val AAC_BIT_RATE = 192_000
    private const val AAC_SAMPLES_PER_FRAME = 1024

    private const val MODE_FLEXIBLE = 0
    private const val MODE_PLANAR = 1
    private const val MODE_SEMI_PLANAR = 2
    private const val MODE_SURFACE = 3

    data class ExportResult(
        val uri: Uri,
    )

    suspend fun exportVideo(
        context: Context,
        width: Int,
        height: Int,
        durationUs: Long,
        audioStreamUrl: String,
        audioStartUs: Long,
        renderFrame: (Canvas, Long) -> Unit,
        onProgress: (Float) -> Unit,
    ): ExportResult =
        withContext(Dispatchers.Default) {
            val safeWidth = width.coerceAtLeast(2)
            val safeHeight = height.coerceAtLeast(2)
            val frameDurationUs = 1_000_000L / FRAME_RATE
            val totalFrames = ((durationUs + frameDurationUs - 1) / frameDurationUs).toInt().coerceAtLeast(1)
            val safeStartUs = audioStartUs.coerceAtLeast(0L)
            val endUs = safeStartUs + durationUs

            val (pcmBytes, sampleRate, channelCount) = decodeAudioToPcm(audioStreamUrl, safeStartUs, endUs)
            if (pcmBytes.isEmpty()) {
                error("Audio stream contained no samples in the requested clip")
            }

            val fileName = "lyrics_video_${System.currentTimeMillis()}"
            val (outputUri, outputStream) = openOutput(context, fileName)
            var muxer: MediaMuxer? = null
            var videoEncoder: MediaCodec? = null
            var audioEncoder: MediaCodec? = null

            try {
                val muxerRef = MediaMuxer(outputStream.fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = muxerRef

                val videoSetup = createVideoEncoder(safeWidth, safeHeight)
                videoEncoder = videoSetup.encoder
                val videoMode = videoSetup.mode
                val videoSurface = videoSetup.inputSurface

                val audioEncoderRef =
                    MediaCodec
                        .createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                        .apply {
                            configure(
                                MediaFormat
                                    .createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
                                    .apply {
                                        setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
                                        setInteger(
                                            MediaFormat.KEY_AAC_PROFILE,
                                            MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                                        )
                                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
                                    },
                                null,
                                null,
                                MediaCodec.CONFIGURE_FLAG_ENCODE,
                            )
                            start()
                        }
                audioEncoder = audioEncoderRef

                val videoTrackIndex = muxerRef.addTrack(videoEncoder!!.outputFormat)
                val audioTrackIndex = muxerRef.addTrack(audioEncoderRef.outputFormat)
                muxerRef.start()

                val pcmArray = pcmBytes
                val pcmChunkSize = AAC_SAMPLES_PER_FRAME * channelCount * 2
                val yuvScratch = ByteArray(max(safeWidth, (safeWidth + 1) / 2) * 2)
                val yBytes = ByteArray(safeWidth * safeHeight)
                val uvBytes = ByteArray(((safeWidth + 1) / 2) * ((safeHeight + 1) / 2) * 2)
                val frameBitmap =
                    if (videoMode == MODE_SURFACE) {
                        null
                    } else {
                        Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
                    }
                val framePixels = IntArray(safeWidth * safeHeight)

                val videoInfo = MediaCodec.BufferInfo()
                val audioInfo = MediaCodec.BufferInfo()

                var frameIndex = 0
                var lastVideoPtsUs = 0L
                var videoEosQueued = false
                var videoDone = false

                var pcmPosition = 0
                var audioChunkIndex = 0
                var lastAudioPtsUs = 0L
                var audioEosQueued = false
                var audioDone = false

                var safetyIterations = 0
                val maxSafetyIterations = totalFrames * 20L + 500_000L

                while (!videoDone || !audioDone) {
                    if (++safetyIterations > maxSafetyIterations) {
                        error("Video export did not finish in time")
                    }

                    if (!videoEosQueued) {
                        val inputIndex = videoEncoder!!.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            if (frameIndex < totalFrames) {
                                val ptsUs = frameIndex * frameDurationUs
                                lastVideoPtsUs = ptsUs
                                queueVideoFrame(
                                    encoder = videoEncoder!!,
                                    inputIndex = inputIndex,
                                    mode = videoMode,
                                    inputSurface = videoSurface,
                                    frameBitmap = frameBitmap,
                                    framePixels = framePixels,
                                    width = safeWidth,
                                    height = safeHeight,
                                    ptsUs = ptsUs,
                                    renderFrame = renderFrame,
                                    yuvScratch = yuvScratch,
                                    yBytes = yBytes,
                                    uvBytes = uvBytes,
                                )
                                frameIndex++
                                if (frameIndex % 12 == 0) {
                                    onProgress(frameIndex.toFloat() / totalFrames)
                                }
                            } else {
                                videoEncoder!!.queueInputBuffer(inputIndex, 0, 0, lastVideoPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                videoEosQueued = true
                            }
                        }
                    }

                    drainEncoder(
                        encoder = videoEncoder!!,
                        muxer = muxerRef,
                        trackIndex = videoTrackIndex,
                        info = videoInfo,
                    ) { eos -> videoDone = eos }

                    if (!audioEosQueued) {
                        val inputIndex = audioEncoderRef.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            if (pcmPosition < pcmArray.size) {
                                val buffer = audioEncoderRef.getInputBuffer(inputIndex)!!
                                buffer.clear()
                                val bytes = minOf(pcmChunkSize, pcmArray.size - pcmPosition)
                                buffer.put(pcmArray, pcmPosition, bytes)
                                val ptsUs = audioChunkIndex * AAC_SAMPLES_PER_FRAME * 1_000_000L / sampleRate
                                lastAudioPtsUs = ptsUs
                                audioEncoderRef.queueInputBuffer(inputIndex, 0, bytes, ptsUs, 0)
                                pcmPosition += bytes
                                audioChunkIndex++
                            } else {
                                audioEncoderRef.queueInputBuffer(inputIndex, 0, 0, lastAudioPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                audioEosQueued = true
                            }
                        }
                    }

                    drainEncoder(
                        encoder = audioEncoderRef,
                        muxer = muxerRef,
                        trackIndex = audioTrackIndex,
                        info = audioInfo,
                    ) { eos -> audioDone = eos }
                }

                onProgress(1f)
            } finally {
                runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
                runCatching { videoEncoder?.stop() }
                runCatching { videoEncoder?.release() }
                runCatching { audioEncoder?.stop() }
                runCatching { audioEncoder?.release() }
                runCatching { outputStream.close() }
            }

            ExportResult(uri = outputUri)
        }

    private fun openOutput(
        context: Context,
        fileName: String,
    ): Pair<Uri, FileOutputStream> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.mp4")
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/JusPlayer")
                }
            val uri =
                context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: error("Failed to create MediaStore record")
            val fd = context.contentResolver.openFileDescriptor(uri, "rw") ?: error("Failed to open output")
            uri to FileOutputStream(fd.fileDescriptor)
        } else {
            val cacheDir = File(context.cacheDir, "videos")
            cacheDir.mkdirs()
            val file = File(cacheDir, "$fileName.mp4")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
            uri to FileOutputStream(file)
        }

    private class VideoEncoderSetup(
        val encoder: MediaCodec,
        val mode: Int,
        val inputSurface: android.view.Surface?,
    )

    private fun createVideoEncoder(
        width: Int,
        height: Int,
    ): VideoEncoderSetup {
        val baseFormat =
            MediaFormat
                .createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
                .apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
                }
        val encoderName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(baseFormat)
        require(encoderName.isNotBlank()) { "No H.264 encoder available" }

        val codecInfo =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { it.name == encoderName }
                ?: error("Encoder $encoderName disappeared")
        val supportedFormats = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats.toSet()

        val (mode, colorFormat) =
            when {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in supportedFormats ->
                    MODE_FLEXIBLE to MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible

                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in supportedFormats ->
                    MODE_PLANAR to MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in supportedFormats ->
                    MODE_SEMI_PLANAR to MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in supportedFormats ->
                    MODE_SURFACE to MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface

                else -> MODE_FLEXIBLE to MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            }

        val encoder =
            MediaCodec
                .createByCodecName(encoderName)
                .apply {
                    configure(
                        baseFormat.apply { setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat) },
                        null,
                        null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE,
                    )
                    start()
                }

        val surface = if (mode == MODE_SURFACE) encoder.createInputSurface() else null
        return VideoEncoderSetup(encoder, mode, surface)
    }

    private fun queueVideoFrame(
        encoder: MediaCodec,
        inputIndex: Int,
        mode: Int,
        inputSurface: android.view.Surface?,
        frameBitmap: Bitmap?,
        framePixels: IntArray,
        width: Int,
        height: Int,
        ptsUs: Long,
        renderFrame: (Canvas, Long) -> Unit,
        yuvScratch: ByteArray,
        yBytes: ByteArray,
        uvBytes: ByteArray,
    ) {
        when (mode) {
            MODE_SURFACE -> {
                val canvas = inputSurface!!.lockCanvas(null)
                renderFrame(canvas, ptsUs)
                inputSurface.unlockCanvasAndPost(canvas)
            }

            MODE_FLEXIBLE -> {
                val bitmap = frameBitmap!!
                val canvas = Canvas(bitmap)
                renderFrame(canvas, ptsUs)
                bitmap.getPixels(framePixels, 0, width, 0, 0, width, height)
                val image = encoder.getInputImage(inputIndex)
                val size = fillFlexibleImage(image!!, framePixels, width, height, yuvScratch)
                encoder.queueInputBuffer(inputIndex, 0, size, ptsUs, 0)
            }

            MODE_PLANAR -> {
                val bitmap = frameBitmap!!
                val canvas = Canvas(bitmap)
                renderFrame(canvas, ptsUs)
                bitmap.getPixels(framePixels, 0, width, 0, 0, width, height)
                val buffer = encoder.getInputBuffer(inputIndex)!!
                buffer.clear()
                writePlanarYuv(buffer, framePixels, width, height, yBytes, uvBytes)
                encoder.queueInputBuffer(inputIndex, 0, planarSize(width, height), ptsUs, 0)
            }

            MODE_SEMI_PLANAR -> {
                val bitmap = frameBitmap!!
                val canvas = Canvas(bitmap)
                renderFrame(canvas, ptsUs)
                bitmap.getPixels(framePixels, 0, width, 0, 0, width, height)
                val buffer = encoder.getInputBuffer(inputIndex)!!
                buffer.clear()
                writeSemiPlanarYuv(buffer, framePixels, width, height, yBytes, uvBytes)
                encoder.queueInputBuffer(inputIndex, 0, planarSize(width, height), ptsUs, 0)
            }
        }
    }

    private fun planarSize(width: Int, height: Int): Int {
        val halfW = (width + 1) / 2
        val halfH = (height + 1) / 2
        return width * height + halfW * halfH * 2
    }

    private fun rgbToY(pixel: Int): Int {
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        return ((Y_R[r] + Y_G[g] + Y_B[b] + 128) shr 8) + 16
    }

    private fun rgbToU(pixel: Int): Int {
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        return ((U_R[r] + U_G[g] + U_B[b] + 128) shr 8) + 128
    }

    private fun rgbToV(pixel: Int): Int {
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        return ((V_R[r] + V_G[g] + V_B[b] + 128) shr 8) + 128
    }

    private val Y_R = IntArray(256) { it * 66 }
    private val Y_G = IntArray(256) { it * 129 }
    private val Y_B = IntArray(256) { it * 25 }
    private val U_R = IntArray(256) { it * -38 }
    private val U_G = IntArray(256) { it * -74 }
    private val U_B = IntArray(256) { it * 112 }
    private val V_R = IntArray(256) { it * 112 }
    private val V_G = IntArray(256) { it * -94 }
    private val V_B = IntArray(256) { it * -18 }

    private fun writePlanarYuv(
        buffer: ByteBuffer,
        pixels: IntArray,
        width: Int,
        height: Int,
        yBytes: ByteArray,
        uvBytes: ByteArray,
    ) {
        for (i in pixels.indices) yBytes[i] = rgbToY(pixels[i]).toByte()
        val halfW = (width + 1) / 2
        val halfH = (height + 1) / 2
        var uv = 0
        for (row in 0 until halfH) {
            val srcRow = row * 2
            for (col in 0 until halfW) {
                val pixel = pixels[srcRow * width + col * 2]
                uvBytes[uv++] = rgbToU(pixel).toByte()
            }
        }
        for (row in 0 until halfH) {
            val srcRow = row * 2
            for (col in 0 until halfW) {
                uvBytes[uv++] = rgbToV(pixels[srcRow * width + col * 2]).toByte()
            }
        }
        buffer.rewind()
        buffer.put(yBytes)
        buffer.put(uvBytes)
    }

    private fun writeSemiPlanarYuv(
        buffer: ByteBuffer,
        pixels: IntArray,
        width: Int,
        height: Int,
        yBytes: ByteArray,
        uvBytes: ByteArray,
    ) {
        for (i in pixels.indices) yBytes[i] = rgbToY(pixels[i]).toByte()
        val halfW = (width + 1) / 2
        val halfH = (height + 1) / 2
        var uv = 0
        for (row in 0 until halfH) {
            val srcRow = row * 2
            for (col in 0 until halfW) {
                val pixel = pixels[srcRow * width + col * 2]
                uvBytes[uv++] = rgbToU(pixel).toByte()
                uvBytes[uv++] = rgbToV(pixel).toByte()
            }
        }
        buffer.rewind()
        buffer.put(yBytes)
        buffer.put(uvBytes)
    }

    private fun fillFlexibleImage(
        image: android.media.Image,
        pixels: IntArray,
        width: Int,
        height: Int,
        scratch: ByteArray,
    ): Int {
        val planes = image.planes
        return when (planes.size) {
            3 -> {
                fillPlane(planes[0], pixels, width, height, isChroma = false, scratch)
                fillPlane(planes[1], pixels, width, height, isChroma = true, scratch)
                fillPlane(planes[2], pixels, width, height, isChroma = true, scratch)
                planes[0].rowStride * height + planes[1].rowStride * ((height + 1) / 2) + planes[2].rowStride * ((height + 1) / 2)
            }

            2 -> {
                fillPlane(planes[0], pixels, width, height, isChroma = false, scratch)
                fillInterleavedPlane(planes[1], pixels, width, height, scratch)
                planes[0].rowStride * height + planes[1].rowStride * ((height + 1) / 2)
            }

            else -> error("Unexpected image plane count: ${planes.size}")
        }
    }

    private fun fillPlane(
        plane: android.media.Image.Plane,
        pixels: IntArray,
        width: Int,
        height: Int,
        isChroma: Boolean,
        scratch: ByteArray,
    ) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (isChroma) {
            val halfW = (width + 1) / 2
            val halfH = (height + 1) / 2
            for (row in 0 until halfH) {
                val srcRow = row * 2
                var out = 0
                for (col in 0 until halfW) {
                    scratch[out] = rgbToU(pixels[srcRow * width + col * 2]).toByte()
                    out += pixelStride
                }
                buffer.position(row * rowStride)
                buffer.put(scratch, 0, halfW * pixelStride)
            }
        } else {
            for (row in 0 until height) {
                val srcRow = row * width
                var out = 0
                for (col in 0 until width) {
                    scratch[out] = rgbToY(pixels[srcRow + col]).toByte()
                    out += pixelStride
                }
                buffer.position(row * rowStride)
                buffer.put(scratch, 0, width * pixelStride)
            }
        }
    }

    private fun fillInterleavedPlane(
        plane: android.media.Image.Plane,
        pixels: IntArray,
        width: Int,
        height: Int,
        scratch: ByteArray,
    ) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val halfW = (width + 1) / 2
        val halfH = (height + 1) / 2
        for (row in 0 until halfH) {
            val srcRow = row * 2
            var out = 0
            for (col in 0 until halfW) {
                val pixel = pixels[srcRow * width + col * 2]
                scratch[out++] = rgbToU(pixel).toByte()
                scratch[out++] = rgbToV(pixel).toByte()
            }
            buffer.position(row * rowStride)
            buffer.put(scratch, 0, halfW * 2)
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        trackIndex: Int,
        info: MediaCodec.BufferInfo,
        onEos: (Boolean) -> Unit,
    ) {
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(info, 0)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit

                outputIndex < 0 -> Unit

                else -> {
                    val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (!isEos && info.size > 0) {
                        val buffer = encoder.getOutputBuffer(outputIndex) ?: error("No output buffer")
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, buffer, info)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (isEos) {
                        onEos(true)
                        return
                    }
                }
            }
        }
    }

    private data class DecodedPcm(
        val bytes: ByteArray,
        val sampleRate: Int,
        val channelCount: Int,
    )

    private fun decodeAudioToPcm(
        audioStreamUrl: String,
        startUs: Long,
        endUs: Long,
    ): DecodedPcm {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(audioStreamUrl)
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            require(audioTrackIndex >= 0) { "No audio track in stream" }

            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            extractor.selectTrack(audioTrackIndex)
            if (startUs > 0) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            decoder =
                MediaCodec
                    .createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME) ?: error("No audio mime"))
                    .apply {
                        configure(inputFormat, null, null, 0)
                        start()
                    }

            val pcmOut = ByteArrayOutputStream()
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            var extractEosQueued = false
            var decoderEos = false
            val info = MediaCodec.BufferInfo()
            var safety = 0

            while (!decoderEos) {
                if (++safety > 2_000_000) {
                    error("Audio decode did not finish")
                }

                val outputIndex = decoder.dequeueOutputBuffer(info, 0)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        format.let { fmt ->
                            sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channelCount = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            pcmEncoding = fmt.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        }
                    }

                    outputIndex < 0 -> Unit

                    else -> {
                        val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (isEos) {
                            decoder.releaseOutputBuffer(outputIndex, false)
                            decoderEos = true
                        } else {
                            val ptsUs = info.presentationTimeUs
                            val beforeClip = ptsUs + 1_000 < startUs
                            val afterClip = ptsUs >= endUs
                            if (beforeClip || afterClip) {
                                decoder.releaseOutputBuffer(outputIndex, false)
                                if (afterClip) {
                                    decoderEos = true
                                }
                            } else {
                                val buffer = decoder.getOutputBuffer(outputIndex) ?: error("No PCM buffer")
                                writePcm16(buffer, info.size, pcmOut, pcmEncoding)
                                decoder.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                    }
                }

                if (!extractEosQueued && !decoderEos) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val sampleSize = extractor.sampleSize
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractEosQueued = true
                        } else {
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleTimeUs >= endUs) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, sampleTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                extractEosQueued = true
                            } else {
                                val inputBuffer = decoder.getInputBuffer(inputIndex) ?: error("No input buffer")
                                inputBuffer.clear()
                                extractor.readSampleData(inputBuffer, 0)
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize.toInt(), sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
            }

            return DecodedPcm(bytes = pcmOut.toByteArray(), sampleRate = sampleRate, channelCount = channelCount)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun writePcm16(
        buffer: ByteBuffer,
        size: Int,
        out: ByteArrayOutputStream,
        encoding: Int,
    ) {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floatBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val sampleCount = size / 4
            for (i in 0 until sampleCount) {
                val sample = (floatBuffer.get(i) * 32767f).roundToInt().coerceIn(-32768, 32767)
                out.write(sample and 0xFF)
                out.write((sample shr 8) and 0xFF)
            }
        } else {
            val bytes = ByteArray(size)
            buffer.position(0)
            buffer.get(bytes, 0, size)
            out.write(bytes, 0, size)
        }
    }
}
