package com.cobe

internal object Shaders {

    const val GLOBE_VERT = """#version 300 es
in vec2 aPosition;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

    const val GLOBE_FRAG = """#version 300 es
precision highp float;

uniform vec2 uResolution;
uniform vec2 uOffset;
uniform vec2 uRotation;          // (phi, theta)
uniform float uDots;
uniform float uScale;
uniform vec3 uBaseColor;
uniform vec3 uGlowColor;
uniform vec4 uRenderParams;      // (dotsBrightness, diffuse, dark, opacity)
uniform float uMapBaseBrightness;
uniform sampler2D uTexture;

out vec4 fragColor;

const float sqrt5 = 2.236068;
const float PI = 3.141593;
const float kTau = 6.283185;
const float kPhi = 1.618034;
const float r = 0.8;

float byDots;

mat3 rotate(float theta, float phi) {
    float cx = cos(theta);
    float cy = cos(phi);
    float sx = sin(theta);
    float sy = sin(phi);
    return mat3(
        cy, sy * sx, -sy * cx,
        0.0, cx, sx,
        sy, cy * -sx, cy * cx
    );
}

vec3 nearestFibonacciLattice(vec3 p, out float m) {
    p = p.xzy;

    float k = max(2.0, floor(log2(sqrt5 * uDots * PI * (1.0 - p.z * p.z)) * 0.72021));

    vec2 f = floor(pow(kPhi, k) / sqrt5 * vec2(1.0, kPhi) + 0.5);
    vec2 br1 = fract((f + 1.0) * (kPhi - 1.0)) * kTau - 3.883222;
    vec2 br2 = -2.0 * f;
    vec2 sp = vec2(atan(p.y, p.x), p.z - 1.0);
    vec2 c = floor(vec2(br2.y * sp.x - br1.y * (sp.y * uDots + 1.0), -br2.x * sp.x + br1.x * (sp.y * uDots + 1.0)) / (br1.x * br2.y - br2.x * br1.y));

    float mindist = PI;
    vec3 minip = vec3(0.0);
    for (float s = 0.0; s < 4.0; s += 1.0) {
        vec2 o = vec2(mod(s, 2.0), floor(s * 0.5));
        float idx = dot(f, c + o);
        if (idx > uDots) continue;

        float a = idx, b = 0.0;
        if (a >= 16384.0) { a -= 16384.0; b += 0.868872; }
        if (a >= 8192.0)  { a -= 8192.0;  b += 0.934436; }
        if (a >= 4096.0)  { a -= 4096.0;  b += 0.467218; }
        if (a >= 2048.0)  { a -= 2048.0;  b += 0.733609; }
        if (a >= 1024.0)  { a -= 1024.0;  b += 0.866804; }
        if (a >= 512.0)   { a -= 512.0;   b += 0.433402; }
        if (a >= 256.0)   { a -= 256.0;   b += 0.216701; }
        if (a >= 128.0)   { a -= 128.0;   b += 0.108351; }
        if (a >= 64.0)    { a -= 64.0;    b += 0.554175; }
        if (a >= 32.0)    { a -= 32.0;    b += 0.777088; }
        if (a >= 16.0)    { a -= 16.0;    b += 0.888544; }
        if (a >= 8.0)     { a -= 8.0;     b += 0.944272; }
        if (a >= 4.0)     { a -= 4.0;     b += 0.472136; }
        if (a >= 2.0)     { a -= 2.0;     b += 0.236068; }
        if (a >= 1.0)     { a -= 1.0;     b += 0.618034; }

        float theta = fract(b) * kTau;

        float cosphi = 1.0 - 2.0 * idx * byDots;
        float sinphi = sqrt(1.0 - cosphi * cosphi);
        vec3 sample_ = vec3(cos(theta) * sinphi, sin(theta) * sinphi, cosphi);

        float dist = length(p - sample_);
        if (dist < mindist) {
            mindist = dist;
            minip = sample_;
        }
    }

    m = mindist;
    return minip.xzy;
}

void main() {
    byDots = 1.0 / uDots;

    vec2 invResolution = 1.0 / uResolution;
    vec2 uv = ((gl_FragCoord.xy * invResolution) * 2.0 - 1.0) / uScale - uOffset * vec2(1.0, -1.0) * invResolution;
    uv.x *= uResolution.x * invResolution.y;

    float l = dot(uv, uv);
    float glowFactor = 0.0;
    vec4 color = vec4(0.0);

    if (l <= r*r) {
        float dis;
        vec4 layer = vec4(0.0);
        vec3 p = normalize(vec3(uv, sqrt(r*r - l)));
        mat3 rot = rotate(uRotation.y, uRotation.x);
        float dotNL = p.z;

        vec3 gP = nearestFibonacciLattice(p * rot, dis);

        float gPhi = asin(gP.y);
        float gTheta = acos(-gP.x / cos(gPhi));
        if (gP.z < 0.0) gTheta = -gTheta;

        float mapColor = max(texture(uTexture, vec2(((gTheta * 0.5) / PI), -(gPhi / PI + 0.5))).x, uMapBaseBrightness);

        float samp = mapColor
            * smoothstep(0.008, 0.0, dis)
            * pow(dotNL, uRenderParams.y)
            * uRenderParams.x;
        layer += vec4(uBaseColor
            * (mix((1.0 - samp) * pow(dotNL, 0.4), samp, uRenderParams.z) + 0.1)
            + pow(1.0 - dotNL, 4.0) * uGlowColor
        , 1.0);

        color += layer * (1.0 + uRenderParams.w) * 0.5;

        glowFactor = (1.0 - l) * (1.0 - l) * smoothstep(0.0, 1.0, 0.2 / (l - r*r));
    } else {
        float outD = sqrt(0.2 / (l - r*r));
        glowFactor = smoothstep(0.5, 1.0, outD / (outD + 1.0));
    }

    fragColor = color + vec4(glowFactor * uGlowColor, glowFactor);
}
"""

    const val MARKER_VERT = """#version 300 es
in vec2 aPosition;
in vec3 aMarkerPos;
in float aMarkerSize;
in vec3 aMarkerColor;
in float aHasColor;

uniform float uPhi;
uniform float uTheta;
uniform vec2 uResolution;
uniform float uScale;
uniform vec2 uOffset;
uniform float uMarkerElevation;
uniform float uPerspective;

out vec2 vUV;
out vec3 vMarkerColor;
out float vHasColor;
out float vFade;

void main() {
    float cx = cos(uTheta), sx = sin(uTheta);
    float cy = cos(uPhi),   sy = sin(uPhi);
    vec3 p = aMarkerPos * (0.8 + uMarkerElevation);
    vec3 rp = vec3(
        cy * p.x + sy * p.z,
        sy * sx * p.x + cx * p.y - cy * sx * p.z,
        -sy * cx * p.x + sx * p.y + cy * cx * p.z
    );

    float occluded = (rp.z < 0.0 && length(rp.xy) < 0.8) ? 1.0 : 0.0;
    vFade = (1.0 - occluded) * smoothstep(-0.15, 0.15, rp.z);

    float depthScale = mix(1.0, 0.5 + max(0.0, rp.z) * 0.9, uPerspective);
    float ia = uResolution.y / uResolution.x;
    vec2 pos = (rp.xy + aPosition * aMarkerSize * depthScale * 2.0) * vec2(ia, 1.0) * uScale + uOffset * vec2(1.0, -1.0) * uScale / uResolution;
    gl_Position = vec4(pos, 0.0, 1.0);

    vUV = aPosition;
    vMarkerColor = aMarkerColor;
    vHasColor = aHasColor;
}
"""

    const val MARKER_FRAG = """#version 300 es
precision highp float;
in vec2 vUV;
in vec3 vMarkerColor;
in float vHasColor;
in float vFade;
uniform vec3 uMarkerColor;
out vec4 fragColor;
void main() {
    if (length(vUV) > 0.25) discard;
    if (vFade <= 0.001) discard;
    vec3 col = (vHasColor > 0.5 ? vMarkerColor : uMarkerColor);
    fragColor = vec4(col, vFade);
}
"""

    const val ARC_VERT = """#version 300 es
in vec2 aPosition;
in vec3 aArcFrom;
in vec3 aArcTo;
in float aArcHeight;
in float aArcWidth;
in vec3 aArcColor;
in float aHasColor;

uniform float uPhi;
uniform float uTheta;
uniform vec2 uResolution;
uniform float uScale;
uniform vec2 uOffset;
uniform float uMarkerElevation;

out vec3 vArcColor;
out float vHasColor;
out float vDepth;
out float vRadialDist;

const float GLOBE_R = 0.8;

mat3 rotate(float theta, float phi) {
    float cx = cos(theta);
    float cy = cos(phi);
    float sx = sin(theta);
    float sy = sin(phi);
    return mat3(
        cy, sy * sx, -sy * cx,
        0.0, cx, sx,
        sy, cy * -sx, cy * cx
    );
}

vec3 bezierPoint(vec3 p0, vec3 p1, vec3 p2, float t) {
    float u = 1.0 - t;
    return u * u * p0 + 2.0 * u * t * p1 + t * t * p2;
}

vec3 bezierTangent(vec3 p0, vec3 p1, vec3 p2, float t) {
    float u = 1.0 - t;
    return 2.0 * u * (p1 - p0) + 2.0 * t * (p2 - p1);
}

void main() {
    mat3 rot = rotate(uTheta, uPhi);

    float endpointR = GLOBE_R + uMarkerElevation;
    vec3 from = aArcFrom * endpointR;
    vec3 to   = aArcTo   * endpointR;

    vec3 midSum = aArcFrom + aArcTo;
    float midLen = length(midSum);
    vec3 midDir = midLen > 0.001 ? midSum / midLen : vec3(0.0, 1.0, 0.0);
    vec3 mid = midDir * (GLOBE_R + aArcHeight);

    float t = aPosition.x;
    vec3 arcPoint = bezierPoint(from, mid, to, t);
    vec3 rotatedPoint = rot * arcPoint;

    vec3 rawTangent = bezierTangent(from, mid, to, t);
    vec3 rotatedTangent = rot * rawTangent;

    vec2 screenTangent = rotatedTangent.xy;
    float screenTangentLen = length(screenTangent);
    vec2 screenPerp = screenTangentLen > 0.001
        ? vec2(-screenTangent.y, screenTangent.x) / screenTangentLen
        : vec2(1.0, 0.0);

    float aspect = uResolution.x / uResolution.y;
    vec2 baseScreenPos = rotatedPoint.xy * vec2(1.0 / aspect, 1.0) * uScale + uOffset * vec2(1.0, -1.0) * uScale / uResolution;
    vec2 screenPos = baseScreenPos + screenPerp * aArcWidth * aPosition.y * uScale;

    gl_Position = vec4(screenPos, 0.0, 1.0);
    vArcColor = aArcColor;
    vHasColor = aHasColor;
    vDepth = rotatedPoint.z;
    vRadialDist = length(rotatedPoint.xy);
}
"""

    const val ARC_FRAG = """#version 300 es
precision highp float;
in vec3 vArcColor;
in float vHasColor;
in float vDepth;
in float vRadialDist;
uniform vec3 uArcColor;
out vec4 fragColor;
const float GLOBE_R = 0.8;
void main() {
    if (vDepth < 0.0 && vRadialDist < GLOBE_R) discard;
    float fade = smoothstep(-0.15, 0.15, vDepth);
    vec3 col = vHasColor > 0.5 ? vArcColor : uArcColor;
    fragColor = vec4(col, fade);
}
"""
}
