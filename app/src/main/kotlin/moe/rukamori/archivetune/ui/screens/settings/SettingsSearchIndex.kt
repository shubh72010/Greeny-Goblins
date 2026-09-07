package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R

/**
 * Deep index: individual toggleables inside sub-screens. Search shows these as
 * separate hits navigating to the parent screen. Keeps SettingsScreen shallow
 * search useful for "pure black" etc without opening every child.
 */
@Composable
fun buildDeepSettingsSearchItems(navController: NavController): List<SettingsItem> = buildList {
    // Appearance — settings/appearance
    add(item("deep_pure_black", R.drawable.dark_mode, R.string.pure_black, null, "appearance • display", listOf("pure black", "amoled", "true black", "dark theme"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=pure_black") })
    add(item("deep_dynamic_theme", R.drawable.palette, R.string.enable_dynamic_theme, null, "appearance • theme", listOf("dynamic theme", "material you", "monet", "adaptive color"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=dynamic_theme") })
    add(item("deep_disable_blur", R.drawable.blur_off, R.string.disable_blur, R.string.disable_blur_desc, "appearance • performance", listOf("blur", "glass", "performance", "disable blur"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=disable_blur") })
    add(item("deep_disable_animations", R.drawable.animation, R.string.disable_animations, R.string.disable_animations_desc, "appearance • performance", listOf("animations", "reduce motion", "low ram"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=disable_animations") })
    add(item("deep_force_high_refresh", R.drawable.animation, R.string.force_high_refresh_rate, null, "appearance • display", listOf("refresh rate", "120hz", "fps", "high refresh"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=force_high_refresh") })
    add(item("deep_blur_intensity", R.drawable.blur_on, R.string.blur_intensity, null, "appearance • blur", listOf("blur intensity", "radius", "blur amount"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=blur_intensity") })
    add(item("deep_album_backdrop", R.drawable.image, R.string.album_backdrop, R.string.album_backdrop_desc, "appearance • player", listOf("backdrop", "blur background", "artwork"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=album_backdrop") })
    add(item("deep_backdrop_blur_amount", R.drawable.blur_on, R.string.backdrop_blur_amount, null, "appearance • player", listOf("backdrop blur", "amount"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=backdrop_blur_amount") })
    add(item("deep_font_pref", R.drawable.format_paint, R.string.font_preference, R.string.font_preference_desc, "appearance • font", listOf("font", "typography", "custom font", "ttf"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=font_pref") })
    add(item("deep_player_design", R.drawable.palette, R.string.player, null, "appearance • player", listOf("player design", "player style", "v7 v8 v9 v10", "layout"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=player_design") })
    add(item("deep_canvas", R.drawable.image, R.string.archivetune_canvas, R.string.archivetune_canvas_desc, "appearance • canvas", listOf("canvas", "animated artwork", "video background"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=canvas") })
    add(item("deep_slider_style", R.drawable.animation, R.string.player_slider_style, null, "appearance • player", listOf("slider", "progress", "wavy", "thick", "circular"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=slider_style") })
    add(item("deep_swipe_thumbnail", R.drawable.swipe, R.string.enable_swipe_thumbnail, null, "appearance • gesture", listOf("swipe", "thumbnail", "gesture"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=swipe_thumbnail") })
    add(item("deep_thumbnail_shape", R.drawable.image, R.string.song_thumbnail_shape, null, "appearance • shape", listOf("thumbnail shape", "rounded", "square", "circle"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=thumbnail_shape") })
    add(item("deep_default_tab", R.drawable.home_filled, R.string.default_open_tab, null, "appearance • startup", listOf("default tab", "home", "search", "library"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/appearance?highlight=default_tab") })

    // Player — settings/player
    add(item("deep_audio_quality", R.drawable.music_note, R.string.audio_quality, null, "player • audio", listOf("audio quality", "bitrate", "highest", "opus", "low"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=audio_quality") })
    add(item("deep_stream_client", R.drawable.language, R.string.player_stream_client, null, "player • stream", listOf("stream client", "innertube", "jusplayer engine", "newpipe", "po token"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=stream_client") })
    add(item("deep_low_data", R.drawable.wifi_proxy, R.string.low_data_mode_title, R.string.low_data_mode_description, "player • network", listOf("low data", "save data", "mobile", "metered"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=low_data") })
    add(item("deep_crossfade", R.drawable.music_note, R.string.audio_crossfade_title, R.string.audio_crossfade_description, "player • audio", listOf("crossfade", "gapless", "transition"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=crossfade") })
    add(item("deep_skip_silence", R.drawable.music_note, R.string.skip_silence, null, "player • audio", listOf("skip silence", "silence"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=skip_silence") })
    add(item("deep_normalization", R.drawable.graphic_eq, R.string.audio_normalization, null, "player • audio", listOf("normalization", "loudness", "replaygain"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=normalization") })
    add(item("deep_offload", R.drawable.bolt, R.string.audio_offload, R.string.audio_offload_desc, "player • audio", listOf("offload", "hardware", "battery", "low power"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=offload") })
    add(item("deep_pause_on_mute", R.drawable.volume_up, R.string.pause_on_device_mute, R.string.pause_on_device_mute_desc, "player • volume", listOf("pause mute", "device mute", "volume zero"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=pause_on_mute") })
    add(item("deep_bluetooth_autostart", R.drawable.bluetooth, R.string.auto_start_on_bluetooth, R.string.auto_start_on_bluetooth_desc, "player • bluetooth", listOf("bluetooth", "auto start", "autoplay", "car"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=bluetooth_autostart") })
    add(item("deep_haptic", R.drawable.graphic_eq, R.string.haptic_visualizer, R.string.haptic_visualizer_desc, "player • haptics", listOf("haptic", "vibration", "visualizer", "bnmv"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=haptic") })
    add(item("deep_persistent_queue", R.drawable.queue_music, R.string.persistent_queue, R.string.persistent_queue_desc, "player • queue", listOf("persistent queue", "restore", "save queue"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=persistent_queue") })
    add(item("deep_permanent_shuffle", R.drawable.shuffle, R.string.permanent_shuffle, R.string.permanent_shuffle_desc, "player • queue", listOf("shuffle", "random", "permanent"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=permanent_shuffle") })
    add(item("deep_auto_download_like", R.drawable.download, R.string.auto_download_on_like, null, "player • download", listOf("auto download", "like", "offline"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=auto_download_like") })
    add(item("deep_auto_skip_error", R.drawable.error, R.string.auto_skip_next_on_error, R.string.auto_skip_next_on_error_desc, "player • playback", listOf("auto skip", "error", "skip"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=auto_skip_error") })
    add(item("deep_stop_on_clear", R.drawable.clear_all, R.string.stop_music_on_task_clear, null, "player • playback", listOf("stop task clear", "swipe away", "kill"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=stop_on_clear") })
    add(item("deep_wakelock", R.drawable.bolt, R.string.wakelock, R.string.wakelock_desc, "player • playback", listOf("wakelock", "keep awake", "cpu"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=wakelock") })
    add(item("deep_external_downloader", R.drawable.download, R.string.external_downloader, R.string.external_downloader_desc, "player • download", listOf("external downloader", "seal", "yt-dlp"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/player?highlight=external_downloader") })

    // Privacy / Behavior — settings/privacy
    add(item("deep_pause_history", R.drawable.history, R.string.pause_listen_history, null, "behavior • privacy", listOf("history", "listen", "pause", "privacy"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/privacy?highlight=pause_history") })
    add(item("deep_pause_search", R.drawable.search, R.string.pause_search_history, null, "behavior • privacy", listOf("search history", "pause"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/privacy?highlight=pause_search") })
    add(item("deep_haptics", R.drawable.graphic_eq, R.string.haptics, R.string.haptics_desc, "behavior • ui", listOf("haptics", "vibration", "feedback"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/privacy?highlight=haptics") })
    add(item("deep_screenshot", R.drawable.hide_image, R.string.disable_screenshot, R.string.disable_screenshot_desc, "behavior • privacy", listOf("screenshot", "secure", "flag secure"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/privacy?highlight=screenshot") })

    // Content — settings/content
    add(item("deep_content_lang", R.drawable.language, R.string.content_language, null, "content • locale", listOf("language", "locale", "hl"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/content?highlight=content_lang") })
    add(item("deep_content_country", R.drawable.language, R.string.content_country, null, "content • locale", listOf("country", "region", "gl"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/content?highlight=content_country") })
    add(item("deep_hide_explicit", R.drawable.explicit, R.string.hide_explicit, null, "content • filter", listOf("explicit", "clean", "filter"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/content?highlight=hide_explicit") })
    add(item("deep_hide_video", R.drawable.hide_image, R.string.hide_video, null, "content • filter", listOf("video", "hide video", "audio only"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/content?highlight=hide_video") })

    // Storage — settings/storage
    add(item("deep_smart_trimmer", R.drawable.storage, R.string.smart_trimmer, R.string.smart_trimmer_description, "storage • cache", listOf("smart trimmer", "trim", "cache"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/storage?highlight=smart_trimmer") })
    add(item("deep_song_cache", R.drawable.storage, R.string.max_song_cache_size, null, "storage • cache", listOf("song cache", "max size", "mb"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/storage?highlight=song_cache") })
    add(item("deep_image_cache", R.drawable.image, R.string.max_image_cache_size, null, "storage • cache", listOf("image cache", "coil", "max size"), MaterialTheme.colorScheme.primary) { navController.navigate("settings/storage?highlight=image_cache") })

    // Internet — settings/internet
    add(item("deep_doh", R.drawable.wifi_proxy, R.string.dns_over_https, R.string.dns_over_https_desc, "internet • dns", listOf("doh", "dns over https", "cloudflare", "privacy"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/internet?highlight=doh") })
    add(item("deep_dns_provider", R.drawable.wifi_proxy, R.string.dns_provider, null, "internet • dns", listOf("dns provider", "cloudflare", "google", "quad9"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/internet?highlight=dns_provider") })
    add(item("deep_proxy", R.drawable.wifi_proxy, R.string.enable_proxy, null, "internet • proxy", listOf("proxy", "http", "socks", "proxy enable"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/internet?highlight=proxy") })
    add(item("deep_proxy_type", R.drawable.wifi_proxy, R.string.proxy_type, null, "internet • proxy", listOf("proxy type", "http socks"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/internet?highlight=proxy_type") })
    add(item("deep_proxy_host", R.drawable.wifi_proxy, R.string.proxy_host, null, "internet • proxy", listOf("proxy host", "ip", "address"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/internet?highlight=proxy_host") })
    add(item("deep_proxy_port", R.drawable.wifi_proxy, R.string.proxy_port, null, "internet • proxy", listOf("proxy port", "port"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/internet?highlight=proxy_port") })
    add(item("deep_bypass_proxy", R.drawable.wifi_proxy, R.string.stream_bypass_proxy, R.string.stream_bypass_proxy_desc, "internet • proxy", listOf("bypass proxy", "stream direct", "ip mismatch"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/internet?highlight=bypass_proxy") })
    add(item("deep_ip_rotation", R.drawable.wifi_proxy, R.string.ip_rotation, R.string.ip_rotation_desc, "internet • proxy", listOf("ip rotation", "rotate ip", "proxy rotation"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/internet?highlight=ip_rotation") })

    // Lyrics — settings/lyrics (+ list screens)
    add(item("deep_lyrics_mode", R.drawable.lyrics, R.string.lyrics_mode, null, "lyrics • mode", listOf("lyrics mode", "v2", "enhanced", "karaoke"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/lyrics?highlight=lyrics_mode") })
    add(item("deep_lrc_lib", R.drawable.lyrics, R.string.enable_lrclib, null, "lyrics • provider", listOf("lrclib", "provider", "lyrics"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/lyrics?highlight=lrc_lib") })
    add(item("deep_kugou", R.drawable.lyrics, R.string.enable_kugou, null, "lyrics • provider", listOf("kugou", "provider"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/lyrics?highlight=kugou") })
    add(item("deep_betterlyrics", R.drawable.lyrics, R.string.enable_betterlyrics, null, "lyrics • provider", listOf("betterlyrics", "provider"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/lyrics?highlight=betterlyrics") })
    add(item("deep_paxsenix", R.drawable.lyrics, R.string.enable_paxsenix_lyrics, null, "lyrics • provider", listOf("paxsenix", "provider"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/lyrics?highlight=paxsenix") })
    add(item("deep_romanize_jp", R.drawable.language, R.string.lyrics_romanize_japanese, null, "lyrics • romanize", listOf("romanize", "japanese", "romaji", "romanization"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/lyrics?highlight=romanize_jp") })
    add(item("deep_romanize_ko", R.drawable.language, R.string.lyrics_romanize_korean, null, "lyrics • romanize", listOf("romanize", "korean"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/lyrics?highlight=romanize_ko") })
    add(item("deep_preload_lyrics", R.drawable.lyrics, R.string.preload_queue_lyrics, null, "lyrics • preload", listOf("preload", "queue", "lyrics cache", "preload count"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/lyrics?highlight=preload_lyrics") })

    // Discord — settings/discord
    add(item("deep_discord_rpc", R.drawable.discord, R.string.enable_discord_rpc, null, "integration • discord", listOf("discord", "rpc", "rich presence"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/discord?highlight=discord_rpc") })
    add(item("deep_discord_paused", R.drawable.discord, R.string.discord_show_when_paused, R.string.discord_show_when_paused_desc, "integration • discord", listOf("discord paused", "show when paused"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/discord?highlight=discord_paused") })
    add(item("deep_discord_status", R.drawable.discord, R.string.activity_status, null, "integration • discord", listOf("discord status", "online", "dnd", "idle"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/discord?highlight=discord_status") })

    // LastFM / ListenBrainz — settings/lastfm-ish (route is settings/integration -> lastfm)
    add(item("deep_scrobble", R.drawable.music_note, R.string.enable_scrobbling, null, "integration • lastfm", listOf("scrobble", "lastfm", "librefm", "scrobbling"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/integration?highlight=scrobble") })
    add(item("deep_listenbrainz", R.drawable.music_note, R.string.listenbrainz_scrobbling, null, "integration • listenbrainz", listOf("listenbrainz", "scrobble"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/integration?highlight=listenbrainz") })

    // AI — settings/ai_integration
    add(item("deep_ai_provider", R.drawable.ai, R.string.ai_provider, null, "ai • provider", listOf("ai", "provider", "chatgpt", "gemini", "Muse", "openrouter"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/ai_integration?highlight=ai_provider") })
    add(item("deep_ai_model", R.drawable.ai, R.string.ai_model, null, "ai • model", listOf("ai model", "gpt", "gemini", "Muse"), MaterialTheme.colorScheme.secondary) { navController.navigate("settings/ai_integration?highlight=ai_model") })

    // Storage / misc extras
    add(item("deep_show_codec", R.drawable.info, R.string.display_codec_on_player, null, "developer • debug", listOf("codec", "bitrate", "player", "nerd stats"), MaterialTheme.colorScheme.tertiary) { navController.navigate("settings/misc?highlight=show_codec") })
}

@Composable
private fun item(
    key: String,
    iconRes: Int,
    titleRes: Int,
    subtitleRes: Int?,
    section: String,
    keywords: List<String>,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
): SettingsItem = SettingsItem(
    key = key,
    icon = painterResource(iconRes),
    title = stringResource(titleRes),
    subtitle = subtitleRes?.let { stringResource(it) } ?: section,
    keywords = keywords + section.split(" • "),
    accentColor = accent,
    onClick = onClick,
)