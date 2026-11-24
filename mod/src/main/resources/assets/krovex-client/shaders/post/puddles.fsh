#version 330

// The Storm shader. Mode 0 runs right after the world is drawn: puddles with reflections, haze, a dimmed sky, glow
// and optional rain. Mode 1 runs after the hand is drawn: the colour grade (saturation, brightness, contrast, tint).

uniform sampler2D InSampler;      // the frame so far
uniform sampler2D DepthSampler;   // the world's depth buffer (mode 0 only)

layout(std140) uniform PuddleConfig {
    mat4 InvViewProj;   // screen -> position relative to the camera
    mat4 ViewProj;      // direction relative to the camera -> screen
    vec4 Camera;        // xyz camera position (x and z wrapped), w time in seconds
    vec4 Params;        // x puddle coverage 0..1, y wet reflections 0..1, z storm amount 0..1, w rain 0..1
    vec4 Zenith;        // colour of the sky straight up (active sky preset)
    vec4 Horizon;       // colour of the sky at the horizon
    vec4 Storm;         // x sky brightness, y haze, z glow, w rain streaks on/off
    vec4 Grade;         // x saturation, y brightness, z contrast, w colour tint amount
    vec4 Tint;          // rgb tint colour, w mode (0 world pass, 1 grade pass)
    vec4 Extra;         // x how deep in a puddle the player stands (0..1)
};

in vec2 texCoord;

out vec4 fragColor;

// no sin(): only fract / multiply / add, so the Java port in Puddles.java gets the same numbers
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) { return noise(p) * 0.55 + noise(p * 2.13 + 7.1) * 0.28 + noise(p * 4.7 + 3.3) * 0.17; }

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 positionAt(vec2 uv, float depth) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

/** How much darker the whole world gets under the storm (the sky itself goes down to Storm.x). */
float worldLight() { return 0.5 + 0.5 * Storm.x; }

/** The colour of the storm's air: the sky's horizon colour pulled toward a cold grey. */
vec3 hazeColour() { return mix(Horizon.rgb, vec3(0.20, 0.25, 0.28), 0.6); }

void gradePass() {
    float amount = Params.z;
    vec2 px = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 c = texture(InSampler, texCoord).rgb;
    // sharpen: the haze and glow soften the picture, so put the edge detail back (unsharp mask)
    vec3 around = (texture(InSampler, texCoord + vec2(px.x, 0.0)).rgb + texture(InSampler, texCoord - vec2(px.x, 0.0)).rgb
                 + texture(InSampler, texCoord + vec2(0.0, px.y)).rgb + texture(InSampler, texCoord - vec2(0.0, px.y)).rgb) * 0.25;
    c += (c - around) * 0.75 * amount;
    float l = luma(c);
    c = mix(vec3(l), c, mix(1.0, Grade.x, amount));                              // saturation
    c *= mix(1.0, Grade.y * worldLight(), amount);                              // brightness, and the storm's dimmer daylight
    c = (c - 0.5) * mix(1.0, Grade.z, amount) + 0.5;                             // contrast
    c = mix(c, c * 0.45 + luma(c) * Tint.rgb * 1.15, clamp(Grade.w * amount, 0.0, 1.0));   // colour tint

    // wet skin: the hand (the only thing drawn close enough to have a tiny depth) turns glossy, much more so in a puddle
    float depth = texture(DepthSampler, texCoord).r;
    if (depth < 0.85) {
        float wet = clamp(max(Extra.x, Params.w * 0.35), 0.0, 1.0);
        if (wet > 0.01) {
            vec3 skyTone = mix(Horizon.rgb, Zenith.rgb, 0.5) * 0.9 + Tint.rgb * 0.15;
            float edge = clamp((abs(dFdx(l)) + abs(dFdy(l))) * 16.0, 0.0, 1.0);
            float drift = Camera.w * 0.5;
            float band = smoothstep(0.78, 1.0, sin((texCoord.x * 4.0 + texCoord.y * 7.0) * 3.14159 + drift) * 0.5 + 0.5);
            float speck = smoothstep(0.82, 1.0, noise(texCoord * vec2(46.0, 26.0) + drift * 0.3));
            c = mix(c, c * 0.72 + skyTone * 0.62, 0.55 * wet);                  // the sky mirrored in the skin
            c += vec3(0.30, 0.34, 0.38) * (edge * 0.9 + band * 0.55 + speck * 0.6) * wet;   // glossy highlights
        }
    }
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}

void main() {
    if (Tint.w > 0.5) { gradePass(); return; }

    vec3 base = texture(InSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    float amount = Params.z;
    vec3 haze = hazeColour();
    vec3 colour = base;

    if (depth >= 0.999995) {
        // the sky: dimmed to "Sky brightness" (the grade pass darkens everything by worldLight afterwards)
        colour = base * mix(1.0, clamp(Storm.x / worldLight(), 0.0, 1.0), amount);
    } else {
        vec3 rel = positionAt(texCoord, depth);                            // this pixel, relative to the camera
        float dist = length(rel);
        float fade = 1.0 - smoothstep(60.0, 110.0, dist);

        // which way the surface faces, from how the position changes across neighbouring pixels
        vec3 n = normalize(cross(dFdx(rel), dFdy(rel)));
        if (dot(n, rel) > 0.0) n = -n;
        float ground = smoothstep(0.93, 0.99, n.y) * fade;                 // flat and facing up

        if (ground > 0.0 && Params.x > 0.001) {
            // organic puddle shapes: two sizes of noise in world space, so they stay put as you move
            vec2 world = rel.xz + Camera.xz;
            float field = fbm(world * 0.085) * 0.74 + fbm(world * 0.31 + 31.7) * 0.26;
            float threshold = mix(0.74, 0.27, clamp(Params.x, 0.0, 1.0));
            float mask = smoothstep(threshold, threshold + 0.07, field) * ground;
            float damp = smoothstep(threshold - 0.16, threshold + 0.02, field) * ground * amount;

            colour = base * mix(1.0, 0.84, damp);                           // wet ground around a puddle is darker
            if (mask > 0.003) {
                vec3 viewDir = normalize(rel);
                float ripple = 0.018 + 0.085 * max(Params.w, Storm.w * 0.7);
                vec2 wave = vec2(noise(world * 3.1 + Camera.w * 0.6), noise(world * 3.1 + 17.0 - Camera.w * 0.5)) - 0.5;
                vec2 drops = vec2(noise(world * 9.0 + Camera.w * 2.3), noise(world * 9.0 - Camera.w * 2.1)) - 0.5;
                float busy = max(Params.w, Storm.w);
                vec3 normal = normalize(vec3(wave.x * ripple + drops.x * ripple * busy, 1.0, wave.y * ripple + drops.y * ripple * busy));
                vec3 mirrored = reflect(viewDir, normal);

                // the sky preset's own colours (seen through the storm's air) where the mirrored view is not on screen
                vec3 sky = mix(mix(Horizon.rgb, Zenith.rgb, smoothstep(0.0, 0.85, mirrored.y)), haze, 0.45);
                vec3 reflection = sky;
                vec4 clip = ViewProj * vec4(mirrored, 0.0);
                if (clip.w > 0.0) {
                    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
                    vec2 edge = smoothstep(vec2(0.0), vec2(0.07), uv) * (1.0 - smoothstep(vec2(0.93), vec2(1.0), uv));
                    reflection = mix(sky, texture(InSampler, uv).rgb, edge.x * edge.y);     // what is really up there this frame
                }
                float fresnel = pow(1.0 - clamp(dot(-viewDir, vec3(0.0, 1.0, 0.0)), 0.0, 1.0), 3.0);
                // in the puddle you stand in (and close around it) the water is nearly a mirror
                float nearYou = Extra.x * (1.0 - smoothstep(2.0, 14.0, dist));
                float wet = clamp(Params.y * (0.55 + 0.45 * fresnel) + nearYou * (0.55 + 0.45 * fresnel), 0.0, 1.0);
                vec3 water = mix(base * mix(0.62, 0.38, nearYou), reflection, wet);
                water += vec3(0.05) * smoothstep(0.6, 1.0, fresnel) * Params.y;              // a soft sheen at grazing angles
                colour = mix(colour, water, mask);
            }
        }

        // haze: the air itself, thicker with distance
        float fog = Storm.y * amount * (1.0 - exp(-dist * 0.011)) * 0.6;
        colour = mix(colour, haze, clamp(fog, 0.0, 1.0));
    }

    // glow: bright things bleed softly into the wet air
    if (Storm.z > 0.01) {
        vec2 px = 1.0 / vec2(textureSize(InSampler, 0));
        vec3 bloom = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7853982;
            vec2 offset = vec2(cos(a), sin(a)) * px * (i % 2 == 0 ? 2.5 : 5.5);
            bloom += max(texture(InSampler, texCoord + offset).rgb - 0.72, 0.0);
        }
        colour += bloom / 8.0 * Storm.z * amount * 1.1;
    }

    // rain: thin fast streaks across the view
    if (Storm.w > 0.5) {
        vec2 size = vec2(textureSize(InSampler, 0));
        float column = floor(texCoord.x * size.x / 3.0);
        float speed = 2.2 + hash(vec2(column, 3.0)) * 1.6;
        float along = fract(texCoord.y * 1.4 + Camera.w * speed + hash(vec2(column, 7.0)));
        float streak = step(0.93, hash(vec2(column, 11.0))) * smoothstep(0.0, 0.05, along) * (1.0 - smoothstep(0.05, 0.16, along));
        colour += vec3(0.55, 0.62, 0.70) * streak * 0.16 * amount;
    }

    fragColor = vec4(clamp(colour, 0.0, 1.0), 1.0);
}
