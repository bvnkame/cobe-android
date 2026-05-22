package com.cobe.sample

import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.cobe.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private enum class DemoKind(val title: String) {
    BASIC("Basic"), DARK("Dark"), GLOW("Glow"),
    EMOJI("Emoji"), ARCS("Arcs"),
    GALLERY("Gallery"), CARD("Card");

    companion object { val all = values().toList() }
}

private data class Pin(
    val id: String, val lat: Double, val lon: Double,
    val title: String, val emoji: String, val tint: Int,
)

private data class StressItem(
    val id: String,
    val lat: Double, val lon: Double,
    val baseSize: Float,
    val color: FloatArray,
    val emoji: String,
    val title: String,
    val tint: Int,
    val arcToLat: Double, val arcToLon: Double,
)

class MainActivity : AppCompatActivity() {

    private lateinit var cobe: CobeView
    private lateinit var overlay: FrameLayout
    private lateinit var menuButton: Button
    private lateinit var inspector: View
    private lateinit var inspectorToggle: Button
    private lateinit var fpsLabel: TextView
    private lateinit var perfLabel: TextView
    private lateinit var perfLabel2: TextView
    private lateinit var zoomSeek: SeekBar
    private lateinit var brightSeek: SeekBar
    private lateinit var sizeSeek: SeekBar
    private lateinit var stressSeek: SeekBar
    private lateinit var stressCountLabel: TextView
    private lateinit var spinSwitch: Switch
    private lateinit var depthSwitch: Switch
    private lateinit var resetButton: Button

    private var phi: Float = -1.4f
    private var theta: Float = 0.25f
    private var autoSpin = true
    private var demo: DemoKind = DemoKind.EMOJI

    private var userScale: Float = 0.55f
    private var brightness: Float = 6f
    private var markerSize: Float = 0.05f
    private var perspective3D = false
    private var stressMode = false

    private val anchors = mutableMapOf<String, View>()
    private val spawnTimes = mutableMapOf<String, Long>()
    private val exitTimes = mutableMapOf<String, Long>()
    private val activeStressIds = mutableSetOf<String>()
    private val stressPool = mutableListOf<StressItem>()
    private val stressById = mutableMapOf<String, StressItem>()
    private val spawnDurationMs = 450L
    private val exitDurationMs = 280L

    private val handler = Handler(Looper.getMainLooper())
    private val frameStamps = ArrayDeque<Long>()
    private var lastFrameTime = 0L
    private var frameMs = 0.0

    private val emojiPins = listOf(
        Pin("elephant", 20.0,   78.0,    "India",     "🐘", 0xFF34C759.toInt()),
        Pin("dragon",   35.0,   104.0,   "China",     "🐉", 0xFFFF3B30.toInt()),
        Pin("gamepad",  39.9,   116.4,   "Beijing",   "🎮", 0xFFAF52DE.toInt()),
        Pin("tower",    35.6762,139.6503,"Tokyo",     "🗼", 0xFFFF2D55.toInt()),
        Pin("surfer",   21.3,  -157.8,   "Hawaii",    "🏄", 0xFF007AFF.toInt()),
        Pin("taco",     19.4326,-99.1332,"Mexico",    "🌮", 0xFFFF9500.toInt()),
        Pin("koala",   -27.0,   135.0,   "Australia", "🐨", 0xFF5AC8FA.toInt()),
    )

    private val galleryPins = listOf(
        Pin("paris",   48.8584, 2.2945,   "Paris",      "🗼", 0xFFFF2D55.toInt()),
        Pin("nyc",     40.6892,-74.0445,  "New York",   "🗽", 0xFF007AFF.toInt()),
        Pin("sydney", -33.8568, 151.2153, "Sydney",     "🇦🇺", 0xFF5AC8FA.toInt()),
        Pin("wall",    40.4319, 116.5704, "Great Wall", "🇨🇳", 0xFFA2845E.toInt()),
        Pin("giza",    29.9792, 31.1342,  "Giza",       "🐪", 0xFFFFCC00.toInt()),
        Pin("rio",    -22.9519,-43.2105,  "Rio",        "🇧🇷", 0xFF34C759.toInt()),
        Pin("taj",     27.1751, 78.0421,  "Agra",       "🕌", 0xFFAF52DE.toInt()),
        Pin("london",  51.5007,-0.1246,   "London",     "🇬🇧", 0xFFFF3B30.toInt()),
    )

    private val randomNames = arrayOf("Aurora","Orion","Vega","Luna","Atlas","Nova","Iris","Echo","Sage","Lyra","Kai","Zen","Onyx","Rune","Sol","Faye","Quill","Pax","Vale","Wren")
    private val randomEmojis = arrayOf("🐘","🐉","🎮","🗼","🏄","🌮","🐨","🐱","🦁","🐯","🐼","🐧","🦄","🍕","🍔","🍣","🚀","⭐️","🎵","🎨","🌵","🐳","🌸","⚡️","🔥","💎","🎯","🍩","🛸","🏝")
    private val randomTints = intArrayOf(
        0xFFFF2D55.toInt(), 0xFF007AFF.toInt(), 0xFF34C759.toInt(), 0xFFAF52DE.toInt(),
        0xFF5AC8FA.toInt(), 0xFFFF9500.toInt(), 0xFFFF3B30.toInt(), 0xFFFFCC00.toInt(),
        0xFFA2845E.toInt(), 0xFF5856D6.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cobe = findViewById(R.id.cobe)
        overlay = findViewById(R.id.overlay)
        menuButton = findViewById(R.id.menuButton)
        inspector = findViewById(R.id.inspector)
        inspectorToggle = findViewById(R.id.inspectorToggle)
        fpsLabel = findViewById(R.id.fpsLabel)
        perfLabel = findViewById(R.id.perfLabel)
        perfLabel2 = findViewById(R.id.perfLabel2)
        zoomSeek = findViewById(R.id.zoomSeek)
        brightSeek = findViewById(R.id.brightSeek)
        sizeSeek = findViewById(R.id.sizeSeek)
        stressSeek = findViewById(R.id.stressSeek)
        stressCountLabel = findViewById(R.id.stressCountLabel)
        spinSwitch = findViewById(R.id.spinSwitch)
        depthSwitch = findViewById(R.id.depthSwitch)
        resetButton = findViewById(R.id.resetButton)

        setupCallbacks()
        setupSliders()
        setupGestures()
        setupMenu()

        menuButton.text = "${demo.title} ▾"
        applyDemo()
        startTick()
    }

    private fun setupCallbacks() {
        cobe.setOnProject { marker, proj ->
            val id = marker.id ?: return@setOnProject
            val v = anchors[id] ?: return@setOnProject
            runOnUiThread {
                if (proj.alpha <= 0.001f) {
                    if (v.visibility != View.GONE) v.visibility = View.GONE
                    return@runOnUiThread
                }
                if (v.visibility != View.VISIBLE) v.visibility = View.VISIBLE
                var entrance = 1f
                spawnTimes[id]?.let { spawn ->
                    val age = System.currentTimeMillis() - spawn
                    val t = (age.toFloat() / spawnDurationMs).coerceIn(0f, 1f)
                    entrance = 1f - (1f - t) * (1f - t)
                }
                val ow = overlay.width.toFloat()
                val oh = overlay.height.toFloat()
                v.translationX = proj.x * ow - v.width / 2f
                v.translationY = proj.y * oh - v.height / 2f
                v.alpha = proj.alpha * entrance
                val depthScale = if (perspective3D) (0.55f + max(0f, proj.depth) * 0.7f) else 1f
                val entranceScale = 0.4f + 0.6f * entrance
                val s = depthScale * entranceScale
                v.scaleX = s; v.scaleY = s
            }
        }
    }

    private fun setupSliders() {
        zoomSeek.progress = mapToSeek(userScale, 0.3f, 2.5f)
        brightSeek.progress = mapToSeek(brightness, 1f, 12f)
        sizeSeek.progress = mapToSeek(markerSize, 0.005f, 0.12f)

        zoomSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                userScale = mapFromSeek(p, 0.3f, 2.5f)
                cobe.update(CobeState(scale = userScale))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        brightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                brightness = mapFromSeek(p, 1f, 12f)
                cobe.update(CobeState(mapBrightness = brightness))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                markerSize = mapFromSeek(p, 0.005f, 0.12f)
                if (!stressMode) applyDemo()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        stressSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                onStressSlider(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        spinSwitch.setOnCheckedChangeListener { _, c -> autoSpin = c }
        depthSwitch.setOnCheckedChangeListener { _, c ->
            perspective3D = c
            cobe.update(CobeState(perspective = if (c) 1f else 0f))
        }
        resetButton.setOnClickListener {
            stressMode = false
            stressSeek.progress = 0
            stressCountLabel.text = "0"
            spawnTimes.clear(); exitTimes.clear(); activeStressIds.clear()
            applyDemo()
        }
        inspectorToggle.setOnClickListener {
            inspector.visibility = if (inspector.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun mapToSeek(v: Float, lo: Float, hi: Float) = (((v - lo) / (hi - lo)) * 1000).toInt().coerceIn(0, 1000)
    private fun mapFromSeek(p: Int, lo: Float, hi: Float) = lo + (p / 1000f) * (hi - lo)

    private fun setupMenu() {
        menuButton.setOnClickListener {
            val popup = PopupMenu(this, menuButton)
            DemoKind.all.forEachIndexed { i, k -> popup.menu.add(0, i, i, k.title) }
            popup.setOnMenuItemClickListener { item ->
                demo = DemoKind.all[item.itemId]
                menuButton.text = "${demo.title} ▾"
                applyDemo()
                true
            }
            popup.show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val scaleDetector = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                userScale = (userScale * d.scaleFactor).coerceIn(0.3f, 2.5f)
                zoomSeek.progress = mapToSeek(userScale, 0.3f, 2.5f)
                cobe.update(CobeState(scale = userScale))
                return true
            }
        })
        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                autoSpin = !autoSpin
                spinSwitch.isChecked = autoSpin
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                autoSpin = false
                spinSwitch.isChecked = false
                phi -= dx * 0.006f
                theta += dy * 0.006f
                theta = theta.coerceIn(-1.4f, 1.4f)
                return true
            }
        })
        cobe.setOnTouchListener { _, ev ->
            scaleDetector.onTouchEvent(ev)
            gesture.onTouchEvent(ev)
            true
        }
    }

    private fun applyDemo() {
        clearAnchors()
        stressMode = false

        val majorCities = listOf(
            Marker(doubleArrayOf( 37.7595, -122.4367), markerSize, id = "sf"),
            Marker(doubleArrayOf( 40.7128,  -74.0060), markerSize, id = "ny"),
            Marker(doubleArrayOf( 51.5074,   -0.1278), markerSize, id = "lon"),
            Marker(doubleArrayOf( 35.6762,  139.6503), markerSize, id = "tyo"),
            Marker(doubleArrayOf(-33.8688,  151.2093), markerSize, id = "syd"),
            Marker(doubleArrayOf(  1.3521,  103.8198), markerSize, id = "sg"),
            Marker(doubleArrayOf( 19.4326,  -99.1332), markerSize, id = "mex"),
            Marker(doubleArrayOf(-23.5505,  -46.6333), markerSize, id = "sao"),
        )
        val majorArcs = listOf(
            Arc(doubleArrayOf(37.7595, -122.4367), doubleArrayOf(40.7128, -74.0060)),
            Arc(doubleArrayOf(40.7128,  -74.0060), doubleArrayOf(51.5074, -0.1278)),
            Arc(doubleArrayOf(51.5074,   -0.1278), doubleArrayOf(35.6762, 139.6503)),
            Arc(doubleArrayOf(35.6762,  139.6503), doubleArrayOf(-33.8688, 151.2093)),
            Arc(doubleArrayOf(-33.8688, 151.2093), doubleArrayOf(1.3521,  103.8198)),
            Arc(doubleArrayOf(19.4326,  -99.1332), doubleArrayOf(-23.5505, -46.6333)),
        )

        val state = CobeState(
            scale = userScale, mapSamples = 16_000f, mapBrightness = brightness,
            diffuse = 3f, opacity = 1f, markerElevation = 0.05f,
            arcWidth = 1f, arcHeight = 0.3f,
            phi = phi, theta = theta,
            perspective = if (perspective3D) 1f else 0f,
        )

        val root = findViewById<FrameLayout>(R.id.root)
        when (demo) {
            DemoKind.BASIC -> {
                root.setBackgroundColor(Color.WHITE)
                state.clearColor  = floatArrayOf(1f, 1f, 1f, 1f)
                state.baseColor   = floatArrayOf(1f, 1f, 1f)
                state.markerColor = floatArrayOf(1f, 0.5f, 0f)
                state.glowColor   = floatArrayOf(0.95f, 0.95f, 0.95f)
                state.dark = 0f
                state.markers = majorCities; state.arcs = emptyList()
            }
            DemoKind.DARK -> {
                root.setBackgroundColor(Color.BLACK)
                state.clearColor  = floatArrayOf(0f, 0f, 0f, 1f)
                state.baseColor   = floatArrayOf(0.3f, 0.3f, 0.3f)
                state.markerColor = floatArrayOf(0.1f, 0.8f, 1f)
                state.glowColor   = floatArrayOf(1f, 1f, 1f)
                state.dark = 1f
                state.markers = majorCities; state.arcs = emptyList()
            }
            DemoKind.GLOW -> {
                root.setBackgroundColor(0xFF0F0F1E.toInt())
                state.clearColor  = floatArrayOf(0.06f, 0.06f, 0.12f, 1f)
                state.baseColor   = floatArrayOf(0.15f, 0.15f, 0.25f)
                state.markerColor = floatArrayOf(1f, 0.3f, 0.8f)
                state.glowColor   = floatArrayOf(0.5f, 0.3f, 1f)
                state.dark = 1f
                state.diffuse = 1.2f
                state.markers = majorCities; state.arcs = emptyList()
            }
            DemoKind.EMOJI -> {
                root.setBackgroundColor(Color.WHITE)
                state.clearColor  = floatArrayOf(1f, 1f, 1f, 1f)
                state.baseColor   = floatArrayOf(1f, 1f, 1f)
                state.markerColor = floatArrayOf(0.9f, 0.2f, 0.55f)
                state.glowColor   = floatArrayOf(0.95f, 0.95f, 0.95f)
                state.dark = 0f
                state.markers = emojiPins.map { Marker(doubleArrayOf(it.lat, it.lon), markerSize, id = it.id) }
                state.arcs = emptyList()
                buildEmojiAnchors()
            }
            DemoKind.ARCS -> {
                root.setBackgroundColor(Color.BLACK)
                state.clearColor  = floatArrayOf(0f, 0f, 0f, 1f)
                state.baseColor   = floatArrayOf(0.2f, 0.2f, 0.3f)
                state.markerColor = floatArrayOf(1f, 1f, 0.2f)
                state.glowColor   = floatArrayOf(0.4f, 0.6f, 1f)
                state.arcColor    = floatArrayOf(0.3f, 0.8f, 1f)
                state.dark = 1f
                state.markers = majorCities; state.arcs = majorArcs
            }
            DemoKind.GALLERY -> {
                root.setBackgroundColor(0xFFF8F8FA.toInt())
                state.clearColor  = floatArrayOf(0.97f, 0.97f, 0.98f, 1f)
                state.baseColor   = floatArrayOf(0.95f, 0.95f, 0.97f)
                state.markerColor = floatArrayOf(0.95f, 0.3f, 0.5f)
                state.glowColor   = floatArrayOf(0.9f, 0.9f, 0.95f)
                state.dark = 0f
                state.markers = galleryPins.map { Marker(doubleArrayOf(it.lat, it.lon), 0.025f, id = it.id) }
                state.arcs = emptyList()
                buildGalleryAnchors()
            }
            DemoKind.CARD -> {
                root.setBackgroundColor(Color.WHITE)
                state.clearColor  = floatArrayOf(1f, 1f, 1f, 1f)
                state.baseColor   = floatArrayOf(1f, 1f, 1f)
                state.markerColor = floatArrayOf(1f, 0.2f, 0.5f)
                state.glowColor   = floatArrayOf(0.95f, 0.95f, 0.95f)
                state.dark = 0f
                state.markers = galleryPins.map { Marker(doubleArrayOf(it.lat, it.lon), 0.03f, id = it.id) }
                state.arcs = emptyList()
                buildCardAnchors()
            }
        }
        cobe.update(state)
    }

    private fun clearAnchors() {
        anchors.values.forEach { overlay.removeView(it) }
        anchors.clear()
    }

    private fun buildEmojiAnchors() {
        for (p in emojiPins) anchors[p.id] = makeEmoji(p.emoji)
    }
    private fun buildGalleryAnchors() {
        for (p in galleryPins) anchors[p.id] = makePhotoCard(p.emoji, p.title, p.tint)
    }
    private fun buildCardAnchors() {
        for (p in galleryPins) anchors[p.id] = makeInfoCard(p.title, p.lat, p.lon, p.emoji)
    }

    private fun makeEmoji(emoji: String): View {
        val tv = TextView(this)
        tv.text = emoji
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        tv.gravity = Gravity.CENTER
        val sp = dp(30)
        overlay.addView(tv, FrameLayout.LayoutParams(sp, sp))
        return tv
    }

    private fun makePhotoCard(emoji: String, title: String, tint: Int): View {
        val w = dp(76); val h = dp(92)
        val v = FrameLayout(this)
        val bg = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Color.WHITE)
            setStroke(1, 0x1A000000)
        }
        v.background = bg
        v.layoutParams = FrameLayout.LayoutParams(w, h)

        val img = TextView(this)
        img.text = emoji
        img.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        img.gravity = Gravity.CENTER
        img.setTextColor(Color.WHITE)
        val tintDrawable = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(tint)
        }
        img.background = tintDrawable
        val imgLp = FrameLayout.LayoutParams(w - dp(12), dp(56))
        imgLp.leftMargin = dp(6); imgLp.topMargin = dp(6)
        v.addView(img, imgLp)

        val cap = TextView(this)
        cap.text = title
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        cap.gravity = Gravity.CENTER
        cap.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        cap.setTextColor(0xFF222222.toInt())
        val capLp = FrameLayout.LayoutParams(w - dp(8), dp(14))
        capLp.leftMargin = dp(4); capLp.topMargin = dp(68)
        v.addView(cap, capLp)

        v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        overlay.addView(v)
        return v
    }

    private fun makeInfoCard(title: String, lat: Double, lon: Double, emoji: String): View {
        val w = dp(124); val h = dp(48)
        val v = FrameLayout(this)
        val bg = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(0xF5FFFFFF.toInt())
            setStroke(1, 0x33000000)
        }
        v.background = bg
        v.layoutParams = FrameLayout.LayoutParams(w, h)

        val icon = TextView(this)
        icon.text = emoji
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        val iconLp = FrameLayout.LayoutParams(dp(26), dp(28))
        iconLp.leftMargin = dp(8); iconLp.topMargin = dp(10)
        v.addView(icon, iconLp)

        val nameLb = TextView(this)
        nameLb.text = title
        nameLb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        nameLb.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        nameLb.setTextColor(0xFF222222.toInt())
        val nLp = FrameLayout.LayoutParams(w - dp(50), dp(18))
        nLp.leftMargin = dp(40); nLp.topMargin = dp(6)
        v.addView(nameLb, nLp)

        val coordLb = TextView(this)
        coordLb.text = String.format("%.2f°, %.2f°", lat, lon)
        coordLb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        coordLb.setTextColor(0xFF888888.toInt())
        val cLp = FrameLayout.LayoutParams(w - dp(50), dp(14))
        cLp.leftMargin = dp(40); cLp.topMargin = dp(24)
        v.addView(coordLb, cLp)

        val dot = View(this)
        val dotBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFF2D55.toInt())
        }
        dot.background = dotBg
        val dLp = FrameLayout.LayoutParams(dp(6), dp(6))
        dLp.leftMargin = w - dp(12); dLp.topMargin = (h - dp(6)) / 2
        v.addView(dot, dLp)

        v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        overlay.addView(v)
        return v
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    // ----- Stress -----
    private fun generateStressPool() {
        stressPool.clear(); stressById.clear()
        repeat(500) { i ->
            val it = StressItem(
                id = "rnd$i",
                lat = Random.nextDouble(-78.0, 78.0),
                lon = Random.nextDouble(-180.0, 180.0),
                baseSize = Random.nextDouble(0.005, 0.018).toFloat(),
                color = floatArrayOf(
                    Random.nextDouble(0.3, 1.0).toFloat(),
                    Random.nextDouble(0.3, 1.0).toFloat(),
                    Random.nextDouble(0.3, 1.0).toFloat()
                ),
                emoji = randomEmojis.random(),
                title = randomNames.random(),
                tint = randomTints.random(),
                arcToLat = Random.nextDouble(-78.0, 78.0),
                arcToLon = Random.nextDouble(-180.0, 180.0),
            )
            stressPool.add(it); stressById[it.id] = it
        }
    }

    private fun onStressSlider(target: Int) {
        if (stressPool.isEmpty()) generateStressPool()
        stressCountLabel.text = "$target"
        if (!stressMode) {
            stressMode = true
            clearAnchors()
            spawnTimes.clear(); exitTimes.clear(); activeStressIds.clear()
        }
        val now = System.currentTimeMillis()
        val newActive = stressPool.take(target).map { it.id }.toMutableSet()
        val added = newActive - activeStressIds
        val removed = activeStressIds - newActive
        activeStressIds.clear(); activeStressIds.addAll(newActive)

        for (id in removed) {
            exitTimes[id] = now
            spawnTimes.remove(id)
            anchors.remove(id)?.let { v ->
                v.animate().alpha(0f).scaleX(1.8f).scaleY(1.8f).setDuration(exitDurationMs)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) { overlay.removeView(v) }
                    }).start()
            }
        }
        for (id in added) {
            val it = stressById[id] ?: continue
            spawnTimes[id] = now
            val anchorView: View? = when (demo) {
                DemoKind.EMOJI   -> makeEmoji(it.emoji)
                DemoKind.GALLERY -> makePhotoCard(it.emoji, it.title, it.tint)
                DemoKind.CARD    -> makeInfoCard(it.title, it.lat, it.lon, it.emoji)
                else -> null
            }
            if (anchorView != null) {
                anchorView.alpha = 0f
                anchors[id] = anchorView
            }
        }
        rebuildStressMarkers()
    }

    private fun rebuildStressMarkers() {
        val now = System.currentTimeMillis()
        val markers = ArrayList<Marker>(activeStressIds.size + exitTimes.size)
        val arcs = ArrayList<Arc>()

        for (id in activeStressIds) {
            val it = stressById[id] ?: continue
            var mul = 1f
            spawnTimes[id]?.let { spawn ->
                val age = now - spawn
                val t = (age.toFloat() / spawnDurationMs).coerceIn(0f, 1f)
                mul = 1f - (1f - t) * (1f - t)
                if (t >= 1f) spawnTimes.remove(id)
            }
            appendStressMarker(it, mul, true, markers, arcs)
        }
        val finished = mutableListOf<String>()
        for ((id, ex) in exitTimes) {
            val age = now - ex
            if (age >= exitDurationMs) { finished.add(id); continue }
            val it = stressById[id] ?: continue
            val t = age.toFloat() / exitDurationMs
            val grow = 1f + 0.9f * t
            val lateFade = if (t < 0.7f) 1f else max(0f, 1f - (t - 0.7f) / 0.3f)
            appendStressMarker(it, grow * lateFade, false, markers, arcs)
        }
        for (id in finished) exitTimes.remove(id)

        cobe.update(CobeState(markers = markers, arcs = arcs))
    }

    private fun appendStressMarker(it: StressItem, mul: Float, full: Boolean,
                                   markers: MutableList<Marker>, arcs: MutableList<Arc>) {
        when (demo) {
            DemoKind.BASIC, DemoKind.DARK, DemoKind.GLOW ->
                markers.add(Marker(doubleArrayOf(it.lat, it.lon), it.baseSize * mul, it.color, it.id))
            DemoKind.EMOJI ->
                markers.add(Marker(doubleArrayOf(it.lat, it.lon), 0.012f * mul, id = it.id))
            DemoKind.GALLERY ->
                markers.add(Marker(doubleArrayOf(it.lat, it.lon), 0.010f * mul, id = it.id))
            DemoKind.CARD ->
                markers.add(Marker(doubleArrayOf(it.lat, it.lon), 0.012f * mul, id = it.id))
            DemoKind.ARCS -> {
                markers.add(Marker(doubleArrayOf(it.lat, it.lon), 0.008f * mul, it.color, it.id))
                if (full) arcs.add(Arc(doubleArrayOf(it.lat, it.lon),
                                       doubleArrayOf(it.arcToLat, it.arcToLon),
                                       it.color, "arc_${it.id}"))
            }
        }
    }

    // ----- Tick -----
    private fun startTick() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (autoSpin) phi += 0.004f
                if (stressMode && (spawnTimes.isNotEmpty() || exitTimes.isNotEmpty())) {
                    rebuildStressMarkers()
                }
                cobe.update(CobeState(phi = phi, theta = theta))
                updateInspector()
                handler.postDelayed(this, 16)
            }
        }, 16)
    }

    private fun updateInspector() {
        val now = System.currentTimeMillis()
        if (lastFrameTime > 0) frameMs = (now - lastFrameTime).toDouble()
        lastFrameTime = now
        frameStamps.addLast(now)
        while (frameStamps.isNotEmpty() && now - frameStamps.first() > 1000) frameStamps.removeFirst()
        val fps = frameStamps.size
        fpsLabel.text = "$fps fps"
        fpsLabel.setTextColor(when {
            fps >= 55 -> 0xFF3DDC84.toInt()
            fps >= 30 -> 0xFFFFD60A.toInt()
            else      -> 0xFFFF453A.toInt()
        })
        perfLabel.text = String.format("frame %.1f ms", frameMs)
        val mc = cobe.options.markers.size
        val ac = cobe.options.arcs.size
        perfLabel2.text = "M:$mc  A:$ac"
    }

    override fun onResume() { super.onResume(); cobe.onResume() }
    override fun onPause()  { super.onPause();  cobe.onPause() }
}
