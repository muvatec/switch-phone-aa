package com.muvatec.switchphone

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Own-drawn UI on the car's navigation surface: rounded cards, light/dark following the car,
 * phone name and Active / Nearby / Saved status. Taps arrive through [SurfaceCallback.onClick].
 */
class SurfaceScreen(ctx: CarContext) : Screen(ctx), SurfaceCallback {
    private val main = Handler(Looper.getMainLooper())
    private val client = DongleClient(ctx)

    private var state: DongleState? = null
    private var error: String? = null
    private var loading = true

    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var density = 1f
    private var stable: Rect? = null

    private var pending: Device? = null       // card tapped, waiting for confirmation
    private var busyText: String? = null      // switching in progress
    private val hits = ArrayList<Pair<RectF, () -> Unit>>()

    private val tick = object : Runnable {
        override fun run() { refresh(); main.postDelayed(this, 8000) }
    }

    init {
        RequestInbox.listener = { render() }
        Presence.start(ctx)
        ctx.getCarService(AppManager::class.java).setSurfaceCallback(this)
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) { main.removeCallbacks(tick); tick.run() }
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) { main.removeCallbacks(tick) }
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { main.removeCallbacks(tick); RequestInbox.listener = null }
        })
    }

    private fun refresh() {
        Thread {
            val res = runCatching { client.infos() }
            main.post {
                state = res.getOrNull() ?: state
                error = res.exceptionOrNull()?.message
                loading = false
                render()
            }
        }.start()
    }

    override fun onGetTemplate(): Template =
        NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("Actualizar").setOnClickListener { refresh() }.build())
                    .build()
            )
            .build()

    // ---- SurfaceCallback -------------------------------------------------------------------

    override fun onSurfaceAvailable(c: SurfaceContainer) {
        surface = c.surface; width = c.width; height = c.height; density = max(c.dpi, 120) / 160f
        CarLog.log(carContext, "surface available ${c.width}x${c.height} dpi=${c.dpi}")
        render()
    }

    override fun onSurfaceDestroyed(c: SurfaceContainer) { surface = null; CarLog.log(carContext, "surface destroyed") }

    override fun onVisibleAreaChanged(r: Rect) { render() }

    override fun onStableAreaChanged(r: Rect) { stable = r; render() }

    override fun onClick(x: Float, y: Float) {
        CarLog.log(carContext, "tap at ${x.toInt()},${y.toInt()} (${hits.size} targets)")
        hits.lastOrNull { it.first.contains(x, y) }?.second?.invoke()
    }

    // ---- Rendering -------------------------------------------------------------------------

    private class Theme(dark: Boolean) {
        val bg = if (dark) 0xFF101318.toInt() else 0xFFEEF1F6.toInt()
        val card = if (dark) 0xFF1D2229.toInt() else 0xFFFFFFFF.toInt()
        val text = if (dark) 0xFFF1F3F6.toInt() else 0xFF14181F.toInt()
        val sub = if (dark) 0xFF9AA3AF.toInt() else 0xFF5B6574.toInt()
        val green = 0xFF2EB872.toInt()
        val blue = 0xFF2B8CFF.toInt()
        val gray = if (dark) 0xFF6B7380.toInt() else 0xFF9AA3AF.toInt()
        val shadow = if (dark) 0x66000000 else 0x30000000
    }

    private fun render() {
        val s = surface ?: return
        if (!s.isValid) return
        val canvas = runCatching { s.lockCanvas(null) }.getOrNull() ?: return
        try { draw(canvas) } finally { runCatching { s.unlockCanvasAndPost(canvas) } }
    }

    private fun paint(color: Int, size: Float = 0f, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        if (size > 0) textSize = size
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun fit(text: String, p: Paint, maxW: Float): String =
        TextUtils.ellipsize(text, android.text.TextPaint(p), maxW, TextUtils.TruncateAt.END).toString()

    private fun draw(c: Canvas) {
        val d = density
        val th = Theme(carContext.isDarkMode)
        hits.clear()
        c.drawColor(th.bg)

        val area = stable?.takeIf { !it.isEmpty } ?: Rect(0, 0, width, height)
        val pad = 20 * d
        val left = area.left + pad
        val right = area.right - pad

        // Header
        val titleP = paint(th.text, 26 * d, bold = true)
        c.drawText("Switch Phone", left, area.top + pad + 26 * d, titleP)
        val subP = paint(th.sub, 14 * d)
        val st = state
        val subtitle = when {
            loading && st == null -> "Buscando el dongle…"
            st == null -> error ?: "Sin conexión con el dongle"
            else -> "Activo: en uso · Disponible: visible · Guardado: no visible"
        }
        c.drawText(fit(subtitle, subP, right - left), left, area.top + pad + 26 * d + 22 * d, subP)

        if (st == null) return drawBusy(c, th, area, null)

        val devices = st.devices.sortedBy {
            when (st.statusOf(it)) { PhoneStatus.CONNECTED -> 0; PhoneStatus.NEARBY -> 1; PhoneStatus.SAVED -> 2 }
        }.take(6)
        if (devices.isEmpty()) {
            c.drawText("El dongle no tiene celulares guardados", left, area.centerY().toFloat(), paint(th.sub, 18 * d))
            return
        }

        val top = area.top + pad + 26 * d + 22 * d + 18 * d
        val bottom = area.bottom - pad
        val gap = 14 * d
        val cols = min(devices.size, 3)
        val rows = ceil(devices.size / cols.toDouble()).toInt()
        val cw = (right - left - gap * (cols - 1)) / cols
        val ch = min((bottom - top - gap * (rows - 1)) / rows, 230 * d)

        devices.forEachIndexed { i, dev ->
            val r = i / cols; val col = i % cols
            val rect = RectF(left + col * (cw + gap), top + r * (ch + gap), left + col * (cw + gap) + cw, top + r * (ch + gap) + ch)
            drawCard(c, th, rect, dev, st)
        }

        pending?.let { drawConfirm(c, th, area, it, st) }
        RequestInbox.pending?.takeIf { System.currentTimeMillis() - it.at < 60_000 }?.let { drawRequest(c, th, area, it) }
        busyText?.let { drawBusy(c, th, area, it) }
    }

    private fun drawCard(c: Canvas, th: Theme, r: RectF, dev: Device, st: DongleState) {
        val d = density
        val status = st.statusOf(dev)
        val accent = when (status) { PhoneStatus.CONNECTED -> th.green; PhoneStatus.NEARBY -> th.blue; PhoneStatus.SAVED -> th.gray }
        val radius = 18 * d

        val cardP = paint(th.card).apply { setShadowLayer(10 * d, 0f, 3 * d, th.shadow) }
        c.drawRoundRect(r, radius, radius, cardP)
        if (status == PhoneStatus.CONNECTED) {
            val stroke = paint(accent).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d }
            c.drawRoundRect(r, radius, radius, stroke)
        }

        // Icon
        val icon = ContextCompat.getDrawable(carContext, R.drawable.ic_phone)?.mutate()
        val isz = min(r.height() * 0.30f, 56 * d).toInt()
        val cx = r.centerX().toInt()
        val iy = (r.top + 16 * d).toInt()
        icon?.setTint(accent)
        icon?.setBounds(cx - isz / 2, iy, cx + isz / 2, iy + isz)
        icon?.draw(c)

        // Name
        val nameP = paint(th.text, min(r.height() * 0.11f, 22 * d), bold = true).apply { textAlign = Paint.Align.CENTER }
        val nameY = iy + isz + nameP.textSize + 10 * d
        c.drawText(fit(dev.label, nameP, r.width() - 24 * d), r.centerX(), nameY, nameP)

        // Status pill
        val label = when (status) { PhoneStatus.CONNECTED -> "ACTIVO"; PhoneStatus.NEARBY -> "DISPONIBLE"; PhoneStatus.SAVED -> "GUARDADO" }
        val pillP = paint(Color.WHITE, min(r.height() * 0.085f, 15 * d), bold = true).apply { textAlign = Paint.Align.CENTER }
        val pw = pillP.measureText(label) + 28 * d
        val ph = pillP.textSize + 14 * d
        val pillTop = nameY + 12 * d
        val pill = RectF(r.centerX() - pw / 2, pillTop, r.centerX() + pw / 2, pillTop + ph)
        c.drawRoundRect(pill, ph / 2, ph / 2, paint(accent))
        c.drawText(label, r.centerX(), pill.centerY() + pillP.textSize * 0.35f, pillP)

        // Hint
        if (status != PhoneStatus.CONNECTED) {
            val hintP = paint(th.sub, min(r.height() * 0.075f, 13 * d)).apply { textAlign = Paint.Align.CENTER }
            val hint = if (status == PhoneStatus.NEARBY) "Visible · toca para cambiar" else "No visible · toca para intentar"
            c.drawText(fit(hint, hintP, r.width() - 20 * d), r.centerX(), r.bottom - 14 * d, hintP)
            hits += r to { if (busyText == null) { pending = dev; render() } }
        }
    }

    private fun drawModal(c: Canvas, th: Theme, area: Rect): RectF {
        val d = density
        c.drawColor(0x99000000.toInt())
        val w = min(area.width() * 0.7f, 560 * d)
        val h = min(area.height() * 0.62f, 260 * d)
        val r = RectF(area.centerX() - w / 2, area.centerY() - h / 2, area.centerX() + w / 2, area.centerY() + h / 2)
        c.drawRoundRect(r, 22 * d, 22 * d, paint(th.card).apply { setShadowLayer(16 * d, 0f, 4 * d, th.shadow) })
        return r
    }

    private fun drawConfirm(c: Canvas, th: Theme, area: Rect, dev: Device, st: DongleState) {
        val d = density
        val r = drawModal(c, th, area)
        val titleP = paint(th.text, 22 * d, bold = true).apply { textAlign = Paint.Align.CENTER }
        c.drawText(fit("¿Cambiar a ${dev.label}?", titleP, r.width() - 32 * d), r.centerX(), r.top + 50 * d, titleP)
        val current = st.devices.firstOrNull { st.isConnected(it) }
        val subP = paint(th.sub, 15 * d).apply { textAlign = Paint.Align.CENTER }
        val line = if (current != null) "Se desconectará ${current.label}" else "Android Auto pasará a este celular"
        c.drawText(fit(line, subP, r.width() - 32 * d), r.centerX(), r.top + 82 * d, subP)

        val bh = 52 * d
        val bw = (r.width() - 24 * d * 3) / 2
        val by = r.bottom - 24 * d - bh
        val cancel = RectF(r.left + 24 * d, by, r.left + 24 * d + bw, by + bh)
        val ok = RectF(cancel.right + 24 * d, by, cancel.right + 24 * d + bw, by + bh)
        val outline = paint(th.gray).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d }
        c.drawRoundRect(cancel, bh / 2, bh / 2, outline)
        c.drawRoundRect(ok, bh / 2, bh / 2, paint(th.blue))
        val bp = paint(th.text, 17 * d, bold = true).apply { textAlign = Paint.Align.CENTER }
        c.drawText("Cancelar", cancel.centerX(), cancel.centerY() + 6 * d, bp)
        c.drawText("Cambiar", ok.centerX(), ok.centerY() + 6 * d, paint(Color.WHITE, 17 * d, bold = true).apply { textAlign = Paint.Align.CENTER })

        hits += RectF(0f, 0f, width.toFloat(), height.toFloat()) to { }          // swallow taps outside
        hits += cancel to { pending = null; render() }
        hits += ok to { pending = null; startSwitch(dev, current) }
    }

    private fun drawRequest(c: Canvas, th: Theme, area: Rect, req: RequestInbox.Req) {
        val d = density
        val r = drawModal(c, th, area)
        val titleP = paint(th.text, 22 * d, bold = true).apply { textAlign = Paint.Align.CENTER }
        c.drawText(fit("${req.name} quiere conectarse", titleP, r.width() - 32 * d), r.centerX(), r.top + 50 * d, titleP)
        val subP = paint(th.sub, 15 * d).apply { textAlign = Paint.Align.CENTER }
        c.drawText(fit("Si aceptas, Android Auto pasa a ese celular", subP, r.width() - 32 * d), r.centerX(), r.top + 82 * d, subP)
        val bh = 52 * d
        val bw = (r.width() - 24 * d * 3) / 2
        val by = r.bottom - 24 * d - bh
        val no = RectF(r.left + 24 * d, by, r.left + 24 * d + bw, by + bh)
        val ok = RectF(no.right + 24 * d, by, no.right + 24 * d + bw, by + bh)
        c.drawRoundRect(no, bh / 2, bh / 2, paint(th.gray).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d })
        c.drawRoundRect(ok, bh / 2, bh / 2, paint(th.green))
        c.drawText("Rechazar", no.centerX(), no.centerY() + 6 * d, paint(th.text, 17 * d, bold = true).apply { textAlign = Paint.Align.CENTER })
        c.drawText("Aceptar", ok.centerX(), ok.centerY() + 6 * d, paint(Color.WHITE, 17 * d, bold = true).apply { textAlign = Paint.Align.CENTER })
        hits += RectF(0f, 0f, width.toFloat(), height.toFloat()) to { }
        hits += no to { PresenceService.reject(carContext) }
        hits += ok to { PresenceService.accept(carContext) }
    }

    private fun drawBusy(c: Canvas, th: Theme, area: Rect, text: String?) {
        if (text == null) return
        val d = density
        val r = drawModal(c, th, area)
        val p = paint(th.text, 20 * d, bold = true).apply { textAlign = Paint.Align.CENTER }
        c.drawText(fit(text, p, r.width() - 32 * d), r.centerX(), r.top + 62 * d, p)
        val sub = paint(th.sub, 15 * d).apply { textAlign = Paint.Align.CENTER }
        val line = if (SwitchProgress.active()) "${SwitchProgress.phase()}  ·  ~${SwitchProgress.remainingSec()} s" else "Un momento…"
        c.drawText(fit(line, sub, r.width() - 32 * d), r.centerX(), r.top + 94 * d, sub)
        // progress bar
        val bw = r.width() - 64 * d
        val bar = RectF(r.left + 32 * d, r.bottom - 56 * d, r.left + 32 * d + bw, r.bottom - 44 * d)
        c.drawRoundRect(bar, 6 * d, 6 * d, paint(th.gray).apply { alpha = 90 })
        val fill = RectF(bar.left, bar.top, bar.left + bw * SwitchProgress.fraction().coerceAtLeast(0.03f), bar.bottom)
        c.drawRoundRect(fill, 6 * d, 6 * d, paint(th.blue))
        hits += RectF(0f, 0f, width.toFloat(), height.toFloat()) to { }
    }

    private fun scheduleBusyRedraw() {
        if (busyText == null) return
        render()
        main.postDelayed({ scheduleBusyRedraw() }, 500)
    }

    private fun startSwitch(target: Device, current: Device?) {
        busyText = "Cambiando a ${target.label}…"
        render()
        Thread {
            val res = runCatching { client.switchTo(target, current, Prefs.aggressive(carContext)) }
            main.post {
                // Keep the countdown up while the dongle reboots (the car usually drops this screen by itself within seconds).
                if (res.isSuccess) { main.postDelayed({ busyText = null; render() }, 12_000); scheduleBusyRedraw() } else busyText = null
                CarToast.makeText(
                    carContext,
                    res.fold({ "Listo: conectando ${target.label}" }, { "Error: ${it.message}" }),
                    CarToast.LENGTH_LONG,
                ).show()
                render()
            }
        }.start()
    }
}
