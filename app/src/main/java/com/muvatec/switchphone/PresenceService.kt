package com.muvatec.switchphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.connection.CarConnection
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.CarContext
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the BLE beacon alive and owns the notifications:
 *  - this phone is projecting  -> permanent notification with "Desconectar" / "Abrir app"
 *  - another phone projecting  -> heads-up offer "Cambiar a este celular"
 *  - another phone asks for the dongle -> this (active) phone performs the switch
 */
class PresenceService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var lastActive: Boolean? = null
    private var offeredFor: String? = null
    private var dismissedFor: String? = null

    private val tick = object : Runnable {
        override fun run() { evaluate(); main.postDelayed(this, 3000) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_IDLE, "Switch Phone en espera", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_ACTIVE, "Conectado al carro", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_OFFER, "Cambiar de celular", NotificationManager.IMPORTANCE_HIGH))
        val n = foregroundNotification(Presence.selfProjecting)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(ID_FG, n)
        Presence.start(this)
        Presence.onRequest = { h -> handleRequest(h) }
        Presence.onResult = { st -> showResult(st) }
        CarConnection(this).type.observeForever { t ->
            Presence.setProjecting(this, t == CarConnection.CONNECTION_TYPE_PROJECTION)
            evaluate()
        }
        main.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Presence.start(this)
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnectNow()
            ACTION_REQUEST -> {
                Presence.requestSwitch(this)
                notifyOffer("Pidiendo el control…", "Esperando que el otro celular lo ceda (unos 30 s).", withActions = false)
            }
            ACTION_ACCEPT -> acceptRequest()
            ACTION_REJECT -> rejectRequest()
            ACTION_CLEAR -> { Presence.clearRequest(this); getSystemService(NotificationManager::class.java).cancel(ID_OFFER) }
            ACTION_DISMISS -> {
                dismissedFor = offeredFor
                getSystemService(NotificationManager::class.java).cancel(ID_OFFER)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        Presence.onRequest = null
        Presence.onResult = null
        Presence.stop(this)
        super.onDestroy()
    }

    // ---- state ------------------------------------------------------------------------------

    private fun evaluate() {
        val nm = getSystemService(NotificationManager::class.java)
        val active = Presence.selfProjecting
        if (active != lastActive) {
            lastActive = active
            nm.notify(ID_FG, foregroundNotification(active))
            if (active) { nm.cancel(ID_OFFER); offeredFor = null; if (Presence.reqState.phase != Presence.ReqPhase.IDLE) Presence.clearRequest(this) }
            SwitchTileService.refresh(this)
        }
        if (!active) {
            val other = Presence.activeOthers().firstOrNull()
            if (other == null) {
                if (offeredFor != null) nm.cancel(ID_OFFER)
                offeredFor = null; dismissedFor = null
            } else if (offeredFor != other && dismissedFor != other) {
                offeredFor = other
                val name = Prefs.nameForHash(this, other) ?: "Otro celular"
                notifyOffer("$name está usando el carro", "¿Quieres que Android Auto pase a este celular?", withActions = true)
            }
        }
    }

    /** Requester side: show how the request ended. */
    private fun showResult(st: Presence.ReqState) {
        val nm = getSystemService(NotificationManager::class.java)
        val who = Prefs.nameForHash(this, st.targetHash) ?: "El otro celular"
        when (st.phase) {
            Presence.ReqPhase.REJECTED -> resultNotification("$who rechazó la solicitud", "Puedes volver a intentarlo.", retry = true)
            Presence.ReqPhase.TIMEOUT -> resultNotification("$who no respondió", "Revisa que el carro esté encendido y vuelve a intentarlo.", retry = true)
            Presence.ReqPhase.ACCEPTED -> resultNotification("$who aceptó", "El dongle se reinicia; Android Auto pasa a este celular en unos 30 s.", retry = false)
            Presence.ReqPhase.IDLE -> if (offeredFor == null) nm.cancel(ID_OFFER)
            Presence.ReqPhase.WAITING -> {}
        }
    }

    private fun resultNotification(title: String, text: String, retry: Boolean) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CH_OFFER).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(text).setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
        if (retry) {
            b.addAction(0, "Reintentar", servicePending(ACTION_REQUEST, 12))
            b.addAction(0, "Cerrar", servicePending(ACTION_CLEAR, 17))
        } else b.setTimeoutAfter(30_000)
        getSystemService(NotificationManager::class.java).notify(ID_OFFER, b.build())
    }

    private fun foregroundNotification(active: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        if (!active) {
            return NotificationCompat.Builder(this, CH_IDLE).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Switch Phone").setContentText("Visible para cambiar de celular en el carro")
                .setContentIntent(open).setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN).build()
        }
        return NotificationCompat.Builder(this, CH_ACTIVE).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Conectado al carro")
            .setContentText("Android Auto activo con este celular")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setColor(0xFF2EB872.toInt())
            .addAction(0, "Desconectar", servicePending(ACTION_DISCONNECT, 11))
            .addAction(0, "Abrir app", open)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun notifyOffer(title: String, text: String, withActions: Boolean) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CH_OFFER).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(text).setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(!withActions).setTimeoutAfter(if (withActions) 0 else 45_000)
        if (withActions) {
            b.addAction(0, "Cambiar a este celular", servicePending(ACTION_REQUEST, 12))
            b.addAction(0, "Ahora no", servicePending(ACTION_DISMISS, 13))
        }
        getSystemService(NotificationManager::class.java).notify(ID_OFFER, b.build())
    }

    private fun servicePending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, PresenceService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    // ---- actions ----------------------------------------------------------------------------

    /** "Desconectar": reboots the dongle so it lets go of this phone (it may reconnect by itself). */
    private fun disconnectNow() {
        Thread {
            val res = runCatching { DongleClient(this).restart() }
            Log.d("SwitchPhone", "disconnect -> $res")
        }.start()
    }

    /** Active phone: another saved phone asked for the dongle. Shows it in the car and as a notification. */
    private fun handleRequest(hash: String) {
        val name = Prefs.nameForHash(this, hash) ?: "Otro celular"
        RequestInbox.post(RequestInbox.Req(hash, name))
        if (Prefs.autoAccept(this)) { acceptRequest(); return }
        main.post {
            val title = "$name quiere conectarse al carro"
            val text = "Si aceptas, Android Auto pasa a ese celular."
            val accept = servicePending(ACTION_ACCEPT, 14)
            val reject = servicePending(ACTION_REJECT, 15)
            // Opens Switch Phone on the car screen when the heads-up is tapped.
            val carOpen = androidx.car.app.notification.CarPendingIntent.getCarApp(
                this, 16,
                Intent(Intent.ACTION_VIEW).setComponent(android.content.ComponentName(this, DongleCarAppService::class.java)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val b = NotificationCompat.Builder(this, CH_OFFER).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title).setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_CALL)
                .setTimeoutAfter(60_000)
                .addAction(0, "Aceptar", accept)
                .addAction(0, "Rechazar", reject)
                // Shown by Android Auto as a heads-up on the car screen, whatever app is in front.
                .extend(
                    CarAppExtender.Builder()
                        .setContentTitle(title).setContentText(text)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setImportance(NotificationManager.IMPORTANCE_HIGH)
                        .setContentIntent(carOpen)
                        .addAction(R.drawable.ic_swap, "Aceptar", accept)
                        .addAction(R.drawable.ic_phone, "Rechazar", reject)
                        .build()
                )
            getSystemService(NotificationManager::class.java).notify(ID_REQUEST, b.build())
        }
    }

    private fun acceptRequest() {
        val req = RequestInbox.pending ?: return
        RequestInbox.clear()
        Presence.reply(this, req.hash, true)
        getSystemService(NotificationManager::class.java).cancel(ID_REQUEST)
        Thread {
            runCatching {
                val client = DongleClient(this)
                val st = client.infos()
                val target = st.devices.firstOrNull { Presence.hash(it.label) == req.hash } ?: return@runCatching
                val me = st.devices.firstOrNull { st.isConnected(it) }
                Log.d("SwitchPhone", "accepted request from ${target.label}")
                client.switchTo(target, me, Prefs.aggressive(this))
                main.post { notifyOffer("Cediendo el carro a ${target.label}", "El dongle se reinicia; vuelve en unos 30 s.", withActions = false) }
            }.onFailure { Log.d("SwitchPhone", "accept failed: $it") }
        }.start()
    }

    private fun rejectRequest() {
        RequestInbox.pending?.let { Presence.reply(this, it.hash, false) }
        RequestInbox.clear()
        getSystemService(NotificationManager::class.java).cancel(ID_REQUEST)
    }

    companion object {
        private const val CH_IDLE = "presence"
        private const val CH_ACTIVE = "active"
        private const val CH_OFFER = "offer"
        private const val ID_FG = 1
        private const val ID_OFFER = 2
        private const val ID_REQUEST = 3
        const val ACTION_DISCONNECT = "com.muvatec.switchphone.DISCONNECT"
        const val ACTION_REQUEST = "com.muvatec.switchphone.REQUEST_SWITCH"
        const val ACTION_DISMISS = "com.muvatec.switchphone.DISMISS_OFFER"
        const val ACTION_CLEAR = "com.muvatec.switchphone.CLEAR_REQUEST"
        const val ACTION_ACCEPT = "com.muvatec.switchphone.ACCEPT_REQUEST"
        const val ACTION_REJECT = "com.muvatec.switchphone.REJECT_REQUEST"

        /** Called from the car screen. */
        fun accept(ctx: Context) = ctx.startForegroundService(Intent(ctx, PresenceService::class.java).setAction(ACTION_ACCEPT))
        fun reject(ctx: Context) = ctx.startForegroundService(Intent(ctx, PresenceService::class.java).setAction(ACTION_REJECT))

        fun start(ctx: Context) {
            if (!Presence.hasPermissions(ctx)) return
            ctx.startForegroundService(Intent(ctx, PresenceService::class.java))
        }

        /** Used by the tile and the icon shortcut. */
        fun requestHere(ctx: Context) {
            if (!Presence.hasPermissions(ctx)) return
            ctx.startForegroundService(Intent(ctx, PresenceService::class.java).setAction(ACTION_REQUEST))
        }
    }
}
