/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Exact port of Better Nothing Music Visualizer — AudioCaptureService config logic
 */

package moe.rukamori.archivetune.visualizer

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

object VisualizerConfigLoader {

    private var cachedRoot: JSONObject? = null

    fun loadRoot(context: Context): JSONObject {
        cachedRoot?.let { return it }
        val obj = loadZonesConfigRoot(context)
        cachedRoot = obj
        return obj
    }

    // Exact from AudioCaptureService: phoneModelForDevice
    fun phoneModelForDevice(device: Int): String = when (device) {
        DeviceProfile.DEVICE_NP1 -> "PHONE1"
        DeviceProfile.DEVICE_NP2 -> "PHONE2"
        DeviceProfile.DEVICE_NP2A -> "PHONE2A"
        DeviceProfile.DEVICE_NP3A -> "PHONE3A"
        DeviceProfile.DEVICE_NP4A -> "PHONE4A"
        DeviceProfile.DEVICE_NP4APRO -> "PHONE4A_PRO"
        DeviceProfile.DEVICE_NP3 -> "PHONE3"
        DeviceProfile.DEVICE_NP4B -> "PHONE4B"
        else -> "UNKNOWN"
    }

    fun getPresetKeysForModel(root: JSONObject, phoneModel: String): List<String> {
        val res = ArrayList<String>()
        if ("UNKNOWN" == phoneModel) return res
        val names = root.names() ?: return res
        for (i in 0 until names.length()) {
            val k = names.optString(i, "")
            val p = root.optJSONObject(k)
            if (p != null && phoneModel.equals(p.optString("phone_model", ""), ignoreCase = true)) res.add(k)
        }
        res.sort()
        return res
    }

    fun getAllPresetKeys(root: JSONObject): List<String> {
        val res = ArrayList<String>()
        val names = root.names() ?: return res
        for (i in 0 until names.length()) res.add(names.optString(i, ""))
        res.sort()
        return res
    }

    fun buildPresetInfos(root: JSONObject, keys: List<String>): List<PresetInfo> {
        val res = ArrayList<PresetInfo>()
        for (k in keys) {
            val p = root.optJSONObject(k)
            if (p != null) res.add(PresetInfo(k, p.optString("preset_name", k), p.optString("description", k)))
        }
        return res
    }

    fun loadPresetInfos(context: Context, device: Int): List<PresetInfo> {
        return try {
            val root = loadRoot(context)
            val pm = phoneModelForDevice(device)
            var keys = getPresetKeysForModel(root, pm)
            if (keys.isEmpty()) keys = getAllPresetKeys(root).filter { root.optJSONObject(it) != null }
            buildPresetInfos(root, keys)
        } catch (e: Exception) { emptyList() }
    }

    // Exact buildConfigExact – mirrors loadVisualizerConfig + buildVisualizerConfig + parseZoneSpecs + parseOptionalPercent
    fun buildConfigExact(context: Context, presetKey: String, sampleRate: Int = 44100, glyphDecaySpeed: Float = 0.75f): AudioProcessor.VisualizerConfig? {
        return try {
            val root = loadRoot(context)
            val p = root.optJSONObject(presetKey) ?: return null
            val za = p.optJSONArray("zones") ?: return null
            var da = if (p.has("decay-alpha")) p.optDouble("decay-alpha", 0.8) else root.optDouble("decay-alpha", 0.8)
            da *= glyphDecaySpeed
            val zs = parseZoneSpecs(za)
            buildVisualizerConfig(presetKey, p.optString("preset_name", presetKey), p.optString("description", presetKey), da, zs)
        } catch (e: Exception) { null }
    }

    // Keep old name for compatibility
    fun buildConfig(context: Context, presetKey: String, sampleRate: Int = 44100, decayOverride: Float? = null): AudioProcessor.VisualizerConfig? {
        return buildConfigExact(context, presetKey, sampleRate, decayOverride ?: 0.75f)
    }

    private fun buildVisualizerConfig(pk: String, name: String, d: String, da: Double, zs: Array<AudioProcessor.ZoneSpec>): AudioProcessor.VisualizerConfig {
        val ad = 0.86f + (da.toFloat() / 10f)
        val up = ArrayList<FloatArray>()
        val sp = HashSet<String>()
        for (z in zs) {
            val key = String.format(Locale.US, "%.4f|%.4f", z.lowHz, z.highHz)
            if (sp.add(key)) up.add(floatArrayOf(z.lowHz, z.highHz))
        }
        up.sortWith { l, r -> l[0].compareTo(r[0]) }
        val ur = Array(up.size) { i -> AudioProcessor.FrequencyRange(up[i][0], up[i][1]) }
        val zr = Array(zs.size) { IntArray(0) }
        for (z in zs.indices) {
            val os = ArrayList<Int>()
            for (r in ur.indices) if (ur[r].lowHz == zs[z].lowHz && ur[r].highHz == zs[z].highHz) os.add(r)
            zr[z] = IntArray(os.size) { i -> os[i] }
        }
        return AudioProcessor.VisualizerConfig(pk, name, d, ad, zs, ur, zr)
    }

    private fun parseZoneSpecs(za: JSONArray): Array<AudioProcessor.ZoneSpec> {
        val zs = Array(za.length()) { AudioProcessor.ZoneSpec(0f, 0f, Float.NaN, Float.NaN) }
        for (i in 0 until za.length()) {
            val z = za.getJSONArray(i)
            val lh = z.getDouble(0).toFloat()
            val hh = z.getDouble(1).toFloat()
            zs[i] = AudioProcessor.ZoneSpec(minOf(lh, hh), maxOf(lh, hh), parseOptionalPercent(z, 3), parseOptionalPercent(z, 4))
        }
        return zs
    }

    private fun parseOptionalPercent(arr: JSONArray, idx: Int): Float {
        if (idx >= arr.length()) return Float.NaN
        val r = arr.opt(idx) ?: return Float.NaN
        if (r == JSONObject.NULL) return Float.NaN
        return try {
            var v: Float
            if (r is Number) v = r.toFloat()
            else {
                var t = r.toString().trim()
                if (t.endsWith("%")) t = t.substring(0, t.length - 1).trim()
                v = t.toFloat()
            }
            if (v in 0f..1f) v *= 100f
            v
        } catch (e: Exception) { Float.NaN }
    }

    // Exact file loading as BNGV: check filesDir/zones.config override, else assets
    fun loadZonesConfigRoot(context: Context): JSONObject = JSONObject(loadZonesConfigText(context))

    fun loadZonesConfigText(context: Context): String {
        val f = File(context.filesDir, "zones.config")
        if (f.isFile) {
            FileInputStream(f).use { return readFully(it) }
        }
        context.assets.open("zones.config").use { return readFully(it) }
    }

    private fun readFully(input: InputStream): String {
        val os = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var r: Int
        while (input.read(buf).also { r = it } != -1) os.write(buf, 0, r)
        return os.toString("UTF-8")
    }

    data class PresetInfo(val key: String, val name: String, val description: String)

    fun loadZonesConfigVersion(context: Context): String = try { loadRoot(context).optString("version", "Unknown") } catch (e: Exception) { "Unknown" }

    fun chooseDefaultPresetKey(pm: String, pks: List<String>): String {
        if (pks.isEmpty()) return "np1"
        val prefs = when (pm) {
            "PHONE1" -> listOf("np1s", "np1")
            "PHONE2" -> listOf("np2")
            "PHONE2A" -> listOf("np2a")
            "PHONE3A" -> listOf("np3as", "np3a")
            "PHONE3" -> listOf("np3test")
            "PHONE4A" -> listOf("np4a")
            "PHONE4A_PRO" -> listOf("np4ap-test")
            "PHONE4B" -> listOf("np4b")
            else -> emptyList()
        }
        for (p in prefs) if (pks.contains(p)) return p
        return pks[0]
    }
}
