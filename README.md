<div align="center">

# 🌍 Cobe Android

**A lightweight, GPU-accelerated 3D globe for Android — Kotlin port of [shuding/cobe](https://github.com/shuding/cobe).**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-21+-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![OpenGL ES](https://img.shields.io/badge/OpenGL%20ES-3.0-5586A4?logo=opengl&logoColor=white)](https://www.khronos.org/opengles/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A faithful Kotlin/OpenGL ES 3.0 port of [@shuding](https://github.com/shuding)'s gorgeous WebGL globe.
Same fibonacci-lattice dot rendering. Same glow. Same markers + arcs. Native on Android.

</div>

---

## Demo

| Basic | Emoji | Gallery |
|:---:|:---:|:---:|
| White globe, orange markers | Pink markers + emoji overlays | Photo cards anchored on globe |

| Dark | Glow | Arcs |
|:---:|:---:|:---:|
| Inverted, cyan markers | Purple glow, deep night | City connections, bezier arcs |

> The demo app ships **7 preset styles**, a **stress slider** (0–500 random markers with entrance / zoom-fade animations), an **FPS inspector**, and **pinch / drag / tap** gestures.

---

## Why

The JS cobe library is ~5 KB of WebGL magic. Bringing that same crispness — fibonacci lattice dots, smooth glow, country silhouettes — to Android usually means heavy frameworks (Unity, Filament, SceneKit-shaped abstractions). This port keeps cobe's minimal soul:

- **Zero third-party deps** beyond `androidx.annotation`
- **Pure Kotlin + GLSL ES 3.0** — no native libs, no JNI
- **Instanced rendering** for markers + arcs (single draw call each)
- **~1.1 KB texture** (the original 1-bit cobe land/sea map)
- **One library module**, drop-in `CobeView` you put in any layout

---

## Quick start

### Gradle

```kotlin
// settings.gradle.kts
include(":cobe")
```

### Layout

```xml
<com.cobe.CobeView
    android:id="@+id/globe"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Kotlin

```kotlin
val globe = findViewById<CobeView>(R.id.globe)

globe.update(CobeState(
    phi = 0f,
    theta = 0.3f,
    mapBrightness = 6f,
    baseColor = floatArrayOf(1f, 1f, 1f),
    markerColor = floatArrayOf(1f, 0.5f, 0f),
    glowColor = floatArrayOf(0.95f, 0.95f, 0.95f),
    markers = listOf(
        Marker(doubleArrayOf(37.7595, -122.4367), 0.05f, id = "sf"),
        Marker(doubleArrayOf(40.7128,  -74.0060), 0.05f, id = "ny"),
        Marker(doubleArrayOf(35.6762, 139.6503),  0.05f, id = "tyo"),
    ),
    arcs = listOf(
        Arc(doubleArrayOf(37.7595, -122.4367), doubleArrayOf(40.7128, -74.0060)),
        Arc(doubleArrayOf(40.7128,  -74.0060), doubleArrayOf(35.6762, 139.6503)),
    ),
))
```

### Animate

```kotlin
val handler = Handler(Looper.getMainLooper())
var phi = 0f
handler.postDelayed(object : Runnable {
    override fun run() {
        phi += 0.004f
        globe.update(CobeState(phi = phi, theta = 0.3f))
        handler.postDelayed(this, 16)
    }
}, 16)
```

---

## API

### `Marker`
```kotlin
data class Marker(
    val location: DoubleArray,    // [lat, lon] in degrees
    val size: Float,              // 0.01 ≈ tiny, 0.05 ≈ medium
    val color: FloatArray? = null,// override markerColor per instance
    val id: String? = null,       // anchor id for overlay views
)
```

### `Arc`
```kotlin
data class Arc(
    val from: DoubleArray,        // [lat, lon]
    val to: DoubleArray,
    val color: FloatArray? = null,
    val id: String? = null,
)
```

### `CobeOptions` — full state

| Option | Default | Notes |
|---|---|---|
| `phi`, `theta` | 0, 0 | Rotation in radians |
| `mapSamples` | 10 000 | Fibonacci lattice density |
| `mapBrightness` | 1 | Land dot intensity |
| `mapBaseBrightness` | 0 | Floor brightness for water (set > 0 for full-sphere dots) |
| `baseColor` | white | Sphere fill |
| `markerColor` | orange | Default marker color |
| `glowColor` | white | Halo around globe |
| `arcColor` | sky blue | Default arc color |
| `arcWidth`, `arcHeight` | 1, 0.2 | Ribbon thickness, bezier peak height |
| `diffuse` | 1 | Light falloff exponent |
| `dark` | 0 | 0 → light theme, 1 → dark theme |
| `opacity` | 1 | Globe alpha multiplier |
| `offset` | [0, 0] | Screen-space offset in px |
| `scale` | 1 | Zoom (use ≈ 0.55 in portrait) |
| `markerElevation` | 0.05 | Hover height above surface |
| `perspective` | 0 | 0 = flat marker scale, 1 = closer = bigger |
| `clearColor` | [1,1,1,1] | GL background (RGBA) |

### Anchor overlays

Project marker positions to screen space and pin your own Views:

```kotlin
globe.setOnProject { marker, projection ->
    val id = marker.id ?: return@setOnProject
    val view = anchors[id] ?: return@setOnProject
    runOnUiThread {
        view.translationX = projection.x * overlay.width  - view.width  / 2f
        view.translationY = projection.y * overlay.height - view.height / 2f
        view.alpha = projection.alpha                          // horizon fade
        val s = 0.5f + max(0f, projection.depth) * 0.7f       // depth scale
        view.scaleX = s; view.scaleY = s
    }
}
```

`CobeProjection`:
- `x`, `y` ∈ [0, 1] — normalized viewport coords
- `alpha` ∈ [0, 1] — smoothstep horizon fade, 0 when behind globe
- `depth` — rz in rotated space; 1.0 = facing camera, < 0 = behind

---

## Architecture

```
cobe/
├── Cobe.kt              ← top-level facade
├── CobeView.kt          ← GLSurfaceView subclass
├── CobeRenderer.kt      ← GLES 3.0 renderer
├── CobeOptions.kt       ← Marker, Arc, Options, State, Projection
├── Shaders.kt           ← 6 GLSL ES 300 shaders inline
├── ShaderProgram.kt     ← compile/link helpers
└── assets/texture.png   ← 256×128 1-bit land/sea map
```

The renderer issues **three draw calls per frame**:

1. **Globe** — fullscreen quad, fragment shader does the fibonacci-lattice nearest-point lookup, samples the land texture, paints dot + glow
2. **Arcs** — instanced `TRIANGLE_STRIP` (66 verts × N), bezier curve with screen-space perpendicular ribbon offset
3. **Markers** — instanced `TRIANGLES` (6 verts × N), depth-aware fade + optional perspective scaling

All transforms happen in the vertex / fragment shaders. The Kotlin side just uploads instance buffers when `markers` / `arcs` change.

---

## Sample app features

The bundled `:sample` module is a full demo playground:

- **Dropdown style picker** — 7 presets
- **FPS inspector** — fps / frame ms / marker count, color-coded
- **Zoom · Map · Marker · 3D · Spin** sliders + switches
- **Stress slider** — drag 0 → 500 random markers; each spawns with ease-out scale; removed markers play a zoom-fade-out pop
- **Pinch + drag + tap** gestures
- **Photo gallery + position card overlays** — examples of anchoring rich `View` content

---

## Performance

| Scenario | iPhone 17 Sim | Pixel emulator (API 35) |
|---|---|---|
| Idle globe | 60 fps | 60 fps |
| 500 dot markers | 60 fps | ~50 fps |
| 500 emoji overlays | 60 fps | ~45 fps |
| 500 photo cards | 57 fps | ~30 fps |

Tips for many anchors:
- Set `view.setLayerType(View.LAYER_TYPE_HARDWARE, null)` — caches composited bitmap
- Avoid shadows and Auto-Layout-style constraints on overlay views
- Cull off-globe anchors (`projection.alpha <= 0`) by setting `View.GONE`

---

## Building

```bash
git clone https://github.com/<you>/cobe-android.git
cd cobe-android
./gradlew :sample:installDebug
```

Requirements:
- JDK 17
- Android SDK 34
- Android device or emulator with **OpenGL ES 3.0** (almost all devices since 2014)

---

## Credits

- **[@shuding](https://github.com/shuding)** — original [cobe](https://github.com/shuding/cobe) library and the fibonacci-lattice shader work. All the math is theirs; this port just translates GLSL → GLES 300 and JS → Kotlin.
- Texture is the 1-bit land/sea map shipped with cobe.

## Companion port

- **iOS / Metal version** — same API surface using `MTKView` + Metal shaders. (Coming soon as a separate repo.)

## License

MIT. See [LICENSE](LICENSE). Original cobe library is also MIT licensed.
