package com.muvatec.switchphone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The dongle only lists phones it has *saved*; it does not say which are around.
 * Each phone running this app advertises a short hash of its Bluetooth name over BLE and scans for the
 * others, so a saved phone whose app is alive nearby is shown as "available".
 */
object Presence {
    private val SERVICE = ParcelUuid(UUID.fromString("0000a7c1-0000-1000-8000-00805f9b34fb"))
    private const val FRESH_MS = 45_000L

    enum class ReqPhase { IDLE, WAITING, REJECTED, ACCEPTED, TIMEOUT }
    class ReqState(val phase: ReqPhase, val targetHash: String = "", val at: Long = 0)

    private class Seen(val at: Long, val projecting: Boolean, val requesting: Boolean)
    private val seen = ConcurrentHashMap<String, Seen>() // name-hash -> last beacon
    @Volatile var myName: String = ""
    @Volatile private var projecting = false
    @Volatile private var requestingUntil = 0L
    @Volatile private var requestTarget = ""
    @Volatile private var replyTarget = ""
    @Volatile private var replyFlag = 0
    @Volatile private var replyUntil = 0L
    @Volatile private var lastActiveHash = ""

    /** Requester side: what happened to the request this phone sent. */
    @Volatile var reqState = ReqState(ReqPhase.IDLE)
        private set
    @Volatile var onResult: ((ReqState) -> Unit)? = null

    private fun setReq(s: ReqState) { reqState = s; mainHandler.post { onResult?.invoke(s) } }
    private val handledAt = ConcurrentHashMap<String, Long>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Active phone side: called with the name-hash of a phone asking to take over. */
    @Volatile var onRequest: ((String) -> Unit)? = null

    val selfProjecting: Boolean get() = projecting
    val isRunning: Boolean get() = started

    /** Bluetooth adapter on? */
    fun bluetoothOn(ctx: Context): Boolean =
        runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true }.getOrDefault(false)

    /** How many other phones' beacons were heard recently. */
    fun heardCount(): Int {
        val now = System.currentTimeMillis()
        return seen.filter { it.key != selfHash && now - it.value.at < FRESH_MS }.size
    }
    val selfHash: String get() = hash(myName)
    private val requesting: Boolean get() = !projecting && System.currentTimeMillis() < requestingUntil

    /** Name-hashes of other phones that are projecting right now. */
    fun activeOthers(): List<String> {
        val now = System.currentTimeMillis()
        val l = seen.filter { it.value.projecting && now - it.value.at < FRESH_MS && it.key != selfHash }.keys.toList()
        if (l.isNotEmpty()) lastActiveHash = l.first()
        return l
    }

    /** Ask the phone that is projecting to hand the dongle over to this phone (sent over BLE). Can be retried. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun requestSwitch(ctx: Context) {
        val now = System.currentTimeMillis()
        requestTarget = activeOthers().firstOrNull() ?: lastActiveHash
        requestingUntil = now + 45_000
        setReq(ReqState(ReqPhase.WAITING, requestTarget, now))
        if (started) { stopAdvertising(); startAdvertising(ctx) }
        mainHandler.postDelayed({
            if (reqState.phase == ReqPhase.WAITING && reqState.at == now) {
                requestingUntil = 0
                setReq(ReqState(ReqPhase.TIMEOUT, requestTarget, now))
                refreshAdvertising(ctx)
            }
        }, 46_000)
    }

    /** Dismiss a finished request (result shown) without retrying. */
    fun clearRequest(ctx: Context) {
        requestingUntil = 0
        setReq(ReqState(ReqPhase.IDLE))
        refreshAdvertising(ctx)
    }

    /** Active phone side: tell the requester whether its request was accepted or rejected. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun reply(ctx: Context, requesterHash: String, accepted: Boolean) {
        if (!accepted) handledAt.remove(requesterHash)       // allow the requester to try again right away
        replyTarget = requesterHash
        replyFlag = if (accepted) 8 else 4
        replyUntil = System.currentTimeMillis() + 30_000
        if (started) { stopAdvertising(); startAdvertising(ctx) }
        mainHandler.postDelayed({ refreshAdvertising(ctx) }, 31_000)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun refreshAdvertising(ctx: Context) {
        if (started) { stopAdvertising(); startAdvertising(ctx) }
    }
    private var adapterRef: android.bluetooth.BluetoothAdapter? = null
    private var started = false
    private var advCb: AdvertiseCallback? = null
    private var scanCb: ScanCallback? = null

    fun hash(name: String): String =
        MessageDigest.getInstance("MD5").digest(name.trim().lowercase().toByteArray())
            .take(4).joinToString("") { "%02x".format(it) }

    /** True when a phone with this saved name was heard recently. */
    fun isNearby(name: String): Boolean {
        if (name.isBlank()) return false
        val s = seen[hash(name)] ?: return false
        return System.currentTimeMillis() - s.at < FRESH_MS
    }

    /** True when that saved phone is the one currently projecting Android Auto through the dongle. */
    fun isActive(name: String): Boolean {
        if (name.isBlank()) return false
        if (projecting && name.trim().equals(myName.trim(), ignoreCase = true)) return true
        val s = seen[hash(name)] ?: return false
        return s.projecting && System.currentTimeMillis() - s.at < FRESH_MS
    }

    /** Called with this phone's Android Auto state; re-advertises so nearby phones learn it. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun setProjecting(ctx: Context, on: Boolean) {
        if (projecting == on) return
        projecting = on
        if (started) { stopAdvertising(); startAdvertising(ctx) }
    }

    fun hasPermissions(ctx: Context): Boolean =
        requiredPermissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31)
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            )
        else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(ctx: Context) {
        if (started || !hasPermissions(ctx)) return
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        if (!adapter.isEnabled) return
        myName = runCatching { adapter.name }.getOrNull().orEmpty()

        adapterRef = adapter
        startAdvertising(ctx)
        adapter.bluetoothLeScanner?.let { sc ->
            val cb = object : ScanCallback() {
                override fun onScanFailed(e: Int) { Log.d("SwitchPhone", "BLE scan FAIL code=$e") }
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val d = result.scanRecord?.getServiceData(SERVICE) ?: return
                    if (d.size < 4) return
                    val h = d.take(4).joinToString("") { "%02x".format(it) }
                    val flags = if (d.size >= 5) d[4].toInt() else 0
                    val target = if (d.size >= 9) d.slice(5..8).joinToString("") { "%02x".format(it) } else ""
                    val forMe = target == selfHash
                    val proj = flags and 1 != 0
                    val req = flags and 2 != 0
                    val prev = seen[h]
                    if (prev == null || prev.projecting != proj || prev.requesting != req)
                        Log.d("SwitchPhone", "BLE saw $h projecting=$proj requesting=$req rssi=${result.rssi}")
                    val now = System.currentTimeMillis()
                    seen[h] = Seen(now, proj, req)
                    if (req && projecting && (forMe || target == "00000000") && now - (handledAt[h] ?: 0L) > 60_000) {
                        handledAt[h] = now
                        onRequest?.invoke(h)
                    }
                    // Replies to this phone's request.
                    if (forMe && reqState.phase == ReqPhase.WAITING) {
                        if (flags and 4 != 0) { requestingUntil = 0; setReq(ReqState(ReqPhase.REJECTED, h, now)) }
                        else if (flags and 8 != 0) { requestingUntil = 0; setReq(ReqState(ReqPhase.ACCEPTED, h, now)) }
                    }
                }
            }
            sc.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(SERVICE).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(),
                cb,
            )
            scanCb = cb
        }
        started = true
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(ctx: Context) {
        val adv = adapterRef?.bluetoothLeAdvertiser ?: return
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(s: AdvertiseSettings?) { Log.d("SwitchPhone", "BLE adv OK name='$myName' projecting=$projecting requesting=$requesting") }
            override fun onStartFailure(e: Int) { Log.d("SwitchPhone", "BLE adv FAIL code=$e") }
        }
        val data = AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(SERVICE).build()
        val replying = System.currentTimeMillis() < replyUntil
        val flags = (if (projecting) 1 else 0) or (if (requesting) 2 else 0) or (if (replying) replyFlag else 0)
        val target = when { requesting -> requestTarget; replying -> replyTarget; else -> "" }.ifBlank { "00000000" }
        fun bytes(h: String) = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val payload = bytes(hash(myName)) + byteArrayOf(flags.toByte()) + bytes(target)
        val resp = AdvertiseData.Builder().addServiceData(SERVICE, payload).build()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(false).setTimeout(0).build()
        adv.startAdvertising(settings, data, resp, cb)
        advCb = cb
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        runCatching { advCb?.let { adapterRef?.bluetoothLeAdvertiser?.stopAdvertising(it) } }
        advCb = null
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun stop(ctx: Context) {
        if (!started) return
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        runCatching { advCb?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) } }
        runCatching { scanCb?.let { adapter?.bluetoothLeScanner?.stopScan(it) } }
        advCb = null; scanCb = null; started = false
    }
}
