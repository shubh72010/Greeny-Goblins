import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchScannerTest {
    private val navigation =
        """
        composable("settings/appearance?highlight={highlight}") { backStackEntry ->
            AppearanceSettings(navController, highlight = backStackEntry.arguments?.getString("highlight"))
        }
        composable("settings/internet?highlight={highlight}") { backStackEntry ->
            InternetSettings(navController, highlight = backStackEntry.arguments?.getString("highlight"))
        }
        """.trimIndent()

    private fun scan(body: String, screen: String = "AppearanceSettings"): List<ScannedSetting> {
        val dir = File.createTempFile("settings", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
        File(dir, "AppearanceSettings.kt").writeText(body)
        val nav = File.createTempFile("nav", ".kt")
        nav.writeText(navigation)
        return SettingsSearchScanner.scan(dir, nav, emptyList())
            .filter { it.key.startsWith("$screen.") }
    }

    @Test
    fun `extracts title, icon, section and highlight route`() {
        val entries =
            scan(
                """
                fun AppearanceSettings(navController: NavController, highlight: String?) {
                    PreferenceGroup(title = stringResource(R.string.theme)) {
                        item {
                            HighlightablePreference(highlightKey = "pure_black", highlight = highlight) {
                                SwitchPreference(
                                    title = { Text(stringResource(R.string.pure_black)) },
                                    icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                                    checked = pureBlack,
                                    onCheckedChange = {},
                                )
                            }
                        }
                    }
                }
                """.trimIndent(),
            )

        val entry = entries.single()
        assertEquals("AppearanceSettings.pure_black", entry.key)
        assertEquals("settings/appearance?highlight=pure_black", entry.route)
        assertEquals("stringResource(R.string.pure_black)", entry.titleExpr)
        assertEquals("dark_mode", entry.iconName)
        assertEquals("stringResource(R.string.theme)", entry.subtitleExpr)
        assertEquals(listOf("pure", "black", "theme"), entry.keywords)
    }

    @Test
    fun `skips rows that only forward a title parameter`() {
        // Private wrapper components take `title` as a param; the call site has the real one.
        val entries =
            scan(
                """
                fun AppearanceSettings(navController: NavController) {
                    SwitchPreference(
                        title = title,
                        description = description,
                        icon = icon,
                    )
                    SwitchPreference(
                        title = { Text(stringResource(R.string.app_icon)) },
                    )
                }
                """.trimIndent(),
            )

        assertEquals(listOf("AppearanceSettings.app_icon"), entries.map { it.key })
    }

    @Test
    fun `prefers a direct settings route over the parent screen`() {
        val entries =
            scan(
                """
                fun AppearanceSettings(navController: NavController) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.color_palette)) },
                        onClick = { navController.navigate("settings/appearance/palette_picker") },
                    )
                }
                """.trimIndent(),
            )

        assertEquals("settings/appearance/palette_picker", entries.single().route)
    }

    @Test
    fun `finds rows in a delegated body composable and groups by multiline title`() {
        val entries =
            scan(
                """
                fun AppearanceSettings(navController: NavController) {
                    AppearanceSettingsContent()
                }

                private fun AppearanceSettingsContent() {
                    PreferenceGroup(
                        title = stringResource(R.string.misc),
                        modifier = Modifier,
                    ) {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.wakelock)) },
                        )
                    }
                }
                """.trimIndent(),
            )

        val entry = entries.single()
        assertEquals("AppearanceSettings.wakelock", entry.key)
        assertEquals("stringResource(R.string.misc)", entry.subtitleExpr)
    }

    @Test
    fun `quotes literal section titles and skips interpolated ones`() {
        val entries =
            scan(
                """
                fun AppearanceSettings(navController: NavController) {
                    PreferenceGroup(title = "Liquid Glass") {
                        SwitchPreference(title = { Text(stringResource(R.string.blur_on)) })
                    }
                    PreferenceGroup(title = "Edit ${'$'}title") {
                        SwitchPreference(title = { Text(stringResource(R.string.discord_status)) })
                    }
                }
                """.trimIndent(),
            )

        assertEquals("\"Liquid Glass\"", entries.first().subtitleExpr)
        // Falls back to the humanised route segment rather than emitting broken Kotlin.
        assertEquals("\"Appearance\"", entries.last().subtitleExpr)
    }

    @Test
    fun `gives duplicate titles distinct keys`() {
        val entries =
            scan(
                """
                fun AppearanceSettings(navController: NavController) {
                    PreferenceGroup(title = stringResource(R.string.general)) {
                        SwitchPreference(title = { Text(stringResource(R.string.show_button)) })
                    }
                    PreferenceGroup(title = stringResource(R.string.options)) {
                        SwitchPreference(title = { Text(stringResource(R.string.show_button)) })
                    }
                }
                """.trimIndent(),
            )

        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.key }.toSet().size)
    }

    @Test
    fun `route pairing does not bleed into the next composable block`() {
        val routes =
            SettingsSearchScanner.parseRoutes(
                """
                composable("settings/backup_restore") {
                    BackupAndRestore(navController)
                }
                composable("settings/discord?highlight={highlight}") {
                    DiscordSettings(navController, highlight = null)
                }
                """.trimIndent(),
                emptyList(),
            )

        assertEquals("settings/backup_restore", routes["BackupAndRestore"])
        assertEquals("settings/discord", routes["DiscordSettings"])
    }

    @Test
    fun `excluded screens are dropped`() {
        val routes =
            SettingsSearchScanner.parseRoutes(
                """
                composable("settings") {
                    SettingsScreen(navController)
                }
                """.trimIndent(),
                listOf("SettingsScreen"),
            )

        assertTrue(routes.isEmpty())
        assertNull(routes["SettingsScreen"])
    }
}
