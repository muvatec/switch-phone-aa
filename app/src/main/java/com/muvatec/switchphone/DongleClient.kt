package com.muvatec.switchphone

import android.content.Context
import android.util.Log
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

data class Device(val id: String, val type: String, val name: String, val index: String) {
    /** Display name; the dongle can return a blank name for some entries. */
    var alias: String = ""
    val label: String get() = name.ifBlank { alias.ifBlank { "Celular guardado" } }
    val nameKnown: Boolean get() = name.isNotBlank() || alias.isNotBlank()
}

data class DongleState(
    val version: String,
    val product: String,
    val btName: String,
    val wifiName: String,
    val boxName: String,
    val connectedModel: String,
    val linkType: String,
    val devices: List<Device>,
    val settings: Map<String, Int>,
    val channels: List<Int> = emptyList(),
    val carInfo: CarInfoData = CarInfoData("", "", 0),
    val langs: List<Lang> = emptyList(),
    val modules: Map<String, String> = emptyMap(),
    val hardware: String = "",
) {
    /** Active = the phone that is projecting Android Auto (known by the phones themselves, not the dongle). */
    fun isConnected(d: Device): Boolean = Presence.isActive(d.label)
}

data class CarInfoData(val brand: String, val model: String, val year: Int)
data class Lang(val id: Int, val name: String)
data class Monitor(val cpu: Int, val temp: Double, val freq: Int, val mem: Double, val rx: Double, val tx: Double)

enum class PhoneStatus { CONNECTED, NEARBY, SAVED }

fun DongleState.statusOf(d: Device): PhoneStatus = when {
    isConnected(d) -> PhoneStatus.CONNECTED
    Presence.isNearby(d.label) -> PhoneStatus.NEARBY
    else -> PhoneStatus.SAVED
}

class DongleException(message: String) : Exception(message)

/** Talks to the Carlinkit/AutoKit box web API at 192.168.50.2 (same API its own web UI uses). */
class DongleClient(context: Context) {
    private val appCtx = context.applicationContext
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** The phone may route default traffic over mobile data, so find the dongle's WiFi network explicitly. */
    private fun dongleNetwork(): Network {
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val lp = cm.getLinkProperties(n) ?: continue
            if (lp.linkAddresses.any { it.address.hostAddress?.startsWith("192.168.50.") == true }) return n
        }
        throw DongleException("No estoy en el WiFi del dongle (AutoKit_xx)")
    }

    private fun md5(s: String) =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun post(params: Map<String, String>, readTimeoutMs: Int = 6000): String {
        val p = params.toMutableMap()
        p["ts"] = System.currentTimeMillis().toString()
        val signed = p.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        p["sign"] = md5(signed + SALT)
        val body = p.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val conn = dongleNetwork().openConnection(URL("http://192.168.50.2/cgi-bin/server.cgi")) as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) throw DongleException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    fun infos(): DongleState {
        val raw = post(mapOf("cmd" to "infos"))
        val j = JSONObject(raw)
        val box = j.getJSONObject("BoxInfo")
        Log.d("SwitchPhone", "NAMES box='${box.optString("boxName")}' bt='${box.optString("bt")}' wifi='${box.optString("wifi")}' ver=${box.optString("ver")}")
        Log.d("SwitchPhone", "BoxInfo MD*: model='${box.optString("MDModel")}' link='${box.optString("MDLinkType")}' os='${box.optString("MDOSVersion")}'")
        Log.d("SwitchPhone", "DevList: ${j.optJSONArray("DevList")}")
        val st = j.getJSONObject("Settings")
        val settings = HashMap<String, Int>()
        st.keys().forEach { k -> st.optInt(k, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { settings[k] = it } }
        val list = ArrayList<Device>()
        j.optJSONArray("DevList")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list += Device(o.optString("id"), o.optString("type"), o.optString("name"), o.optString("index"))
            }
        }
        val model = box.optString("MDModel")
        val aliases = Prefs.aliases(appCtx)
        // Learn names the dongle left blank: if a phone is linked and no saved name matches it,
        // and exactly one saved phone has no name, that phone is the one that is linked.
        if (model.isNotBlank()) {
            val blank = list.filter { it.name.isBlank() && aliases[it.id].isNullOrBlank() }
            val matched = list.any { it.name.isNotBlank() && (it.name.contains(model, true) || model.contains(it.name, true)) }
            if (!matched && blank.size == 1) {
                Prefs.setAlias(appCtx, blank[0].id, model); aliases[blank[0].id] = model
            }
        }
        val channels = ArrayList<Int>()
        j.optJSONArray("WifiChannelList")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optInt("id")?.let { channels += it } }
        val ci = j.optJSONObject("CarInfo")
        val carInfo = CarInfoData(ci?.optString("brand").orEmpty(), ci?.optString("model").orEmpty(), ci?.optInt("year") ?: 0)
        val langs = ArrayList<Lang>()
        j.optJSONArray("LangList")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.opt(i)
                if (o is JSONObject) langs += Lang(o.optInt("id", i), o.optString("name"))
                else langs += Lang(i, o.toString())
            }
        }
        val modules = LinkedHashMap<String, String>()
        j.optJSONArray("ModuleInfo")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { modules[it.optString("name")] = it.optString("version") } }
        list.forEach { it.alias = aliases[it.id].orEmpty() }
        Prefs.cacheNames(appCtx, list.map { it.label })
        Prefs.cacheBtName(appCtx, box.optString("bt"))
        return DongleState(
            version = box.optString("ver"),
            product = box.optString("productType"),
            btName = box.optString("bt"),
            wifiName = box.optString("wifi"),
            boxName = box.optString("boxName"),
            connectedModel = box.optString("MDModel"),
            linkType = box.optString("MDLinkType"),
            devices = list,
            settings = settings,
            channels = channels,
            carInfo = carInfo,
            langs = langs,
            modules = modules,
            hardware = box.optString("hardwareVer"),
        )
    }

    fun set(item: String, value: Any) = expectOk(post(mapOf("cmd" to "set", "item" to item, "val" to value.toString())))

    /** Remove a paired phone from the dongle's list. */
    fun forget(deviceId: String) = set("delDev", deviceId)

    /**
     * Hand the dongle over to [target]. The dongle API has no "connect to phone X" command, so this drops the
     * current phone (reboot) and lets [target] link first. When [aggressive], the current phone is also
     * removed from the dongle's list so it cannot grab the link again (it must be re-paired later).
     */
    fun switchTo(target: Device, current: Device?, aggressive: Boolean) {
        if (aggressive && current != null && current.id != target.id) forget(current.id)
        restart()
    }

    fun boxMonitor(): Monitor {
        val m = JSONObject(post(mapOf("cmd" to "BoxMonitor"))).getJSONObject("BoxMonitor")
        return Monitor(m.optInt("CpuRate"), m.optDouble("CpuTemp"), m.optInt("CpuFreq"), m.optDouble("MemRate"), m.optDouble("WifiRX"), m.optDouble("WifiTX"))
    }

    /** Car brand / model / year the dongle reports to the phone. Each value up to 31 bytes. */
    fun setCarInfo(brand: String, model: String, year: Int) =
        expectOk(post(mapOf("cmd" to "carInfo", "brand" to brand, "model" to model, "year" to year.toString())))

    /** kind: logFile | appLogFile | sdkLogFile */
    fun readLog(kind: String): String = post(mapOf("cmd" to kind), readTimeoutMs = 20000)

    fun resetLogo() = set("resetLogo", "")
    fun factoryReset() = expectOk(post(mapOf("cmd" to "reset")))
    fun resetApp() = expectOk(post(mapOf("cmd" to "resetApp")))

    /** Upload a custom car logo (PNG, up to 100 KB, 180x180). The dongle applies it after a restart. */
    fun uploadLogo(png: ByteArray) {
        val boundary = "----SwitchPhone${System.nanoTime()}"
        val conn = dongleNetwork().openConnection(URL("http://192.168.50.2/upload.cgi")) as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { o ->
                o.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"carlogo.png\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
                o.write(png)
                o.write("\r\n--$boundary--\r\n".toByteArray())
            }
            if (conn.responseCode != 200) throw DongleException("Subida rechazada (HTTP ${conn.responseCode})")
        } finally {
            conn.disconnect()
        }
    }

    /** Reboot the dongle; the linked phone drops and the box searches again. */
    fun restart() = expectOk(post(mapOf("cmd" to "restart")))

    private fun expectOk(resp: String) {
        val err = runCatching { JSONObject(resp).optInt("err", 0) }.getOrDefault(0)
        if (err != 0) throw DongleException("El dongle respondió err=$err")
    }

    companion object {
        // Salt used by the dongle's own web UI to sign requests.
        private const val SALT = "HweL*@M@JEYUnvPw9G36MVB9X6u@2qxK"
    }
}

/** Tiny settings store shared by the phone UI and the car screens. */
object Prefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("autokit", Context.MODE_PRIVATE)
    fun aggressive(c: Context) = sp(c).getBoolean("aggressive_switch", false)
    fun aliases(c: Context): MutableMap<String, String> {
        val out = HashMap<String, String>()
        val raw = sp(c).getString("aliases", "{}") ?: "{}"
        runCatching { org.json.JSONObject(raw).let { o -> o.keys().forEach { k -> out[k] = o.getString(k) } } }
        return out
    }
    fun setAlias(c: Context, id: String, name: String) {
        val m = aliases(c); if (name.isBlank()) m.remove(id) else m[id] = name.trim()
        sp(c).edit().putString("aliases", org.json.JSONObject(m as Map<*, *>).toString()).apply()
    }
    /** Saved phone names seen on the dongle, so a phone that is not on the dongle WiFi can still name them. */
    fun cacheNames(c: Context, names: List<String>) {
        sp(c).edit().putStringSet("known_names", names.filter { it.isNotBlank() }.toSet()).apply()
    }
    fun knownNames(c: Context): Set<String> = sp(c).getStringSet("known_names", emptySet()) ?: emptySet()
    fun nameForHash(c: Context, h: String): String? =
        sp(c).getStringSet("known_names", emptySet())?.firstOrNull { Presence.hash(it) == h }
    fun autoAccept(c: Context) = sp(c).getBoolean("auto_accept", false)
    fun setAutoAccept(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_accept", v).apply()
    fun cacheBtName(c: Context, v: String) { if (v.isNotBlank()) sp(c).edit().putString("bt_name", v).apply() }
    fun lastBtName(c: Context) = sp(c).getString("bt_name", "") ?: ""
    fun setAggressive(c: Context, v: Boolean) = sp(c).edit().putBoolean("aggressive_switch", v).apply()
}
