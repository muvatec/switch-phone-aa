package com.muvatec.switchphone

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.core.graphics.drawable.IconCompat
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

/** Android Auto entry point: lists the phones known by the dongle and lets the driver drop/switch. */
class DongleCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = SurfaceScreen(carContext)
    }
}

private val main = Handler(Looper.getMainLooper())

private fun CarContext.toast(msg: String) = CarToast.makeText(this, msg, CarToast.LENGTH_LONG).show()

private fun CarContext.icon(res: Int, tint: CarColor) =
    CarIcon.Builder(IconCompat.createWithResource(this, res)).setTint(tint).build()

private fun colored(text: String, color: CarColor): CarText {
    val sp = SpannableString(text)
    sp.setSpan(ForegroundCarColorSpan.create(color), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    return CarText.create(sp)
}

class DevicesScreen(ctx: CarContext) : Screen(ctx) {
    private val client = DongleClient(ctx)
    private var state: DongleState? = null
    private var error: String? = null
    private var loading = true

    init {
        Presence.start(ctx)
        refresh()
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) { schedule() }
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) { main.removeCallbacks(tick) }
        })
    }

    private val tick = Runnable { refresh(); schedule() }
    private fun schedule() { main.removeCallbacks(tick); main.postDelayed(tick, 8000) }

    private fun refresh() {
        loading = true
        invalidate()
        Thread {
            val res = runCatching { client.infos() }
            main.post {
                state = res.getOrNull()
                error = res.exceptionOrNull()?.message
                loading = false
                invalidate()
            }
        }.start()
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Actualizar").setOnClickListener { refresh() }.build())
            .build()
        val b = GridTemplate.Builder().setTitle("Switch Phone").setHeaderAction(Action.APP_ICON).setActionStrip(strip)
        val s = state
        if (loading && s == null) return b.setLoading(true).build()
        if (s == null) {
            return MessageTemplate.Builder(error ?: "Sin conexión con el dongle")
                .setTitle("Switch Phone").setHeaderAction(Action.APP_ICON)
                .addAction(Action.Builder().setTitle("Reintentar").setOnClickListener { refresh() }.build())
                .build()
        }
        val limit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        val current = s.devices.firstOrNull { s.isConnected(it) }
        val order = mapOf(PhoneStatus.CONNECTED to 0, PhoneStatus.NEARBY to 1, PhoneStatus.SAVED to 2)
        val list = ItemList.Builder().setNoItemsMessage("El dongle no tiene celulares guardados")
        s.devices.sortedBy { order[s.statusOf(it)] }.take(limit).forEach { d ->
            val st = s.statusOf(d)
            val item = GridItem.Builder().setTitle(d.label)
            when (st) {
                PhoneStatus.CONNECTED -> item
                    .setImage(carContext.icon(R.drawable.ic_phone, CarColor.GREEN), GridItem.IMAGE_TYPE_LARGE)
                    .setText(colored("● Activo", CarColor.GREEN))
                PhoneStatus.NEARBY -> item
                    .setImage(carContext.icon(R.drawable.ic_swap, CarColor.BLUE), GridItem.IMAGE_TYPE_LARGE)
                    .setText(colored("◉ Cerca", CarColor.BLUE))
                PhoneStatus.SAVED -> item
                    .setImage(carContext.icon(R.drawable.ic_phone, CarColor.createCustom(0xFF8A8A8A.toInt(), 0xFF8A8A8A.toInt())), GridItem.IMAGE_TYPE_LARGE)
                    .setText("Guardado")
            }
            if (st != PhoneStatus.CONNECTED) {
                item.setOnClickListener {
                    val warn = if (st == PhoneStatus.SAVED) "\nNo se detecta cerca: puede no conectar." else ""
                    screenManager.push(
                        ConfirmScreen(
                            carContext,
                            "¿Cambiar a ${d.label}?",
                            (if (current != null) "Android Auto pasará a ${d.label}.\nSe desconectará ${current.label}." else "Android Auto pasará a ${d.label}.") + warn,
                            "Cambiar",
                            busyText = "Cambiando a ${d.label}…",
                        ) {
                            client.switchTo(d, current, Prefs.aggressive(carContext))
                            "Listo: conectando ${d.label}"
                        }
                    )
                }
            }
            list.addItem(item.build())
        }
        return b.setSingleList(list.build()).build()
    }
}

class ConfirmScreen(
    ctx: CarContext,
    private val title: String,
    private val message: String,
    private val yes: String,
    private val busyText: String = "Un momento…",
    private val action: () -> String,
) : Screen(ctx) {
    private var busy = false
    override fun onGetTemplate(): Template {
        if (busy) {
            // A loading template cannot carry an icon or actions.
            return MessageTemplate.Builder(busyText).setTitle(title).setHeaderAction(Action.BACK).setLoading(true).build()
        }
        return MessageTemplate.Builder(message)
            .setTitle(title).setHeaderAction(Action.BACK)
            .setIcon(carContext.icon(R.drawable.ic_swap, CarColor.DEFAULT))
            .addAction(
                Action.Builder().setTitle(yes).setFlags(Action.FLAG_PRIMARY).setOnClickListener {
                    busy = true; invalidate()
                    Thread {
                        val res = runCatching { action() }
                        main.post {
                            carContext.toast(res.getOrElse { "Error: ${it.message}" })
                            screenManager.popToRoot()
                        }
                    }.start()
                }.build()
            )
            .addAction(Action.Builder().setTitle("Cancelar").setOnClickListener { screenManager.pop() }.build())
            .build()
    }
}
