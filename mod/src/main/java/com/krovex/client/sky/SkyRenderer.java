package com.krovex.client.sky;

import com.krovex.client.KrovexClientMod;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws the animated sky presets. Every preset is a full sphere: a solid gradient dome that replaces
 * vanilla's sky (horizon glow, sun, moon and fog included, see the sky mixins), with translucent detail
 * layers on top. Everything is built once into one vertex buffer of quads and split into "layers" that
 * each get their own rotation speed, brightness curve and extra drift, so one static mesh still
 * shimmers, twinkles, flashes and slides.
 */
public final class SkyRenderer implements AutoCloseable {
    private static final float R = 85f;
    private static final float TAU = (float) (2 * Math.PI);
    private static final float HALF_PI = TAU / 4;
    private static final int STRIDE = 16;

    /** Brightness multiplier (0 hides the layer) as a function of seconds since the mesh was built. */
    @FunctionalInterface interface Curve { float at(double seconds); }
    record Layer(int baseVertex, int quads, float spin, Curve pulse, Curve drift, Curve rise, boolean solid) {}
    record MeshKey(SkyPreset.Kind kind, int primary, int secondary) {}
    /** The three colours of a preset's background gradient: straight up, mid-sky, and at the horizon (also the fog colour). */
    record Palette(int zenith, int mid, int horizon) {}

    private final List<Layer> layers = new ArrayList<>();
    private GpuBuffer buffer;
    private int totalQuads;
    private MeshKey key;
    private long previousFrame, startNanos;
    private float rotation;

    // ------------------------------------------------------- shared with the mixins

    /** The preset currently replacing vanilla's sky, or null when vanilla's own sky should show. */
    public static SkyPreset activeCustom() {
        var store = KrovexClientMod.skyPresetStore();
        if (store == null || !store.enabled()) return null;
        var preset = store.active();
        return preset.supportsAnimationControls() && preset.intensity() > 0 ? preset : null;
    }

    /** Fog / horizon colour for a preset, so distant terrain fades into the custom sky instead of vanilla's orange. */
    public static int horizonColor(SkyPreset preset) {
        return palette(preset.kind()).horizon();
    }

    /** Top / middle / horizon colours for the Sky panel's preview tiles. */
    public static int[] previewColors(SkyPreset.Kind kind) {
        if (kind == SkyPreset.Kind.VANILLA) return new int[] {0xFF2A5FB0, 0xFF5B9BE0, 0xFFBFDFF5};
        if (kind == SkyPreset.Kind.IMAGE) return new int[] {0xFF3A3550, 0xFF4A4468, 0xFF6A6488};
        var p = palette(kind);
        return new int[] {p.zenith(), p.mid(), p.horizon()};
    }

    private static Palette palette(SkyPreset.Kind kind) {
        return switch (kind) {
            case TOPOGRAPHIC_LINES -> new Palette(0xFF05030D, 0xFF120A28, 0xFF241048);
            case AURORA_RIBBONS -> new Palette(0xFF02050E, 0xFF06182A, 0xFF0B2E3B);
            case STARFIELD -> new Palette(0xFF02030A, 0xFF080D26, 0xFF121A45);
            case THUNDERCLOUDS -> new Palette(0xFF090C14, 0xFF171C2B, 0xFF353E58);
            case NEBULA -> new Palette(0xFF04030D, 0xFF0D0620, 0xFF1C0D3A);
            case SYNTHWAVE -> new Palette(0xFF07011A, 0xFF1E0538, 0xFF5A0F63);
            case GALAXY -> new Palette(0xFF010208, 0xFF050818, 0xFF0A1030);
            case DUSK -> new Palette(0xFF141039, 0xFF52306E, 0xFFE8785A);
            case SNOWFALL -> new Palette(0xFF14213D, 0xFF3C5683, 0xFFB7CCE6);
            case SAKURA -> new Palette(0xFF2E2150, 0xFFA85C97, 0xFFFFC2B0);
            case OCEAN -> new Palette(0xFF1394A8, 0xFF095A72, 0xFF031B2A);
            case PRISM -> new Palette(0xFF5C93E4, 0xFFA9CBF6, 0xFFEFF6FF);
            case RAINFALL -> new Palette(0xFF161C28, 0xFF283244, 0xFF485670);
            case COSMIC_RIFT -> new Palette(0xFF040109, 0xFF0F0424, 0xFF2A0C46);
            case METEORS -> new Palette(0xFF02030C, 0xFF0A1230, 0xFF4B2F5E);
            case BLACK_HOLE -> new Palette(0xFF010106, 0xFF050818, 0xFF0D1232);
            case LANTERNS -> new Palette(0xFF060A1C, 0xFF151A3C, 0xFF46284A);
            case TEMPEST -> new Palette(0xFF0B0C11, 0xFF15171D, 0xFF24272F);
            case INFERNO -> new Palette(0xFF0F0305, 0xFF45100C, 0xFFE0511C);
            case MATRIX -> new Palette(0xFF000805, 0xFF011309, 0xFF04301A);
            case GOLDEN_HOUR -> new Palette(0xFF27456F, 0xFFD39A62, 0xFFFFD389);
            case VORTEX -> new Palette(0xFF04010D, 0xFF120533, 0xFF2D0D5E);
            case FIREFLIES -> new Palette(0xFF02050C, 0xFF07161F, 0xFF10373A);
            default -> new Palette(0xFF000000, 0xFF000000, 0xFF000000);
        };
    }

    // ------------------------------------------------------------------ drawing

    public void draw() {
        var preset = activeCustom();
        if (preset == null) { previousFrame = 0; return; }
        var nextKey = new MeshKey(preset.kind(), preset.primaryColorArgb(), preset.secondaryColorArgb());
        if (!nextKey.equals(key)) {
            rebuild(nextKey);
            rotation = 0;
            previousFrame = 0;
            startNanos = System.nanoTime();
        }
        if (buffer == null || layers.isEmpty()) return;
        long now = System.nanoTime();
        if (previousFrame != 0 && preset.animationEnabled()) {
            float speed = preset.lowMotion() ? Math.min(.25f, preset.animationSpeed()) : preset.animationSpeed();
            rotation = (rotation + Math.min(.1f, (now - previousFrame) / 1e9f) * speed * .045f) % TAU;
        }
        previousFrame = now;
        double seconds = preset.animationEnabled() ? (now - startNanos) / 1e9 : 0;

        var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        GpuBuffer indexBuffer = indices.getIndexBuffer(totalQuads * 6);
        // Uniform uploads go through the command encoder, which refuses new commands while a render
        // pass is open -- so every visible layer's uniforms are written BEFORE the pass starts.
        var visible = new ArrayList<Layer>();
        var uniformSlices = new ArrayList<com.mojang.blaze3d.buffers.GpuBufferSlice>();
        for (Layer layer : layers) {
            float level = layer.pulse() == null ? 1f : layer.pulse().at(seconds);
            if (level <= .004f) continue;
            float turn = rotation * layer.spin() + (layer.drift() == null ? 0f : layer.drift().at(seconds));
            var matrix = new Matrix4f(RenderSystem.getModelViewMatrix());
            if (layer.rise() != null) matrix.translate(0f, layer.rise().at(seconds), 0f);   // falling snow, rising embers ...
            matrix.rotateY(turn);
            float opacity = layer.solid() ? Math.min(1f, level) : Math.min(1f, preset.intensity() * level);
            uniformSlices.add(RenderSystem.getDynamicUniforms().write(matrix,
                    new Vector4f(1, 1, 1, opacity), new Vector3f(), new Matrix4f()));
            visible.add(layer);
        }
        if (visible.isEmpty()) return;
        var target = MinecraftClient.getInstance().getFramebuffer();
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Krovex sky effects", target.getColorAttachmentView(), OptionalInt.empty(),
                target.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.DEBUG_QUADS);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setVertexBuffer(0, buffer);
            pass.setIndexBuffer(indexBuffer, indices.getIndexType());
            for (int i = 0; i < visible.size(); i++) {
                pass.setUniform("DynamicTransforms", uniformSlices.get(i));
                pass.drawIndexed(visible.get(i).baseVertex(), 0, visible.get(i).quads() * 6, 1);
            }
        }
    }

    // ------------------------------------------------------------------- mesh

    static final class Mesh {
        float[] pos = new float[3 * 16384];
        int[] col = new int[16384];
        int count, mark;

        void vertex(float x, float y, float z, int argb) {
            if (count == col.length) {
                col = Arrays.copyOf(col, col.length * 2);
                pos = Arrays.copyOf(pos, pos.length * 2);
            }
            pos[count * 3] = x; pos[count * 3 + 1] = y; pos[count * 3 + 2] = z;
            col[count++] = argb;
        }

        /** A dome point from azimuth / elevation. */
        void ae(float az, float el, int argb) {
            float c = (float) Math.cos(el);
            vertex(R * c * (float) Math.cos(az), R * (float) Math.sin(el), R * c * (float) Math.sin(az), argb);
        }

        /** A dome point offset (in radians, "east"/"up") from an anchor direction: shapes stay undistorted near the zenith. */
        void tp(float az, float el, float dx, float dy, int argb) {
            float ce = (float) Math.cos(el), se = (float) Math.sin(el), ca = (float) Math.cos(az), sa = (float) Math.sin(az);
            float x = ce * ca + dx * -sa + dy * -se * ca;
            float y = se + dy * ce;
            float z = ce * sa + dx * ca + dy * -se * sa;
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            vertex(R * x / len, R * y / len, R * z / len, argb);
        }

        void layer(List<Layer> into, float spin, Curve pulse, Curve drift, Curve rise, boolean solid) {
            int quads = (count - mark) / 4;
            if (quads > 0) into.add(new Layer(mark, quads, spin, pulse, drift, rise, solid));
            mark = count;
        }
    }

    /** Result of building a preset's mesh (also used by the offline preview renderer). */
    record Built(Mesh mesh, List<Layer> layers, Palette palette) {}

    static Built buildForPreview(MeshKey next) {
        SkyRenderer renderer = new SkyRenderer();
        Mesh m = renderer.construct(next);
        return new Built(m, renderer.layers, palette(next.kind()));
    }

    private void rebuild(MeshKey next) {
        close();
        var m = construct(next);
        finishBuffer(m, next);
    }

    private Mesh construct(MeshKey next) {
        var m = new Mesh();
        var pal = palette(next.kind());
        buildDome(m, pal);
        m.layer(layers, 0f, null, null, null, true);
        switch (next.kind()) {
            case STARFIELD -> buildStars(m, next.primary());
            case THUNDERCLOUDS -> buildStorm(m, next.primary(), next.secondary());
            case AURORA_RIBBONS -> buildAurora(m, next.primary(), next.secondary());
            case TOPOGRAPHIC_LINES -> buildTopography(m, next.primary(), next.secondary());
            case NEBULA -> buildNebula(m, next.primary());
            case SYNTHWAVE -> buildSynthwave(m, next.primary());
            case GALAXY -> buildGalaxy(m, next.primary());
            case DUSK -> buildDusk(m, next.primary());
            case SNOWFALL -> buildSnow(m, next.primary());
            case SAKURA -> buildSakura(m, next.primary());
            case OCEAN -> buildOcean(m, next.primary());
            case INFERNO -> buildInferno(m, next.primary());
            case MATRIX -> buildMatrix(m, next.primary());
            case GOLDEN_HOUR -> buildGolden(m, next.primary());
            case VORTEX -> buildVortex(m, next.primary(), next.secondary());
            case FIREFLIES -> buildFireflies(m, next.primary());
            case PRISM -> buildPrism(m, next.primary());
            case RAINFALL -> buildRain(m, next.primary());
            case COSMIC_RIFT -> buildRift(m, next.primary(), next.secondary());
            case METEORS -> buildMeteors(m, next.primary());
            case BLACK_HOLE -> buildBlackHole(m, next.primary());
            case LANTERNS -> buildLanterns(m, next.primary());
            case TEMPEST -> buildTempest(m, next.primary(), next.secondary());
            default -> throw new IllegalArgumentException("No mesh for " + next.kind());
        }
        return m;
    }

    private void finishBuffer(Mesh m, MeshKey next) {
        totalQuads = m.count / 4;
        ByteBuffer data = ByteBuffer.allocateDirect(m.count * STRIDE).order(ByteOrder.nativeOrder());
        for (int i = 0; i < m.count; i++) {
            int color = m.col[i];
            data.putFloat(m.pos[i * 3]).putFloat(m.pos[i * 3 + 1]).putFloat(m.pos[i * 3 + 2])
                    .put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >>> 24));
        }
        data.flip();
        buffer = RenderSystem.getDevice().createBuffer(() -> "Krovex sky mesh", GpuBuffer.USAGE_VERTEX, data);
        key = next;
    }

    // ---------------------------------------------------------------- helpers

    private static int alpha(int color, float a) {
        return color & 0xFFFFFF | Math.clamp(Math.round(a), 0, 255) << 24;
    }

    private static int mix(int a, int b, float t) { return SkyColorMath.lerpArgb(a, b, Math.clamp(t, 0f, 1f)); }

    private static int lighten(int c, float t) { return mix(c, 0xFFFFFFFF, t); }

    private static float smooth(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** The background gradient at an elevation: horizon -> mid-sky -> zenith above, darkening below the horizon. */
    private static int domeColor(Palette p, float el) {
        if (el >= 0) {
            float t = smooth(0f, 1.3f, el);
            return t < .4f ? mix(p.horizon(), p.mid(), t / .4f) : mix(p.mid(), p.zenith(), (t - .4f) / .6f);
        }
        return mix(p.horizon(), p.zenith(), smooth(0f, 1.1f, -el) * .9f);
    }

    /** The solid sky: a whole sphere of gradient quads (nothing of vanilla's sky shows through). */
    private static void buildDome(Mesh m, Palette p) {
        float[] rings = {-HALF_PI, -1.15f, -.7f, -.35f, -.14f, 0f, .05f, .14f, .26f, .42f, .62f, .86f, 1.12f, 1.36f, HALF_PI};
        int segments = 32;
        for (int i = 0; i + 1 < rings.length; i++) {
            int lower = domeColor(p, rings[i]), upper = domeColor(p, rings[i + 1]);
            for (int j = 0; j < segments; j++) {
                float a0 = j * TAU / segments, a1 = (j + 1) * TAU / segments;
                m.ae(a0, rings[i], lower); m.ae(a1, rings[i], lower);
                m.ae(a1, rings[i + 1], upper); m.ae(a0, rings[i + 1], upper);
            }
        }
    }

    /** A soft blob: dense centre fading through two rings to a ragged transparent rim (25 quads, 10 lobes). */
    private static void puff(Mesh m, float az, float el, float ox, float oy, float rx, float ry, int color, float a, Random r) {
        final int n = 10;
        float[] jitter = new float[n];
        for (int k = 0; k < n; k++) jitter[k] = .78f + r.nextFloat() * .44f;
        int solid = alpha(color, a), inner = alpha(color, a * .8f), outer = alpha(color, a * .34f), clear = alpha(color, 0);
        for (int k = 0; k < n; k += 2) {          // dense core
            m.tp(az, el, ox, oy, solid);
            for (int s = 0; s < 3; s++) {
                float t = (k + s) % n * TAU / n;
                m.tp(az, el, ox + rx * .36f * (float) Math.cos(t), oy + ry * .36f * (float) Math.sin(t), inner);
            }
        }
        for (int k = 0; k < n; k++) {
            float t0 = k * TAU / n, t1 = (k + 1) % n * TAU / n;
            float mid0 = .66f * (.62f + .38f * jitter[k]), mid1 = .66f * (.62f + .38f * jitter[(k + 1) % n]);   // shared by both rings: no seams
            float far0 = jitter[k], far1 = jitter[(k + 1) % n];
            // inner ring -> middle ring
            m.tp(az, el, ox + rx * .36f * (float) Math.cos(t0), oy + ry * .36f * (float) Math.sin(t0), inner);
            m.tp(az, el, ox + rx * mid0 * (float) Math.cos(t0), oy + ry * mid0 * (float) Math.sin(t0), outer);
            m.tp(az, el, ox + rx * mid1 * (float) Math.cos(t1), oy + ry * mid1 * (float) Math.sin(t1), outer);
            m.tp(az, el, ox + rx * .36f * (float) Math.cos(t1), oy + ry * .36f * (float) Math.sin(t1), inner);
            // middle ring -> ragged rim
            m.tp(az, el, ox + rx * mid0 * (float) Math.cos(t0), oy + ry * mid0 * (float) Math.sin(t0), outer);
            m.tp(az, el, ox + rx * far0 * (float) Math.cos(t0), oy + ry * far0 * (float) Math.sin(t0), clear);
            m.tp(az, el, ox + rx * far1 * (float) Math.cos(t1), oy + ry * far1 * (float) Math.sin(t1), clear);
            m.tp(az, el, ox + rx * mid1 * (float) Math.cos(t1), oy + ry * mid1 * (float) Math.sin(t1), outer);
        }
    }
    /** A star / glow: bright centre, soft diamond falloff (2 quads). */
    private static void glint(Mesh m, float az, float el, float size, int color, float a, float rim) {
        int centre = alpha(color, a), edge = alpha(color, a * rim);
        float[][] corner = {{size, 0}, {0, size}, {-size, 0}, {0, -size}};
        for (int half = 0; half < 2; half++) {
            m.tp(az, el, 0, 0, centre);
            for (int s = 0; s < 3; s++) {
                float[] c = corner[(half * 2 + s) % 4];
                m.tp(az, el, c[0], c[1], edge);
            }
        }
    }

    /** A flat ribbon between two dome points with a physical width (radians) and a colour at each end. */
    private static void ribbon(Mesh m, float a0, float e0, float a1, float e1, float width, int c0, int c1) {
        float k = (float) Math.cos((e0 + e1) / 2);
        float dx = (a1 - a0) * k, dy = e1 - e0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-6f) return;
        float oa = -dy / len * width / 2 / Math.max(k, .05f), oe = dx / len * width / 2;
        m.ae(a0 + oa, e0 + oe, c0);
        m.ae(a1 + oa, e1 + oe, c1);
        m.ae(a1 - oa, e1 - oe, c1);
        m.ae(a0 - oa, e0 - oe, c0);
    }

    /** Like {@link #ribbon} but the colour peaks along the centre line and fades to nothing at both edges (a glow, not a strip). */
    private static void softRibbon(Mesh m, float a0, float e0, float a1, float e1, float width, int color, float c0, float c1) {
        float k = (float) Math.cos((e0 + e1) / 2);
        float dx = (a1 - a0) * k, dy = e1 - e0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-6f) return;
        float oa = -dy / len * width / Math.max(k, .05f), oe = dx / len * width;
        int s0 = alpha(color, c0), s1 = alpha(color, c1), clear = alpha(color, 0);
        m.ae(a0 - oa, e0 - oe, clear); m.ae(a0, e0, s0); m.ae(a1, e1, s1); m.ae(a1 - oa, e1 - oe, clear);
        m.ae(a0, e0, s0); m.ae(a0 + oa, e0 + oe, clear); m.ae(a1 + oa, e1 + oe, clear); m.ae(a1, e1, s1);
    }

    /** A glowing strip along a polyline with shared normals (no kinks or gaps): colour peaks on the line, fades to nothing at both edges. */
    private static void softStrip(Mesh m, List<float[]> pts, float[] width, int color, float[] alphaAt) {
        int n = pts.size();
        float[][] left = new float[n][], right = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] p = pts.get(i), p0 = pts.get(Math.max(0, i - 1)), p1 = pts.get(Math.min(n - 1, i + 1));
            float k = (float) Math.cos(p[1]);
            float dx = (p1[0] - p0[0]) * k, dy = p1[1] - p0[1], len = (float) Math.hypot(dx, dy);
            if (len < 1e-7f) { len = 1e-7f; }
            float oa = -dy / len * width[i] / Math.max(k, .05f), oe = dx / len * width[i];
            left[i] = new float[] {p[0] - oa, p[1] - oe};
            right[i] = new float[] {p[0] + oa, p[1] + oe};
        }
        int clear = alpha(color, 0);
        for (int i = 0; i + 1 < n; i++) {
            float[] p = pts.get(i), q = pts.get(i + 1);
            int c0 = alpha(color, alphaAt[i]), c1 = alpha(color, alphaAt[i + 1]);
            m.ae(left[i][0], left[i][1], clear); m.ae(p[0], p[1], c0); m.ae(q[0], q[1], c1); m.ae(left[i + 1][0], left[i + 1][1], clear);
            m.ae(p[0], p[1], c0); m.ae(right[i][0], right[i][1], clear); m.ae(right[i + 1][0], right[i + 1][1], clear); m.ae(q[0], q[1], c1);
        }
    }

    /** Smooths a rough polyline (Catmull-Rom) into {@code steps} points per segment. */
    private static List<float[]> smooth(List<float[]> rough, int steps) {
        List<float[]> out = new ArrayList<>();
        int n = rough.size();
        for (int i = 0; i + 1 < n; i++) {
            float[] p0 = rough.get(Math.max(0, i - 1)), p1 = rough.get(i), p2 = rough.get(i + 1), p3 = rough.get(Math.min(n - 1, i + 2));
            for (int s = 0; s < steps; s++) {
                float t = s / (float) steps, t2 = t * t, t3 = t2 * t;
                float[] pt = new float[2];
                for (int d = 0; d < 2; d++) {
                    pt[d] = .5f * ((2 * p1[d]) + (-p0[d] + p2[d]) * t + (2 * p0[d] - 5 * p1[d] + 4 * p2[d] - p3[d]) * t2 + (-p0[d] + 3 * p1[d] - 3 * p2[d] + p3[d]) * t3);
                }
                out.add(pt);
            }
        }
        out.add(rough.get(n - 1));
        return out;
    }

    private void emit(Mesh m, float spin, Curve pulse, Curve drift) { m.layer(layers, spin, pulse, drift, null, false); }

    /** A layer that also slides vertically over time (period-wrapped particle fields: snow, embers, bubbles, rain). */
    private void emitRising(Mesh m, float spin, Curve pulse, Curve drift, Curve rise) { m.layer(layers, spin, pulse, drift, rise, false); }

    private static Curve breathe(float phase, float speed, float depth) {
        return t -> 1f - depth + depth * (float) (.5 + .5 * Math.sin(t * speed + phase));
    }

    private static final int[] STAR_TINTS = {0xFFFFFFFF, 0xFFBFD4FF, 0xFFFFE3BC, 0xFFD6C8FF};

    /** Scattered stars over the whole sphere in several groups that twinkle out of step with each other. */
    private void scatterStars(Mesh m, Random r, int primary, int groups, int perGroup, float brightChance) {
        for (int group = 0; group < groups; group++) {
            for (int i = 0; i < perGroup; i++) {
                float el = (float) Math.asin(-.4 + r.nextFloat() * 1.4), az = r.nextFloat() * TAU;
                boolean bright = r.nextFloat() < brightChance;
                int tint = mix(primary, STAR_TINTS[r.nextInt(STAR_TINTS.length)], .65f);
                float size = bright ? .0052f + r.nextFloat() * .0042f : .0022f + r.nextFloat() * .0026f;
                glint(m, az, el, size, tint, bright ? 255 : 150 + r.nextInt(100), .28f);
                if (bright) { glint(m, az, el, size * 4.2f, tint, 52, 0f); glint(m, az, el, size * 9f, tint, 22, 0f); }
            }
            emit(m, 1f, breathe(group * 2.1f, 1.1f + group * .55f, .42f), null);
        }
    }

    /** Soft coloured gas clouds drifting over the sphere. */
    private void wisps(Mesh m, Random r, int count, int[] colors, float minSize, float maxSize, float minAlpha, float maxAlpha) {
        for (int i = 0; i < count; i++) {
            float az = r.nextFloat() * TAU, el = (float) Math.asin(-.3 + r.nextFloat() * 1.3);
            float size = minSize + r.nextFloat() * (maxSize - minSize);
            puff(m, az, el, 0, 0, size * (1f + r.nextFloat() * .6f), size, colors[r.nextInt(colors.length)],
                    minAlpha + r.nextFloat() * (maxAlpha - minAlpha), r);
        }
    }

    // ---------------------------------------------------------------- Starfield

    private void buildStars(Mesh m, int primary) {
        Random r = new Random(9001);
        // soft violet / blue gas behind the stars
        wisps(m, r, 24, new int[] {0xFF5B6CFF, 0xFF8A5BFF, 0xFF3A8CFF, 0xFFB35BFF}, .35f, .85f, 16, 40);
        emit(m, .5f, breathe(0f, .3f, .25f), null);
        // the Milky Way: a tilted band of dust and a few thousand faint stars
        float nx = .42f, ny = .78f, nz = .46f, nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl; ny /= nl; nz /= nl;
        float ux = -nz, uy = 0, uz = nx, ul = (float) Math.sqrt(ux * ux + uz * uz);
        ux /= ul; uz /= ul;
        float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
        for (int i = 0; i < 90; i++) {
            float phi = r.nextFloat() * TAU, spread = (float) r.nextGaussian() * .14f;
            float x = (float) Math.cos(phi) * ux + (float) Math.sin(phi) * vx + spread * nx;
            float y = (float) Math.cos(phi) * uy + (float) Math.sin(phi) * vy + spread * ny;
            float z = (float) Math.cos(phi) * uz + (float) Math.sin(phi) * vz + spread * nz;
            float el = (float) Math.asin(Math.clamp(y / (float) Math.sqrt(x * x + y * y + z * z), -1f, 1f));
            glint(m, (float) Math.atan2(z, x), el, .17f + r.nextFloat() * .18f, mix(primary, 0xFF9DB0FF, .55f), 17 + r.nextInt(18), 0f);
        }
        for (int i = 0; i < 1700; i++) {
            float phi = r.nextFloat() * TAU, spread = (float) r.nextGaussian() * .11f;
            float x = (float) Math.cos(phi) * ux + (float) Math.sin(phi) * vx + spread * nx;
            float y = (float) Math.cos(phi) * uy + (float) Math.sin(phi) * vy + spread * ny;
            float z = (float) Math.cos(phi) * uz + (float) Math.sin(phi) * vz + spread * nz;
            float el = (float) Math.asin(Math.clamp(y / (float) Math.sqrt(x * x + y * y + z * z), -1f, 1f));
            glint(m, (float) Math.atan2(z, x), el, .0017f + r.nextFloat() * .0016f, STAR_TINTS[r.nextInt(2)], 70 + r.nextInt(120), .2f);
        }
        emit(m, 1f, null, null);
        scatterStars(m, r, primary, 3, 620, .12f);
        shootingStars(m, r, primary);
    }

    private void shootingStars(Mesh m, Random r, int primary) {
        double[] periods = {7.3, 11.9, 17.1, 23.7};
        for (int k = 0; k < periods.length; k++) {
            float az = r.nextFloat() * TAU, el = .5f + r.nextFloat() * .55f;
            int head = 0xFFFFFFFF, tail = alpha(mix(primary, 0xFF9DB8FF, .5f), 0);
            m.tp(az, el, 0, .0026f, head); m.tp(az, el, 0, -.0026f, head);
            m.tp(az, el, .17f, .05f - .0005f, tail); m.tp(az, el, .17f, .05f + .0005f, tail);
            double period = periods[k], offset = k * 3.1;
            emit(m, 1f, t -> {
                double tt = (t + offset) % period;
                return tt < 1.2 ? (float) Math.sqrt(Math.sin(Math.PI * tt / 1.2)) : 0f;
            }, t -> (float) (((t + offset) % period) * .22));
        }
    }

    // ------------------------------------------------------------------ Aurora

    private void buildAurora(Mesh m, int primary, int secondary) {
        Random r = new Random(4242);
        // a faint glow hugging the horizon, above and below it
        for (int i = 0; i < 90; i++) {
            float a0 = i * TAU / 90, a1 = (i + 1) * TAU / 90;
            int g = alpha(primary, 64);
            m.ae(a0, 0f, g); m.ae(a1, 0f, g);
            m.ae(a1, .36f, alpha(secondary, 0)); m.ae(a0, .36f, alpha(secondary, 0));
            int low = alpha(primary, 34);
            m.ae(a0, 0f, low); m.ae(a1, 0f, low);
            m.ae(a1, -.3f, alpha(secondary, 0)); m.ae(a0, -.3f, alpha(secondary, 0));
        }
        emit(m, 1f, breathe(0, .5f, .3f), null);
        scatterStars(m, r, 0xFFFFFFFF, 2, 380, .06f);
        // fast shimmering rays that flicker through the curtains
        for (int group = 0; group < 3; group++) {
            for (int i = 0; i < 46; i++) {
                float az = r.nextFloat() * TAU, base = .13f + r.nextFloat() * .1f, top = base + .12f + r.nextFloat() * .18f, w = .003f + r.nextFloat() * .003f;
                int c = mix(primary, 0xFFFFFFFF, .4f);
                m.ae(az - w, base, alpha(c, 120)); m.ae(az + w, base, alpha(c, 120));
                m.ae(az + w * .3f, top, alpha(secondary, 0)); m.ae(az - w * .3f, top, alpha(secondary, 0));
            }
            emit(m, 1f + group * .18f, breathe(group * 2.4f, 1.8f + group * .9f, .95f), sway(.04f, .5f, group));
        }
        curtains(m, primary, secondary, 1f, 1f);
        curtains(m, primary, secondary, -1f, .26f); // faint reflection under the horizon
    }

    private void curtains(Mesh m, int primary, int secondary, float sign, float strength) {
        Random r = new Random(4242);
        for (int curtain = 0; curtain < 7; curtain++) {
            boolean far = curtain >= 4;
            float span = 2.0f + r.nextFloat() * .9f, centre = curtain * TAU / 7 + r.nextFloat() * .5f;
            float baseEl = .10f + r.nextFloat() * .10f, wave = .05f + r.nextFloat() * .06f, reach = (far ? .22f : .32f) + r.nextFloat() * .22f;
            int columns = 190;
            float prevAz = 0, prevBase = 0, prevTop = 0, prevBright = 0;
            for (int i = 0; i <= columns; i++) {
                float u = i / (float) columns, az = centre - span / 2 + u * span;
                float envelope = (float) Math.pow(Math.sin(Math.PI * u), .65);
                float rays = (float) Math.abs(Math.sin(az * 39 + curtain * 1.7) * .6 + Math.sin(az * 13 + curtain) * .4);
                float bright = (.4f + .6f * (float) Math.pow(rays, .85f)) * envelope * (far ? .62f : 1f) * strength;
                float base = baseEl + wave * (float) Math.sin(az * 3.1 + curtain) + wave * .45f * (float) Math.sin(az * 7.3 + curtain * 2);
                float height = reach * (.55f + .45f * (float) Math.sin(az * 5 + curtain * 2.3)) * (.35f + .65f * envelope);
                if (i > 0) {
                    int c0 = lighten(primary, .25f), c1 = mix(primary, secondary, .5f), c2 = mix(secondary, 0xFFFF5CD6, .55f);
                    int bottom0 = alpha(c0, 235 * prevBright), bottom1 = alpha(c0, 235 * bright);
                    int mid0 = alpha(c1, 160 * prevBright), mid1 = alpha(c1, 160 * bright);
                    int up0 = alpha(c2, 85 * prevBright), up1 = alpha(c2, 85 * bright);
                    int top = alpha(c2, 0);
                    float mb0 = prevBase + (prevTop - prevBase) * .38f, mb1 = base + height * .38f;
                    float ub0 = prevBase + (prevTop - prevBase) * .74f, ub1 = base + height * .74f;
                    m.ae(prevAz, sign * prevBase, bottom0); m.ae(az, sign * base, bottom1); m.ae(az, sign * mb1, mid1); m.ae(prevAz, sign * mb0, mid0);
                    m.ae(prevAz, sign * mb0, mid0); m.ae(az, sign * mb1, mid1); m.ae(az, sign * ub1, up1); m.ae(prevAz, sign * ub0, up0);
                    m.ae(prevAz, sign * ub0, up0); m.ae(az, sign * ub1, up1); m.ae(az, sign * (base + height), top); m.ae(prevAz, sign * prevTop, top);
                }
                prevAz = az; prevBase = base; prevTop = base + height; prevBright = bright;
            }
            float phase = curtain * 1.3f;
            emit(m, far ? .55f : 1f + curtain * .22f, breathe(phase, .5f + curtain * .07f, far ? .25f : .4f),
                    far ? null : t -> (float) (.05 * Math.sin(t * .3 + phase)));
        }
    }

    // -------------------------------------------------------------- Topography

    private static final class Field {
        final float[] cx, cy, cz, amp, sharp;
        final float phase;
        Field(Random r, int hills) {
            cx = new float[hills]; cy = new float[hills]; cz = new float[hills]; amp = new float[hills]; sharp = new float[hills];
            phase = r.nextFloat() * TAU;
            for (int i = 0; i < hills; i++) {
                float az = r.nextFloat() * TAU, el = -.9f + r.nextFloat() * 2.15f;
                cx[i] = (float) (Math.cos(el) * Math.cos(az)); cy[i] = (float) Math.sin(el); cz[i] = (float) (Math.cos(el) * Math.sin(az));
                amp[i] = .55f + r.nextFloat() * .45f;
                float sigma = .22f + r.nextFloat() * .3f;
                sharp[i] = 1f / (sigma * sigma);
            }
        }
        float at(float az, float el) {
            float ce = (float) Math.cos(el), x = ce * (float) Math.cos(az), y = (float) Math.sin(el), z = ce * (float) Math.sin(az);
            float v = 0;
            for (int i = 0; i < cx.length; i++) v += amp[i] * (float) Math.exp(-(1 - (x * cx[i] + y * cy[i] + z * cz[i])) * sharp[i]);
            return v + .09f * (float) (Math.sin(6 * x + 2.1 * y + phase) * Math.cos(5 * z + phase * .7));
        }
    }

    private void buildTopography(Mesh m, int primary, int secondary) {
        Random dust = new Random(31);
        scatterStars(m, dust, mix(primary, 0xFFFFFFFF, .5f), 1, 260, .03f);
        for (int pass = 0; pass < 2; pass++) {
            boolean back = pass == 1;
            Random r = new Random(7001 + pass * 131L);
            Field field = new Field(r, back ? 9 : 12);
            int az = back ? 150 : 240, el = 84;
            float el0 = -1.15f, el1 = 1.5f, dAz = TAU / az, dEl = (el1 - el0) / el;
            float[][] v = new float[az + 1][el + 1];
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int i = 0; i <= az; i++) for (int j = 0; j <= el; j++) {
                v[i][j] = field.at(i * dAz, el0 + j * dEl);
                lo = Math.min(lo, v[i][j]); hi = Math.max(hi, v[i][j]);
            }
            int tint = back ? mix(primary, 0xFF6FB4FF, .45f) : primary;
            // a soft glow under the hills so the map has body, not just outlines
            int step = 2;
            for (int i = 0; i + step <= az; i += step) for (int j = 0; j + step <= el; j += step) {
                int[] c = new int[4];
                int[] di = {0, step, step, 0}, dj = {0, 0, step, step};
                for (int k = 0; k < 4; k++) {
                    float n = (v[i + di[k]][j + dj[k]] - lo) / (hi - lo);
                    float e = el0 + (j + dj[k]) * dEl;
                    c[k] = alpha(mix(tint, 0xFFFFFFFF, n * .25f), (back ? 34 : 60) * (float) Math.pow(n, 1.5) * smooth(-1.15f, -.85f, e));
                }
                for (int k = 0; k < 4; k++) m.ae((i + di[k]) * dAz, el0 + (j + dj[k]) * dEl, c[k]);
            }
            // contour lines (marching squares), every 4th one is an index line like on a real map
            int levels = back ? 11 : 17;
            for (int level = 0; level < levels; level++) {
                float frac = level / (float) (levels - 1);
                float iso = lo + (hi - lo) * (.06f + .9f * frac);
                boolean index = level % 4 == 0;
                int lineColor = mix(mix(tint, 0xFF5AE0FF, frac * .55f), 0xFFFFFFFF, .12f + .45f * frac);
                float width = (index ? .0072f : .0036f) * (back ? .8f : 1f);
                float coreA = (index ? 255 : 205) * (back ? .7f : 1f);
                for (int i = 0; i < az; i++) for (int j = 0; j < el; j++) {
                    float v00 = v[i][j], v10 = v[i + 1][j], v11 = v[i + 1][j + 1], v01 = v[i][j + 1];
                    if (iso < Math.min(Math.min(v00, v10), Math.min(v11, v01)) || iso > Math.max(Math.max(v00, v10), Math.max(v11, v01))) continue;
                    float a0 = i * dAz, a1 = (i + 1) * dAz, e0 = el0 + j * dEl, e1 = el0 + (j + 1) * dEl;
                    float[] px = new float[4], py = new float[4];
                    int n = 0;
                    if ((v00 < iso) != (v10 < iso)) { px[n] = a0 + (iso - v00) / (v10 - v00) * (a1 - a0); py[n++] = e0; }
                    if ((v10 < iso) != (v11 < iso)) { px[n] = a1; py[n++] = e0 + (iso - v10) / (v11 - v10) * (e1 - e0); }
                    if ((v01 < iso) != (v11 < iso)) { px[n] = a0 + (iso - v01) / (v11 - v01) * (a1 - a0); py[n++] = e1; }
                    if ((v00 < iso) != (v01 < iso)) { px[n] = a0; py[n++] = e0 + (iso - v00) / (v01 - v00) * (e1 - e0); }
                    if (n == 2) line(m, px, py, 0, 1, width, lineColor, coreA, index);
                    else if (n == 4) {
                        boolean low = (v00 + v10 + v11 + v01) / 4 < iso;
                        line(m, px, py, 0, low ? 3 : 1, width, lineColor, coreA, index);
                        line(m, px, py, low ? 1 : 2, low ? 2 : 3, width, lineColor, coreA, index);
                    }
                }
            }
            // front map drifts one way, back map the other: the lines slide across each other
            emit(m, back ? -.7f : 1f, breathe(pass * 1.7f, .35f, .22f), null);
        }
    }

    private static void line(Mesh m, float[] px, float[] py, int p, int q, float width, int color, float coreA, boolean glow) {
        float fp = smooth(-1.15f, -.85f, py[p]), fq = smooth(-1.15f, -.85f, py[q]);
        if (glow) ribbon(m, px[p], py[p], px[q], py[q], width * 3.6f, alpha(color, 40 * fp), alpha(color, 40 * fq));
        ribbon(m, px[p], py[p], px[q], py[q], width, alpha(color, coreA * fp), alpha(color, coreA * fq));
    }

    // ------------------------------------------------------------------- Storm

    private void buildStorm(Mesh m, int primary, int secondary) {
        Random r = new Random(5150);
        int dark = mix(secondary, 0xFF000000, .1f), shade = mix(secondary, primary, .3f), light = mix(primary, 0xFFFFFFFF, .25f);
        // low haze along the horizon, a sea of cloud below it, and a heavy overcast canopy overhead
        for (int i = 0; i < 72; i++) {
            float a0 = i * TAU / 72, a1 = (i + 1) * TAU / 72;
            int bottom = alpha(mix(shade, light, .25f), 150), top = alpha(shade, 0);
            m.ae(a0, 0f, bottom); m.ae(a1, 0f, bottom); m.ae(a1, .2f, top); m.ae(a0, .2f, top);
        }
        for (int i = 0; i < 60; i++) {
            float az = r.nextFloat() * TAU, el = -.05f - r.nextFloat() * .7f, size = .32f + r.nextFloat() * .34f;
            puff(m, az, el, 0, 0, size * 1.8f, size * .55f, mix(dark, shade, .25f + r.nextFloat() * .5f), 215, r);
        }
        for (int i = 0; i < 36; i++) {
            float az = r.nextFloat() * TAU, el = .75f + r.nextFloat() * .7f, size = .4f + r.nextFloat() * .34f;
            puff(m, az, el, 0, 0, size * 1.5f, size, mix(dark, shade, r.nextFloat() * .6f), 205, r);
        }
        emit(m, .3f, null, null);
        storm(m, r, 20, .03f, .10f, .34f, .56f, .17f, .30f, dark, shade, light);
        emit(m, .45f, null, null);
        storm(m, r, 10, .06f, .20f, .70f, 1.1f, .40f, .66f, dark, shade, light);
        emit(m, .65f, null, null);
        for (int i = 0; i < 40; i++) {
            float az = r.nextFloat() * TAU, w = .04f + r.nextFloat() * .08f, top = .22f + r.nextFloat() * .15f;
            beam(m, az, top, top * .55f, w * .6f, w, -.012f, 0xFF7F8EAE, 0, 34);
            beam(m, az, top * .55f, .0f, w, w * 1.3f, -.012f, 0xFF7F8EAE, 34, 4);
        }
        emit(m, .4f, breathe(1.3f, .45f, .35f), sway(.03f, .15f, 0f));

        // lightning: each bolt flashes on its own schedule and lights up the CLOUDS around it (never the whole screen)
        double[] periods = {6.7, 9.3, 12.1, 15.7};
        for (int k = 0; k < periods.length; k++) {
            double period = periods[k], offset = k * 2.7;
            Curve flash = t -> {
                double tt = (t + offset) % period;
                if (tt < .07) return 1f;
                if (tt < .13) return .15f;
                if (tt < .22) return .95f;
                if (tt < .42) return (float) (.55 * (1 - (tt - .22) / .2));
                return 0f;
            };
            float az = r.nextFloat() * TAU;
            puff(m, az, .50f, 0, 0, .70f, .46f, 0xFFA9BDFF, 72, r);     // the cloud the bolt starts in glows blue-white
            puff(m, az, .46f, 0, 0, .30f, .22f, 0xFFE8EEFF, 120, r);
            puff(m, az, .30f, 0, 0, .55f, .30f, 0xFF7F96E6, 38, r);      // faint light spilling down the cloud base
            bolt(m, r, az);
            emit(m, .55f, flash, null);
        }
        // distant sheet lightning: clouds flickering from inside, dim and short, away from the bolts
        double[] sheet = {4.3, 7.9, 10.4};
        for (int k = 0; k < sheet.length; k++) {
            double period = sheet[k], offset = k * 1.9 + .6;
            puff(m, r.nextFloat() * TAU, .12f + r.nextFloat() * .3f, 0, 0, .55f + r.nextFloat() * .3f, .24f, 0xFF9FB4F0, 58, r);
            emit(m, .5f, t -> {
                double tt = (t + offset) % period;
                if (tt < .05) return .9f;
                if (tt < .11) return .2f;
                if (tt < .2) return .7f;
                if (tt < .45) return (float) (.5 * (1 - (tt - .2) / .25));
                return 0f;
            }, null);
        }
    }
    /** A row of cumulonimbus towers: flat dark bases, billowing shaded sides, bright sunlit crowns and anvil tops. */
    private static void storm(Mesh m, Random r, int count, float elMin, float elMax, float wMin, float wMax,
                              float hMin, float hMax, int dark, int shade, int light) {
        record Blob(float x, float y, float rx, float ry, int color, float a) {}
        for (int c = 0; c < count; c++) {
            float az = (c + r.nextFloat() * .7f) * TAU / count, el = elMin + r.nextFloat() * (elMax - elMin);
            float width = wMin + r.nextFloat() * (wMax - wMin), height = hMin + r.nextFloat() * (hMax - hMin);
            List<Blob> blobs = new ArrayList<>();
            for (int i = 0; i < 9; i++) {  // the flat underside
                float x = (r.nextFloat() * 2 - 1) * width * .5f;
                blobs.add(new Blob(x, r.nextFloat() * height * .06f, width * .16f, height * .13f, mix(dark, shade, r.nextFloat() * .3f), 225));
            }
            for (int i = 0; i < 28; i++) {
                float t = (float) Math.pow(r.nextFloat(), 1.3);
                float half = width * .5f * (t < .72f ? 1f - .5f * t : .64f + (t - .72f) * 2.4f);
                float x = (r.nextFloat() * 2 - 1) * half * (t < .72f ? 1f : .9f);
                float radius = (.05f + .07f * r.nextFloat()) * (width + height) * .5f * (1.15f - .35f * t) * 1.5f;
                float lit = smooth(.1f, 1f, t) * (.45f + .3f * r.nextFloat());
                int color = mix(mix(dark, shade, .55f + .3f * r.nextFloat()), light, lit);
                blobs.add(new Blob(x, t * height, radius, radius * .82f, color, 215));
            }
            for (int i = 0; i < 6; i++) {  // glowing crown
                float x = (r.nextFloat() * 2 - 1) * width * .3f;
                blobs.add(new Blob(x, height * (.82f + r.nextFloat() * .22f), width * .12f, height * .09f, mix(light, shade, .25f), 120));
            }
            blobs.sort((p, q) -> Float.compare(p.y(), q.y()));
            for (Blob b : blobs) puff(m, az, el, b.x(), b.y(), b.rx(), b.ry(), b.color(), b.a(), r);
        }
    }

    private static void bolt(Mesh m, Random r, float az) {
        int core = 0xFFF4F7FF, halo = 0xFF9CB4FF;
        float a = az, e = .52f;
        List<float[]> trunk = new ArrayList<>();
        trunk.add(new float[] {a, e});
        for (int i = 0; i < 16; i++) {
            a += (r.nextFloat() - .5f) * .05f;
            e -= .03f + r.nextFloat() * .008f;
            trunk.add(new float[] {a, Math.max(e, .02f)});
        }
        strokeBolt(m, trunk, .0075f, core, halo);
        for (int b = 0; b < 3; b++) {
            float[] from = trunk.get(3 + r.nextInt(8));
            List<float[]> branch = new ArrayList<>();
            float ba = from[0], be = from[1], dir = r.nextBoolean() ? 1 : -1;
            branch.add(new float[] {ba, be});
            for (int i = 0; i < 6; i++) {
                ba += dir * (.015f + r.nextFloat() * .02f);
                be -= .018f + r.nextFloat() * .01f;
                branch.add(new float[] {ba, Math.max(be, .02f)});
            }
            strokeBolt(m, branch, .0042f, core, halo);
        }
    }

    private static void strokeBolt(Mesh m, List<float[]> pts, float width, int core, int halo) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            float[] p = pts.get(i), q = pts.get(i + 1);
            ribbon(m, p[0], p[1], q[0], q[1], width * 5, alpha(halo, 55), alpha(halo, 55));
            ribbon(m, p[0], p[1], q[0], q[1], width, alpha(core, 255), alpha(core, 235));
        }
    }

    // ------------------------------------------------------------------ Nebula

    private void buildNebula(Mesh m, int primary) {
        Random r = new Random(777);
        int cyan = 0xFF36C6FF, pink = 0xFFFF6BA8, indigo = 0xFF4A4DFF;
        int[] gas = {primary, cyan, pink, indigo, primary};
        // broad, faint clouds of coloured gas filling the whole sphere
        wisps(m, r, 46, gas, .3f, .85f, 14, 36);
        emit(m, .22f, breathe(0f, .25f, .22f), null);
        // brighter filaments inside them
        wisps(m, r, 150, gas, .08f, .22f, 26, 62);
        emit(m, .3f, breathe(1.7f, .4f, .3f), null);
        // dark dust lanes that carve shapes out of the glow
        for (int i = 0; i < 26; i++) {
            float az = r.nextFloat() * TAU, el = (float) Math.asin(-.2 + r.nextFloat() * 1.15);
            float size = .18f + r.nextFloat() * .32f;
            puff(m, az, el, 0, 0, size * 1.7f, size * .6f, 0xFF04020C, 95, r);
        }
        emit(m, .26f, null, null);
        scatterStars(m, r, primary, 3, 560, .1f);
        shootingStars(m, r, primary);
    }

    // --------------------------------------------------------------- Synthwave

    private void buildSynthwave(Mesh m, int primary) {
        Random r = new Random(1984);
        int neonPink = primary, neonCyan = 0xFF2BE6FF, violet = 0xFF7A2CFF;
        // glowing horizon bands: hot pink haze, a violet layer above it, and a thin cyan neon line
        for (int i = 0; i < 90; i++) {
            float a0 = i * TAU / 90, a1 = (i + 1) * TAU / 90;
            m.ae(a0, 0f, alpha(neonPink, 240)); m.ae(a1, 0f, alpha(neonPink, 240));
            m.ae(a1, .42f, alpha(neonPink, 0)); m.ae(a0, .42f, alpha(neonPink, 0));
            m.ae(a0, .1f, alpha(violet, 135)); m.ae(a1, .1f, alpha(violet, 135));
            m.ae(a1, .95f, alpha(violet, 0)); m.ae(a0, .95f, alpha(violet, 0));
        }
        emit(m, 1f, breathe(0f, .6f, .2f), null);
        scatterStars(m, r, 0xFFFFE6FF, 2, 360, .07f);
        // dark mountain range along the horizon with a neon outline
        int segments = 360;
        float[] top = new float[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float a = i * TAU / segments;
            top[i] = .02f + .055f * (float) Math.abs(Math.sin(a * 5 + 1)) + .03f * (float) Math.abs(Math.sin(a * 13 + 2)) + .012f * (float) Math.sin(a * 41);
        }
        top[segments] = top[0];
        for (int i = 0; i < segments; i++) {
            float a0 = i * TAU / segments, a1 = (i + 1) * TAU / segments;
            int ink = 0xFF0C0220;
            m.ae(a0, -.06f, ink); m.ae(a1, -.06f, ink); m.ae(a1, top[i + 1], ink); m.ae(a0, top[i], ink);
            ribbon(m, a0, top[i], a1, top[i + 1], .0045f, alpha(neonCyan, 235), alpha(neonCyan, 235));
        }
        emit(m, .12f, null, null);
        // a neon city skyline in front of the mountains, with lit windows
        int blocks = 300;
        float[] roof = new float[blocks], start = new float[blocks], span = new float[blocks];
        float cursor = 0;
        for (int i = 0; i < blocks; i++) {
            float w = TAU / blocks * (.6f + r.nextFloat() * .9f);
            float a0 = cursor, a1 = a0 + w * .86f;
            cursor += w;
            float cluster = .5f + .5f * (float) Math.sin(a0 * 6 + 1.2) * (float) Math.sin(a0 * 3.1);
            float h = .018f + r.nextFloat() * .035f + cluster * .06f * r.nextFloat();
            roof[i] = h; start[i] = a0; span[i] = a1 - a0;
            int ink = 0xFF120432;
            m.ae(a0, -.06f, ink); m.ae(a1, -.06f, ink); m.ae(a1, h, mix(ink, 0xFF5A1A6E, .5f)); m.ae(a0, h, mix(ink, 0xFF5A1A6E, .5f));
            ribbon(m, a0, h, a1, h, .0026f, alpha(i % 3 == 0 ? neonCyan : neonPink, 235), alpha(i % 3 == 0 ? neonCyan : neonPink, 235));
            if (cursor > TAU) break;
        }
        emit(m, .12f, null, null);
        for (int group = 0; group < 3; group++) {
            for (int i = 0; i < blocks; i++) {
                if (span[i] <= 0) continue;
                int windows = 2 + (int) (roof[i] * 160);
                for (int wd = 0; wd < windows; wd++) {
                    if (r.nextFloat() > .45f) continue;
                    float az = start[i] + span[i] * (.12f + r.nextFloat() * .76f), el = .003f + r.nextFloat() * Math.max(.001f, roof[i] - .007f);
                    glint(m, az, el, .0021f, new int[] {0xFFFFE27A, 0xFF7AF4FF, 0xFFFF8AD8}[r.nextInt(3)], 255, .35f);
                }
            }
            emit(m, .12f, breathe(group * 2f, .6f + group * .5f, .55f), null);
        }
        // the neon grid below the horizon: rings that bunch up toward it, lines running away from it
        float[] rings = {-.035f, -.06f, -.095f, -.14f, -.2f, -.28f, -.4f, -.58f, -.85f, -1.2f};
        for (int k = 0; k < rings.length; k++) {
            float width = .0036f * (1 + k * .55f);
            int color = mix(neonPink, neonCyan, k / (float) rings.length);
            int steps = 150;
            for (int i = 0; i < steps; i++) {
                ribbon(m, i * TAU / steps, rings[k], (i + 1) * TAU / steps, rings[k], width,
                        alpha(color, 230 - k * 8), alpha(color, 230 - k * 8));
            }
        }
        for (int line = 0; line < 72; line++) {
            float az = line * TAU / 72;
            float prev = -.02f;
            for (int s = 1; s <= 14; s++) {
                float next = -.02f - (float) Math.pow(s / 14f, 1.7) * 1.3f;
                float fade = 1f - (s / 14f) * .55f;
                ribbon(m, az, prev, az, next, .004f * (1 + s * .25f), alpha(neonPink, 235 * fade), alpha(neonPink, 235 * (1f - (s + 1) / 14f * .55f)));
                prev = next;
            }
        }
        emit(m, .6f, breathe(1f, .5f, .18f), null);
        shootingStars(m, r, 0xFFFFC2F0);
    }

    // ------------------------------------------------------------------ Galaxy

    private void buildGalaxy(Mesh m, int primary) {
        Random r = new Random(2112);
        wisps(m, r, 14, new int[] {0xFF3A4CCF, 0xFF6A3FBF}, .4f, .8f, 8, 20);
        emit(m, .4f, breathe(0f, .3f, .2f), null);
        scatterStars(m, r, primary, 3, 520, .08f);
        galaxyDisc(m, r, 1.05f, .98f, .66f, primary, .5f, 2300);
        emit(m, .1f, null, null);
        galaxyDisc(m, r, 3.9f, .55f, .28f, mix(primary, 0xFF9DB8FF, .6f), -.7f, 900);
        emit(m, .13f, null, null);
        shootingStars(m, r, primary);
    }

    /** A spiral galaxy: a glowing core, two sweeping arms of tiny stars, dust lanes, all tilted. */
    private void galaxyDisc(Mesh m, Random r, float az, float el, float radius, int core, float tilt, int stars) {
        int arm = mix(core, 0xFF7EA2FF, .7f);
        float cosT = (float) Math.cos(tilt), sinT = (float) Math.sin(tilt);
        puff(m, az, el, 0, 0, radius * 1.0f, radius * .6f, arm, 20, r);
        for (int i = 0; i < 9; i++) {
            float s = radius * (.1f + r.nextFloat() * .16f);
            puff(m, az, el, (float) r.nextGaussian() * radius * .05f, (float) r.nextGaussian() * radius * .035f, s * 1.3f, s, mix(core, 0xFFFFB070, .35f), 22 + r.nextInt(16), r);
        }
        for (int i = 0; i < stars; i++) {
            float t = r.nextFloat() * 3.2f * (float) Math.PI;
            float rad = radius * (.07f + t / (3.2f * (float) Math.PI) * .93f);
            float theta = t + (i % 2) * (float) Math.PI + (float) r.nextGaussian() * .22f;
            float px = rad * (float) Math.cos(theta), py = rad * (float) Math.sin(theta) * .55f;
            float dx = px * cosT - py * sinT, dy = px * sinT + py * cosT;
            int c = mix(core, arm, Math.clamp(rad / radius * 1.4f, 0, 1));
            glint(m, az + dx / (float) Math.cos(el), el + dy, .0017f + r.nextFloat() * .0024f, c, 70 + r.nextInt(150), .25f);
        }
        for (int i = 0; i < 34; i++) {
            float t = r.nextFloat() * 3f * (float) Math.PI;
            float rad = radius * (.34f + t / (3f * (float) Math.PI) * .6f);
            float theta = t + (i % 2) * (float) Math.PI + .2f;
            float px = rad * (float) Math.cos(theta), py = rad * (float) Math.sin(theta) * .55f;
            puff(m, az, el, px * cosT - py * sinT, px * sinT + py * cosT, radius * .1f, radius * .05f, 0xFF05040C, 70, r);
        }
    }

    // -------------------------------------------------------------------- Dusk

    private void buildDusk(Mesh m, int primary) {
        Random r = new Random(1600);
        int peach = 0xFFFFB48A, rose = 0xFFFF7FA8, lilac = 0xFFB69CFF;
        // a wide warm glow around the whole horizon
        for (int i = 0; i < 90; i++) {
            float a0 = i * TAU / 90, a1 = (i + 1) * TAU / 90;
            m.ae(a0, 0f, alpha(primary, 150)); m.ae(a1, 0f, alpha(primary, 150));
            m.ae(a1, .5f, alpha(rose, 0)); m.ae(a0, .5f, alpha(rose, 0));
        }
        emit(m, 1f, breathe(0f, .3f, .15f), null);
        float sunAz = 3.9f;
        puff(m, sunAz, .06f, 0, 0, 1.3f, .55f, 0xFFFFA070, 100, r);
        puff(m, sunAz, .08f, 0, 0, .5f, .25f, 0xFFFFD0A0, 110, r);
        emit(m, 0f, breathe(1f, .3f, .15f), null);
        for (int i = 0; i < 14; i++) {
            float a = sunAz + (i - 6.5f) * .15f + (r.nextFloat() - .5f) * .05f, w = .02f + r.nextFloat() * .03f;
            beam(m, a, 1.05f, .02f, w * .6f, w * 3f, (a - sunAz) * .28f, 0xFFFFB486, 0, 46);
            beam(m, a, .02f, -.1f, w * 3f, w * 3.4f, (a - sunAz) * .08f, 0xFFFFB486, 46, 0);
        }
        emit(m, 0f, breathe(2f, .35f, .5f), null);
        scatterStars(m, r, 0xFFFFFFFF, 2, 200, .05f);
        // long streaks of high cloud, lit from below by the vanished sun
        float[] rows = {.06f, .15f, .27f, .41f, .58f, .8f};
        for (int row = 0; row < rows.length; row++) {
            int count = 18 - row * 2;
            for (int i = 0; i < count; i++) {
                float az = (i + r.nextFloat() * .8f) * TAU / count, el = rows[row] + (r.nextFloat() - .5f) * .05f;
                float length = (.3f + r.nextFloat() * .4f) * (1f + row * .12f), thick = .018f + r.nextFloat() * .03f + row * .004f;
                float a = 175 - row * 18;
                puff(m, az, el, 0, -thick * .35f, length, thick * 1.6f, mix(peach, rose, row / 5f), a * .85f, r);
                puff(m, az, el, 0, thick * .5f, length * .85f, thick, mix(lilac, rose, .4f + row * .08f), a * .7f, r);
            }
            emit(m, .25f + row * .09f, breathe(row * 1.3f, .35f, .18f), null);
        }
    }

    // ================================================================== particle fields
    // Falling snow, rising embers, bubbles and rain are built as a tall cylinder of specks around the viewer, twice
    // (one period apart), and the whole layer slides up or down by one period over time: it wraps with no seam.

    private static final float FIELD_BOTTOM = -40f, FIELD_PERIOD = 150f;

    /** A soft round speck (octagon, bright centre fading out) hanging at (az, y) on a cylinder of radius rho, facing the viewer. */
    private static void speck(Mesh m, float az, float y, float rho, float w, float h, int color, float a, float rim) {
        float cx = rho * (float) Math.cos(az), cz = rho * (float) Math.sin(az);
        float ex = -(float) Math.sin(az), ez = (float) Math.cos(az);
        int centre = alpha(color, a), edge = alpha(color, a * rim);
        float[] px = new float[8], py = new float[8];
        for (int k = 0; k < 8; k++) { px[k] = (float) Math.cos(k * Math.PI / 4) * w; py[k] = (float) Math.sin(k * Math.PI / 4) * h; }
        for (int q = 0; q < 4; q++) {
            m.vertex(cx, y, cz, centre);
            for (int s = 0; s < 3; s++) {
                int k = (2 * q + s) % 8;
                m.vertex(cx + ex * px[k], y + py[k], cz + ez * px[k], edge);
            }
        }
    }

    /** A soft round glow on the dome (8 sides): fireflies, lamps. */
    private static void orb(Mesh m, float az, float el, float radius, int color, float a, float rim) {
        int centre = alpha(color, a), edge = alpha(color, a * rim);
        for (int q = 0; q < 4; q++) {
            m.tp(az, el, 0, 0, centre);
            for (int s = 0; s < 3; s++) {
                double ang = ((2 * q + s) % 8) * Math.PI / 4;
                m.tp(az, el, (float) Math.cos(ang) * radius, (float) Math.sin(ang) * radius, edge);
            }
        }
    }
    /** A vertical streak (bright head at the bottom fading up) on the same cylinder: digital rain. */
    private static void streak(Mesh m, float az, float y, float rho, float w, float length, int head, int tail) {
        float cx = rho * (float) Math.cos(az), cz = rho * (float) Math.sin(az);
        float ex = -(float) Math.sin(az) * w, ez = (float) Math.cos(az) * w;
        m.vertex(cx - ex, y, cz - ez, head); m.vertex(cx + ex, y, cz + ez, head);
        m.vertex(cx + ex, y + length, cz + ez, tail); m.vertex(cx - ex, y + length, cz - ez, tail);
    }

    private interface SpeckShape { void put(Mesh m, Random r, float az, float y, float rho); }

    /** {@code count} specks spread over one period (and repeated one period higher), then one layer that scrolls. */
    private void fall(Mesh m, Random r, int count, float rho, float speed, float spin, Curve pulse, Curve drift, SpeckShape shape) {
        for (int i = 0; i < count; i++) {
            float az = r.nextFloat() * TAU, y = FIELD_BOTTOM + r.nextFloat() * FIELD_PERIOD;
            float jr = rho * (.92f + r.nextFloat() * .16f);
            shape.put(m, r, az, y, jr);
            shape.put(m, r, az, y + FIELD_PERIOD, jr);
        }
        Curve rise = t -> (float) (((t * Math.abs(speed)) % FIELD_PERIOD) * Math.signum(speed));
        emitRising(m, spin, pulse, drift, rise);
    }

    private static Curve sway(float amount, float speed, float phase) {
        return t -> amount * (float) Math.sin(t * speed + phase);
    }

    private static float between(Random r, float lo, float hi) { return lo + r.nextFloat() * (hi - lo); }

    /** A band of colour hugging the horizon (above, and optionally a reflection below). */
    private static void horizonGlow(Mesh m, int color, float aBottom, float height, float below) {
        for (int i = 0; i < 90; i++) {
            float a0 = i * TAU / 90, a1 = (i + 1) * TAU / 90;
            m.ae(a0, 0f, alpha(color, aBottom)); m.ae(a1, 0f, alpha(color, aBottom));
            m.ae(a1, height, alpha(color, 0)); m.ae(a0, height, alpha(color, 0));
            if (below > 0) {
                m.ae(a0, 0f, alpha(color, aBottom * .6f)); m.ae(a1, 0f, alpha(color, aBottom * .6f));
                m.ae(a1, -below, alpha(color, 0)); m.ae(a0, -below, alpha(color, 0));
            }
        }
    }

    // ----------------------------------------------------------------- Winter Snowfall

    private void buildSnow(Mesh m, int primary) {
        Random r = new Random(1221);
        horizonGlow(m, 0xFFDCEBFF, 120, .42f, 0f);
        emit(m, 1f, breathe(0f, .25f, .12f), null);
        wisps(m, r, 26, new int[] {0xFF9DB4D8, 0xFFC4D4EE, 0xFF7D96C0}, .35f, .8f, 14, 34);
        emit(m, .18f, breathe(1f, .2f, .15f), null);
        scatterStars(m, r, 0xFFE6F0FF, 2, 240, .05f);
        int[] flake = {primary, 0xFFD6E6FF, 0xFFFFFFFF};
        fall(m, r, 560, 78f, -3.2f, .02f, null, sway(.035f, .35f, 0f),
                (mm, rr, az, y, rho) -> speck(mm, az, y, rho, .26f + rr.nextFloat() * .16f, .26f + rr.nextFloat() * .16f, flake[rr.nextInt(3)], between(rr, 150, 235), .25f));
        fall(m, r, 280, 52f, -5.4f, .025f, null, sway(.05f, .5f, 1.7f),
                (mm, rr, az, y, rho) -> speck(mm, az, y, rho, .38f + rr.nextFloat() * .22f, .38f + rr.nextFloat() * .22f, flake[rr.nextInt(3)], between(rr, 170, 245), .3f));
        fall(m, r, 120, 30f, -8.5f, .03f, null, sway(.07f, .7f, 3.1f),
                (mm, rr, az, y, rho) -> speck(mm, az, y, rho, .5f + rr.nextFloat() * .3f, .5f + rr.nextFloat() * .3f, 0xFFFFFFFF, between(rr, 190, 255), .35f));
    }

    // ---------------------------------------------------------------- Cherry Blossom

    private void buildSakura(Mesh m, int primary) {
        Random r = new Random(404);
        horizonGlow(m, 0xFFFFD2C2, 170, .5f, 0f);
        emit(m, 1f, breathe(0f, .3f, .15f), null);
        float[] rows = {.07f, .17f, .3f, .46f, .66f};
        for (int row = 0; row < rows.length; row++) {
            int count = 15 - row * 2;
            for (int i = 0; i < count; i++) {
                float az = (i + r.nextFloat() * .8f) * TAU / count, el = rows[row] + (r.nextFloat() - .5f) * .05f;
                float length = (.28f + r.nextFloat() * .36f) * (1f + row * .12f), thick = .03f + r.nextFloat() * .035f;
                puff(m, az, el, 0, -thick * .3f, length, thick * 1.7f, mix(0xFFFFD0DC, 0xFFFF9EC0, row / 4f), 150 - row * 14, r);
                puff(m, az, el, length * .1f, thick * .5f, length * .8f, thick, mix(0xFFFFEFF4, 0xFFFFB8D2, row / 6f), 120 - row * 12, r);
            }
            emit(m, .2f + row * .08f, breathe(row * 1.1f, .3f, .15f), null);
        }
        scatterStars(m, r, 0xFFFFFFFF, 1, 90, .05f);
        int[] petal = {0xFFFFB3CC, 0xFFFFD0E0, 0xFFFF93B8, 0xFFFFF0F5};
        for (int layer = 0; layer < 3; layer++) {
            float rho = 70f - layer * 20f, size = .5f + layer * .22f;
            fall(m, r, 210 - layer * 40, rho, -(2.2f + layer * 1.6f), .04f, null, sway(.06f + layer * .03f, .45f + layer * .15f, layer * 2.1f),
                    (mm, rr, az, y, rh) -> speck(mm, az, y, rh, size * (.7f + rr.nextFloat() * .7f), size * (.45f + rr.nextFloat() * .4f), petal[rr.nextInt(4)], between(rr, 170, 250), .4f));
        }
    }

    // ---------------------------------------------------------------- Deep Ocean

    /** A soft light shaft: bright in the middle, fading to nothing at both sides and with depth (no hard edges). */
    private static void beam(Mesh m, float az, float topEl, float botEl, float wTop, float wBot, float slant, int color, float aTop, float aBot) {
        float kTop = 1f / Math.max(.12f, (float) Math.cos(topEl)), kBot = 1f / Math.max(.12f, (float) Math.cos(botEl));
        int top = alpha(color, aTop), bot = alpha(color, aBot), edgeTop = alpha(color, 0), edgeBot = alpha(color, 0);
        float bAz = az + slant;
        m.ae(az - wTop * kTop, topEl, edgeTop); m.ae(az, topEl, top); m.ae(bAz, botEl, bot); m.ae(bAz - wBot * kBot, botEl, edgeBot);
        m.ae(az, topEl, top); m.ae(az + wTop * kTop, topEl, edgeTop); m.ae(bAz + wBot * kBot, botEl, edgeBot); m.ae(bAz, botEl, bot);
    }

    private void buildOcean(Mesh m, int primary) {
        Random r = new Random(808);
        int cyan = 0xFF5FE8E0, aqua = 0xFF2AA9D0, pale = 0xFFCFFFF6;
        // the bright surface far above, rippling
        for (int group = 0; group < 3; group++) {
            for (int i = 0; i < 34; i++) {
                float az = r.nextFloat() * TAU, el = 1.0f + r.nextFloat() * .55f, size = .09f + r.nextFloat() * .17f;
                puff(m, az, el, 0, 0, size * (1.2f + r.nextFloat() * .8f), size, mix(cyan, pale, r.nextFloat()), 26 + r.nextFloat() * 34, r);
            }
            emit(m, .08f + group * .06f, breathe(group * 2.3f, .5f + group * .3f, .6f), sway(.06f, .2f + group * .08f, group * 1.6f));
        }
        // soft shafts of sunlight slanting down from the surface, three depths that sway out of step
        for (int depth = 0; depth < 3; depth++) {
            for (int i = 0; i < 9; i++) {
                float az = r.nextFloat() * TAU, top = 1.5f, bottom = .1f + depth * .12f;
                float wTop = .03f + depth * .02f + r.nextFloat() * .03f, wBot = wTop * (3f + r.nextFloat() * 2f);
                beam(m, az, top, bottom, wTop, wBot, .05f + r.nextFloat() * .16f, mix(pale, aqua, depth * .3f), 52 + r.nextFloat() * 30 - depth * 8, 0);
            }
            emit(m, .05f + depth * .04f, breathe(depth * 1.7f, .35f + depth * .15f, .55f), sway(.05f, .17f + depth * .06f, depth * 2.2f));
        }
        // glowing water near the horizon, so the deep fades into blue-green dark instead of a line
        horizonGlow(m, 0xFF1FB5B8, 70, .55f, .3f);
        emit(m, 1f, breathe(.4f, .3f, .2f), null);
        // bioluminescence far below: drifting soft orbs
        int[] glow = {0xFF3DF5E0, 0xFF6A8CFF, 0xFFB45CFF, 0xFF3DFF9A};
        for (int group = 0; group < 4; group++) {
            for (int i = 0; i < 16; i++) {
                float az = r.nextFloat() * TAU, el = .02f + r.nextFloat() * .65f, size = .008f + r.nextFloat() * .012f;
                int c = glow[(group + i) % 4];
                orb(m, az, el, size, c, 255, .5f);
                orb(m, az, el, size * 5f, c, 70, 0f);
            }
            final float ph = group * 1.4f;
            emit(m, .1f + group * .03f, breathe(ph, .5f + group * .22f, .85f), sway(.04f, .13f + group * .04f, ph));
        }
        // plankton glints and rising bubbles
        scatterStars(m, r, 0xFFBFFFF4, 2, 180, .05f);
        fall(m, r, 120, 74f, 3.4f, .03f, null, sway(.05f, .3f, 0f),
                (mm, rr, az, y, rho) -> speck(mm, az, y, rho, .2f + rr.nextFloat() * .2f, .2f + rr.nextFloat() * .2f, 0xFFD8FFFF, between(rr, 80, 160), .28f));
        fall(m, r, 60, 40f, 5.6f, .04f, null, sway(.07f, .45f, 2f),
                (mm, rr, az, y, rho) -> speck(mm, az, y, rho, .4f + rr.nextFloat() * .35f, .4f + rr.nextFloat() * .35f, 0xFFE6FFFF, between(rr, 70, 150), .3f));
        fall(m, r, 260, 66f, -1.4f, .02f, null, sway(.04f, .25f, 1f),      // marine snow drifting down
                (mm, rr, az, y, rho) -> speck(mm, az, y, rho, .1f + rr.nextFloat() * .08f, .1f + rr.nextFloat() * .08f, 0xFFA9DCE6, between(rr, 60, 130), .25f));
    }
    // ------------------------------------------------------------------- Inferno

    private void buildInferno(Mesh m, int primary) {
        Random r = new Random(666);
        horizonGlow(m, 0xFFFF5A12, 235, .55f, .3f);
        emit(m, 1f, breathe(0f, .9f, .28f), null);
        horizonGlow(m, 0xFFFFB347, 130, .18f, 0f);
        emit(m, 1f, breathe(2f, 1.3f, .4f), null);
        // heavy smoke lit orange from underneath
        for (int i = 0; i < 26; i++) {
            float az = r.nextFloat() * TAU, el = .06f + r.nextFloat() * .55f, size = .2f + r.nextFloat() * .3f;
            puff(m, az, el, 0, -size * .35f, size * 1.7f, size * .7f, 0xFFFF6A1A, 34 + r.nextFloat() * 26, r);
            puff(m, az, el, 0, size * .15f, size * 1.5f, size * .75f, mix(0xFF160807, 0xFF3A130C, r.nextFloat()), 200, r);
        }
        emit(m, .22f, breathe(1f, .5f, .22f), null);
        for (int i = 0; i < 18; i++) {
            float az = r.nextFloat() * TAU, el = .7f + r.nextFloat() * .7f, size = .3f + r.nextFloat() * .3f;
            puff(m, az, el, 0, 0, size * 1.6f, size, mix(0xFF1A0808, 0xFF3A140D, r.nextFloat()), 170, r);
        }
        emit(m, .15f, null, null);
        int[] ember = {0xFFFFD27A, 0xFFFF8A2A, 0xFFFF5A1A};
        for (int layer = 0; layer < 3; layer++) {
            float size = .24f + layer * .12f;
            fall(m, r, 300 - layer * 70, 74f - layer * 22f, 6f + layer * 5f, .03f, breathe(layer * 1.4f, 2.6f + layer, .25f), sway(.06f, .7f, layer * 1.3f),
                    (mm, rr, az, y, rho) -> speck(mm, az, y, rho, size * (.7f + rr.nextFloat() * .9f), size * (1f + rr.nextFloat() * 1.4f), ember[rr.nextInt(3)], between(rr, 170, 255), .15f));
        }
    }

    // --------------------------------------------------------------- Digital Rain

    private void buildMatrix(Mesh m, int primary) {
        Random r = new Random(1999);
        horizonGlow(m, primary, 70, .35f, .2f);
        emit(m, 1f, breathe(0f, .5f, .3f), null);
        for (int layer = 0; layer < 3; layer++) {
            float rho = 76f - layer * 22f, width = .08f + layer * .05f;
            int count = 340 - layer * 80;
            final float spd = -(7f + layer * 6f);
            for (int i = 0; i < count; i++) {
                float az = r.nextFloat() * TAU, y = FIELD_BOTTOM + r.nextFloat() * FIELD_PERIOD, len = 8f + r.nextFloat() * 22f * (1f + layer * .3f);
                int head = mix(primary, 0xFFFFFFFF, .7f), tail = alpha(primary, 0);
                int bright = alpha(head, between(r, 150, 255)), body = alpha(primary, between(r, 90, 170));
                for (int copy = 0; copy < 2; copy++) {
                    float yy = y + copy * FIELD_PERIOD, rr = rho * (.94f + r.nextFloat() * .12f);
                    // bright leading cell, then a long fading tail above it
                    streak(m, az, yy, rr, width, 1.6f + layer * .7f, bright, bright);
                    streak(m, az, yy + 1.6f + layer * .7f, rr, width * .75f, len, body, tail);
                }
            }
            emitRising(m, .02f, null, null, t -> (float) (((t * Math.abs(spd)) % FIELD_PERIOD) * Math.signum(spd)));
        }
        scatterStars(m, r, primary, 1, 120, .03f);
    }

    // ---------------------------------------------------------------- Golden Hour

    private void buildGolden(Mesh m, int primary) {
        Random r = new Random(1203);
        float sunAz = 1.1f;
        horizonGlow(m, 0xFFFFE0A0, 200, .5f, .15f);
        emit(m, 1f, breathe(0f, .25f, .1f), null);
        puff(m, sunAz, .1f, 0, 0, 1.1f, .6f, 0xFFFFF0C0, 110, r);    // the hidden sun's glow (no disc)
        puff(m, sunAz, .12f, 0, 0, .45f, .26f, 0xFFFFFFE0, 120, r);
        emit(m, 0f, breathe(1f, .3f, .12f), null);
        // soft rays fanning up from behind the horizon
        for (int i = 0; i < 16; i++) {
            float a = sunAz + (i - 7.5f) * .12f + (r.nextFloat() - .5f) * .05f, w = .02f + r.nextFloat() * .03f;
            m.ae(a - w * .4f, .08f, alpha(0xFFFFE9B0, 90)); m.ae(a + w * .4f, .08f, alpha(0xFFFFE9B0, 90));
            m.ae(a + w * 3.2f + (a - sunAz) * .35f, 1.0f, alpha(0xFFFFD27A, 0)); m.ae(a - w * 3.2f + (a - sunAz) * .35f, 1.0f, alpha(0xFFFFD27A, 0));
        }
        emit(m, 0f, breathe(2f, .5f, .55f), null);
        float[] rows = {.07f, .17f, .3f, .46f, .66f, .9f};
        for (int row = 0; row < rows.length; row++) {
            int count = 17 - row * 2;
            for (int i = 0; i < count; i++) {
                float az = (i + r.nextFloat() * .8f) * TAU / count, el = rows[row] + (r.nextFloat() - .5f) * .05f;
                float length = (.26f + r.nextFloat() * .4f) * (1f + row * .12f), thick = .03f + r.nextFloat() * .04f;
                puff(m, az, el, 0, -thick * .3f, length, thick * 1.6f, mix(0xFFFFB866, 0xFFFFE8B8, row / 5f), 160 - row * 12, r);
                puff(m, az, el, length * .08f, thick * .55f, length * .85f, thick, mix(0xFFFFF2D0, 0xFFE8B8C8, row / 6f), 130 - row * 10, r);
            }
            emit(m, .16f + row * .07f, breathe(row * 1.2f, .3f, .14f), null);
        }
    }

    // -------------------------------------------------------------------- Vortex

    private void buildVortex(Mesh m, int primary, int secondary) {
        Random r = new Random(314);
        wisps(m, r, 22, new int[] {primary, secondary, 0xFF6A3FFF}, .3f, .7f, 10, 26);
        emit(m, .25f, breathe(0f, .3f, .25f), null);
        for (int pass = 0; pass < 2; pass++) {
            boolean inner = pass == 1;
            int arms = inner ? 5 : 8;
            for (int arm = 0; arm < arms; arm++) {
                float base = arm * TAU / arms + (inner ? .4f : 0f);
                float pa = 0, pe = 0, pw = 0; int pc = 0;
                for (int i = 0; i <= 80; i++) {
                    float u = i / 80f;
                    float el = (inner ? 1.5f : 1.45f) - u * (inner ? 1.0f : 1.25f);
                    float az = base + (inner ? -1 : 1) * (u * 5.4f + (float) Math.sin(u * 9 + arm) * .08f);
                    float width = (inner ? .006f : .008f) + u * (inner ? .02f : .036f);
                    int color = mix(primary, secondary, u);
                    float fade = (float) Math.sin(Math.PI * Math.min(1f, u * 1.15f)) * (1f - u * .35f);
                    int col = alpha(color, (inner ? 230 : 190) * Math.max(0, fade));
                    if (i > 0) ribbon(m, pa, pe, az, el, (pw + width) / 2f, pc, col);
                    pa = az; pe = el; pw = width; pc = col;
                }
            }
            emit(m, inner ? -1.6f : 1.1f, breathe(pass * 1.5f, .5f, .25f), null);
        }
        puff(m, 0f, 1.5f, 0, 0, .55f, .55f, lighten(primary, .5f), 90, r);
        puff(m, 0f, 1.5f, 0, 0, .25f, .25f, 0xFFFFFFFF, 90, r);
        emit(m, 0f, breathe(0f, 1.1f, .35f), null);
        scatterStars(m, r, 0xFFFFFFFF, 2, 300, .07f);
        shootingStars(m, r, primary);
    }

    // ----------------------------------------------------------------- Firefly Night

    private void buildFireflies(Mesh m, int primary) {
        Random r = new Random(555);
        horizonGlow(m, 0xFF2FB58A, 95, .4f, 0f);
        emit(m, 1f, breathe(0f, .3f, .18f), null);
        scatterStars(m, r, 0xFFFFFFFF, 2, 260, .06f);
        wisps(m, r, 12, new int[] {0xFF1D6B5E, 0xFF2A8F7A}, .3f, .6f, 8, 22);
        emit(m, .12f, breathe(.5f, .25f, .2f), null);
        for (int group = 0; group < 5; group++) {
            for (int i = 0; i < 46; i++) {
                float az = r.nextFloat() * TAU, el = .015f + (float) Math.pow(r.nextFloat(), 1.5) * .55f, size = .006f + r.nextFloat() * .006f;
                int c = mix(primary, 0xFFFFF59D, r.nextFloat() * .6f);
                orb(m, az, el, size * .9f, c, 255, .55f);
                orb(m, az, el, size * 4.5f, c, 90, 0f);
            }
            final float ph = group * 1.7f;
            emitRising(m, .3f + group * .05f, t -> Math.clamp((float) Math.sin(t * (.9 + ph * .22) + ph) * 1.6f - .2f, 0f, 1f),
                    sway(.05f, .21f, ph), sway(1.4f, .6f, ph));
        }
        // dark treeline
        int segments = 300;
        float[] top = new float[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float a = i * TAU / segments;
            top[i] = .03f + .05f * (float) Math.abs(Math.sin(a * 9 + 2)) + .03f * (float) Math.abs(Math.sin(a * 23 + 1)) + .02f * r.nextFloat();
        }
        top[segments] = top[0];
        for (int i = 0; i < segments; i++) {
            float a0 = i * TAU / segments, a1 = (i + 1) * TAU / segments;
            m.ae(a0, -.06f, 0xFF02070A); m.ae(a1, -.06f, 0xFF02070A); m.ae(a1, top[i + 1], 0xFF02070A); m.ae(a0, top[i], 0xFF02070A);
        }
        emit(m, .0f, null, null);
    }
    // ================================================================ four more skies

    // ----------------------------------------------------------------------- Prism

    private void buildPrism(Mesh m, int primary) {
        Random r = new Random(7777);
        horizonGlow(m, 0xFFFFF4E8, 110, .5f, 0f);
        emit(m, 1f, breathe(0f, .25f, .08f), null);
        // soft pastel light bleeding across the dome
        wisps(m, r, 20, new int[] {0xFFFFC8E6, 0xFFC8D8FF, 0xFFFFF0C0, 0xFFD2FFE9}, .3f, .75f, 22, 48);
        emit(m, .12f, breathe(1f, .22f, .2f), null);
        // two rainbows (the second one fainter, colours reversed), built as soft bands that fade at both edges
        int[] bands = {0xFFFF4D4D, 0xFFFF9A3D, 0xFFFFE347, 0xFF55E36A, 0xFF4DB8FF, 0xFF6A5CFF, 0xFFB45CFF};
        for (int bow = 0; bow < 2; bow++) {
            float radius = bow == 0 ? .78f : .98f, bandWidth = bow == 0 ? .034f : .036f, alpha = bow == 0 ? 190 : 80;
            float az0 = .75f + bow * .02f, el0 = -.3f;
            for (int band = 0; band < bands.length; band++) {
                int color = bands[bow == 0 ? band : bands.length - 1 - band];
                float rm = radius - band * bandWidth * .92f * (bow == 0 ? 1 : -1);
                for (int i = 0; i < 70; i++) {
                    float t0 = .12f + i * (float) Math.PI * .76f / 70, t1 = .12f + (i + 1) * (float) Math.PI * .76f / 70;
                    float f0 = (float) Math.sin(Math.PI * i / 70.0), f1 = (float) Math.sin(Math.PI * (i + 1) / 70.0);
                    float a0 = alpha * (.35f + .65f * f0), a1 = alpha * (.35f + .65f * f1);
                    m.tp(az0, el0, (rm - bandWidth) * (float) Math.cos(t0), (rm - bandWidth) * (float) Math.sin(t0), alpha(color, 0));
                    m.tp(az0, el0, rm * (float) Math.cos(t0), rm * (float) Math.sin(t0), alpha(color, a0));
                    m.tp(az0, el0, rm * (float) Math.cos(t1), rm * (float) Math.sin(t1), alpha(color, a1));
                    m.tp(az0, el0, (rm - bandWidth) * (float) Math.cos(t1), (rm - bandWidth) * (float) Math.sin(t1), alpha(color, 0));
                    m.tp(az0, el0, rm * (float) Math.cos(t0), rm * (float) Math.sin(t0), alpha(color, a0));
                    m.tp(az0, el0, (rm + bandWidth) * (float) Math.cos(t0), (rm + bandWidth) * (float) Math.sin(t0), alpha(color, 0));
                    m.tp(az0, el0, (rm + bandWidth) * (float) Math.cos(t1), (rm + bandWidth) * (float) Math.sin(t1), alpha(color, 0));
                    m.tp(az0, el0, rm * (float) Math.cos(t1), rm * (float) Math.sin(t1), alpha(color, a1));
                }
            }
            emit(m, 0f, breathe(bow * 2f, .3f, .22f), null);
        }
        // fluffy white clouds with blue shadows
        float[] rows = {.08f, .2f, .36f, .56f, .8f, 1.08f};
        for (int row = 0; row < rows.length; row++) {
            int count = 8 - row / 2;
            for (int i = 0; i < count; i++) {
                float az = (i + r.nextFloat() * .8f) * TAU / count, el = rows[row] + (r.nextFloat() - .5f) * .05f, size = .1f + r.nextFloat() * .1f;
                for (int k = 0; k < 4; k++) {
                    float ox = (k - 1.5f) * size * .8f, oy = (float) Math.sin(k * 1.7f) * size * .25f;
                    puff(m, az, el, ox, oy - size * .18f, size * 1.3f, size * .7f, 0xFFA8BDE6, 120, r);
                    puff(m, az, el, ox, oy + size * .1f, size * 1.15f, size * .72f, 0xFFFFFFFF, 150, r);
                }
            }
            emit(m, .1f + row * .05f, breathe(row * 1.1f, .25f, .08f), null);
        }
        // prism sparkles
        int[] sparkle = {0xFFFFFFFF, 0xFFFFD0F0, 0xFFD0F0FF, 0xFFFFF4C0};
        for (int group = 0; group < 3; group++) {
            for (int i = 0; i < 70; i++) {
                float az = r.nextFloat() * TAU, el = .05f + r.nextFloat() * 1.3f;
                glint(m, az, el, .004f + r.nextFloat() * .006f, sparkle[r.nextInt(4)], 255, .1f);
            }
            emit(m, .1f, breathe(group * 2.2f, 1.6f + group * .8f, .9f), null);
        }
    }

    // --------------------------------------------------------------------- Rainfall

    private void buildRain(Mesh m, int primary) {
        Random r = new Random(2468);
        horizonGlow(m, 0xFF8EA0BE, 150, .45f, .2f);
        emit(m, 1f, breathe(0f, .3f, .12f), null);
        // layered grey overcast
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < 30; i++) {
                float az = r.nextFloat() * TAU, el = .25f + layer * .4f + r.nextFloat() * .45f, size = .3f + r.nextFloat() * .3f;
                puff(m, az, el, 0, 0, size * 2f, size * .8f, mix(0xFF1B2230, 0xFF4B5870, r.nextFloat() * (.5f + layer * .15f)), 175 - layer * 15, r);
            }
            emit(m, .12f + layer * .06f, breathe(layer * 1.6f, .22f, .1f), null);
        }
        // veils of rain falling from the cloud base, slow and soft
        for (int i = 0; i < 46; i++) {
            float az = r.nextFloat() * TAU, w = .05f + r.nextFloat() * .09f;
            beam(m, az, .5f, .26f, w, w * 1.2f, -.015f, 0xFF98A8C4, 0, 40);
            beam(m, az, .26f, 0f, w * 1.2f, w * 1.5f, -.015f, 0xFF98A8C4, 40, 6);
        }
        emit(m, .5f, breathe(1f, .5f, .3f), sway(.04f, .2f, 0f));
        // drops: three depths, fast, thin
        int drop = 0xFFC8D8F0;
        float[] rho = {78f, 52f, 30f}, width = {.03f, .05f, .08f}, len = {6f, 8f, 11f}, speed = {-70f, -95f, -130f};
        int[] count = {620, 330, 130};
        for (int layer = 0; layer < 3; layer++) {
            final float spd = speed[layer];
            for (int i = 0; i < count[layer]; i++) {
                float az = r.nextFloat() * TAU, y = FIELD_BOTTOM + r.nextFloat() * FIELD_PERIOD, rr = rho[layer] * (.92f + r.nextFloat() * .16f);
                float l = len[layer] * (.7f + r.nextFloat() * .6f);
                int head = alpha(drop, 50 + r.nextFloat() * 60), tail = alpha(drop, 0);
                for (int copy = 0; copy < 2; copy++) streak(m, az, y + copy * FIELD_PERIOD, rr, width[layer], l, head, tail);
            }
            emitRising(m, .01f, null, null, t -> (float) (((t * Math.abs(spd)) % FIELD_PERIOD) * Math.signum(spd)));
        }
        // far-off lightning lighting the cloud from inside, soft and rare
        double[] periods = {8.7, 13.1};
        for (int k = 0; k < periods.length; k++) {
            double period = periods[k], offset = k * 3.9;
            puff(m, r.nextFloat() * TAU, .55f + r.nextFloat() * .3f, 0, 0, .9f, .5f, 0xFFB4C8FF, 70, r);
            emit(m, .2f, t -> {
                double tt = (t + offset) % period;
                if (tt < .06) return 1f;
                if (tt < .14) return .1f;
                if (tt < .24) return .8f;
                if (tt < .6) return (float) (.45 * (1 - (tt - .24) / .36));
                return 0f;
            }, null);
        }
    }

    // ------------------------------------------------------------------ Cosmic Rift

    private void buildRift(Mesh m, int primary, int secondary) {
        Random r = new Random(9090);
        wisps(m, r, 30, new int[] {secondary, 0xFF5B2CFF, primary}, .3f, .8f, 12, 32);
        emit(m, .15f, breathe(0f, .35f, .3f), null);
        scatterStars(m, r, 0xFFFFFFFF, 2, 380, .08f);
        float riftAz = .6f;
        // the rift: a jagged tear climbing the sky, with a white core, a cyan edge and a magenta halo
        List<float[]> rough = new ArrayList<>();
        float a = riftAz, e = -.05f;
        rough.add(new float[] {a, e});
        for (int i = 0; i < 12; i++) {
            a += (r.nextFloat() - .5f) * .2f;
            e += .12f + r.nextFloat() * .05f;
            rough.add(new float[] {a, e});
        }
        List<float[]> path = smooth(rough, 6);
        int np = path.size();
        for (int ring = 0; ring < 4; ring++) {
            float base = new float[] {.22f, .1f, .04f, .012f}[ring];
            int color = new int[] {secondary, secondary, primary, 0xFFFFFFFF}[ring];
            float peak = new float[] {60, 110, 190, 255}[ring];
            float[] w = new float[np], al = new float[np];
            for (int i = 0; i < np; i++) {
                float taper = (float) Math.sin(Math.PI * i / (np - 1)) * .8f + .2f;
                float wobble = 1f + .25f * (float) Math.sin(i * .9f + ring);
                w[i] = base * taper * wobble;
                al[i] = peak * (.55f + .45f * taper);
            }
            softStrip(m, path, w, color, al);
        }        emit(m, .06f, breathe(0f, 1.2f, .35f), null);
        // branching arcs of energy that flicker around it
        for (int group = 0; group < 4; group++) {
            for (int b = 0; b < 4; b++) {
                float[] from = path.get(6 + r.nextInt(np - 14));
                float ba = from[0], be = from[1], dir = r.nextBoolean() ? 1 : -1;
                float pa = ba, pe = be;
                for (int i = 0; i < 9; i++) {
                    float na = pa + dir * (.02f + r.nextFloat() * .035f), ne = pe + (r.nextFloat() - .3f) * .035f;
                    softRibbon(m, pa, pe, na, ne, .03f, primary, 150, 150);
                    softRibbon(m, pa, pe, na, ne, .008f, 0xFFFFFFFF, 255, 230);
                    pa = na; pe = ne;
                }
            }
            final double period = 1.3 + group * .55, offset = group * .7;
            emit(m, .06f, t -> {
                double tt = (t + offset) % period;
                return tt < .08 ? 1f : tt < .15 ? .15f : tt < .26 ? .85f : 0f;
            }, null);
        }
        // sparks and dust drawn toward the tear
        for (int group = 0; group < 3; group++) {
            for (int i = 0; i < 90; i++) {
                float[] p = path.get(r.nextInt(path.size()));
                float az = p[0] + (float) r.nextGaussian() * .16f, el = p[1] + (float) r.nextGaussian() * .1f;
                glint(m, az, el, .003f + r.nextFloat() * .006f, mix(primary, secondary, r.nextFloat()), 255, .15f);
            }
            emit(m, .06f, breathe(group * 1.8f, 1.1f + group * .5f, .8f), sway(.03f, .4f, group));
        }
        shootingStars(m, r, primary);
    }

    // --------------------------------------------------------------- Meteor Shower

    private void buildMeteors(Mesh m, int primary) {
        Random r = new Random(4711);
        horizonGlow(m, 0xFFFF9A5C, 95, .3f, 0f);
        emit(m, 1f, breathe(0f, .3f, .15f), null);
        // a faint Milky Way
        float nx = .3f, ny = .85f, nz = .42f, nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl; ny /= nl; nz /= nl;
        float ux = -nz, uz = nx, ul = (float) Math.sqrt(ux * ux + uz * uz);
        ux /= ul; uz /= ul;
        float vx = ny * uz, vy = nz * ux - nx * uz, vz = -nx * 0 - ny * ux;
        for (int i = 0; i < 1500; i++) {
            float phi = r.nextFloat() * TAU, spread = (float) r.nextGaussian() * .12f;
            float x = (float) Math.cos(phi) * ux + (float) Math.sin(phi) * vx + spread * nx;
            float y = (float) Math.sin(phi) * vy + spread * ny;
            float z = (float) Math.cos(phi) * uz + (float) Math.sin(phi) * vz + spread * nz;
            float el = (float) Math.asin(Math.clamp(y / (float) Math.sqrt(x * x + y * y + z * z), -1f, 1f));
            glint(m, (float) Math.atan2(z, x), el, .0017f + r.nextFloat() * .0018f, STAR_TINTS[r.nextInt(3)], 60 + r.nextInt(130), .2f);
        }
        emit(m, .5f, null, null);
        scatterStars(m, r, 0xFFFFFFFF, 3, 560, .1f);
        // meteors: each crosses the sky on its own schedule with a long fading tail
        double[] periods = {2.9, 3.7, 4.4, 5.3, 6.1, 7.0, 7.9, 9.2, 10.6, 12.1};
        for (int k = 0; k < periods.length; k++) {
            float az = r.nextFloat() * TAU, el = .35f + r.nextFloat() * .8f;
            float dirA = (r.nextBoolean() ? 1 : -1), tailLen = .22f + r.nextFloat() * .22f, slope = .22f + r.nextFloat() * .25f;
            int head = mix(primary, 0xFFFFFFFF, .75f), warm = alpha(primary, 150), tail = alpha(mix(primary, 0xFFFF7A3D, .5f), 0);
            // a slanted ribbon: bright head at the front, cooling to nothing behind
            float tx = -dirA * tailLen, ty = slope * tailLen;
            m.tp(az, el, 0, .003f, head); m.tp(az, el, 0, -.003f, head);
            m.tp(az, el, tx * .35f, ty * .35f - .002f, warm); m.tp(az, el, tx * .35f, ty * .35f + .002f, warm);
            m.tp(az, el, tx * .35f, ty * .35f + .002f, warm); m.tp(az, el, tx * .35f, ty * .35f - .002f, warm);
            m.tp(az, el, tx, ty - .0004f, tail); m.tp(az, el, tx, ty + .0004f, tail);
            glint(m, az, el, .012f, 0xFFFFFFFF, 255, .25f);
            glint(m, az, el, .05f, primary, 90, 0f);
            double period = periods[k], offset = k * 1.37;
            final float speedA = dirA * .5f, speedY = -slope * .5f * 18f;
            emitRising(m, 1f, t -> {
                double tt = (t + offset) % period;
                return tt < 1.0 ? (float) Math.sin(Math.PI * tt / 1.0) : 0f;
            }, t -> (float) (((t + offset) % period) * speedA * .5), t -> (float) (((t + offset) % period) * speedY * .5));
        }
    }
    // ------------------------------------------------------------------ Black Hole

    /** A band of an ellipse around (az, el), tilted, between two radius factors, each with its own colour. */
    private static void discBand(Mesh m, float az, float el, float rx, float ry, float tilt, float f0, float f1,
                                 int c0, int c1, float from, float to, int steps) {
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        for (int i = 0; i < steps; i++) {
            float t0 = from + (to - from) * i / steps, t1 = from + (to - from) * (i + 1) / steps;
            float[] ts = {t0, t0, t1, t1};
            float[] fs = {f0, f1, f1, f0};
            int[] cs = {c0, c1, c1, c0};
            for (int k = 0; k < 4; k++) {
                float px = rx * fs[k] * (float) Math.cos(ts[k]), py = ry * fs[k] * (float) Math.sin(ts[k]);
                m.tp(az, el, px * ct - py * st, px * st + py * ct, cs[k]);
            }
        }
    }

    private void buildBlackHole(Mesh m, int primary) {
        Random r = new Random(1915);
        wisps(m, r, 18, new int[] {0xFF2B3A8F, 0xFF5B2C8F, 0xFF123A6B}, .35f, .8f, 10, 26);
        emit(m, .2f, breathe(0f, .3f, .2f), null);
        scatterStars(m, r, 0xFFFFFFFF, 3, 620, .1f);
        float az = 1.0f, el = .78f, tilt = -.32f, hole = .1f;
        int white = 0xFFFFF7E6, gold = mix(primary, 0xFFFFE0A0, .35f), ember = 0xFFFF5A1A;
        // a wide warm glow, then the far side of the disc bent up and over the hole by gravity
        puff(m, az, el, 0, 0, .95f, .6f, ember, 30, r);
        puff(m, az, el, 0, 0, .5f, .34f, gold, 44, r);
        discBand(m, az, el, hole * 1.05f, hole * 1.05f, 0, 1f, 1.5f, alpha(white, 235), alpha(gold, 120), 0, TAU, 72);
        discBand(m, az, el, hole * 1.05f, hole * 1.05f, 0, 1.5f, 2.5f, alpha(gold, 120), alpha(ember, 0), 0, TAU, 72);
        emit(m, .03f, breathe(0f, .8f, .12f), null);
        // the far half of the disc (behind the hole)
        discBand(m, az, el, .58f, .13f, tilt, .22f, .5f, alpha(white, 235), alpha(gold, 215), 0, (float) Math.PI, 60);
        discBand(m, az, el, .58f, .13f, tilt, .5f, 1f, alpha(gold, 215), alpha(ember, 0), 0, (float) Math.PI, 60);
        emit(m, .03f, breathe(1f, 1.1f, .14f), null);
        // the hole itself: nothing, with a razor-thin ring of light around it
        discBand(m, az, el, hole, hole, 0, 0f, 1f, 0xFF000000, 0xFF000000, 0, TAU, 48);
        discBand(m, az, el, hole, hole, 0, .96f, 1.06f, alpha(white, 255), alpha(white, 0), 0, TAU, 72);
        emit(m, .03f, null, null);
        // the near half of the disc, crossing in front
        discBand(m, az, el, .58f, .13f, tilt, .2f, .5f, alpha(white, 255), alpha(gold, 230), (float) Math.PI, TAU, 60);
        discBand(m, az, el, .58f, .13f, tilt, .5f, 1f, alpha(gold, 230), alpha(ember, 0), (float) Math.PI, TAU, 60);
        emit(m, .03f, breathe(2f, 1.3f, .14f), null);
        // hot clumps circling in the disc, and thin jets from the poles
        for (int group = 0; group < 3; group++) {
            float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
            for (int i = 0; i < 70; i++) {
                float t = r.nextFloat() * TAU, f = .25f + r.nextFloat() * .7f;
                float px = .58f * f * (float) Math.cos(t), py = .13f * f * (float) Math.sin(t);
                glint(m, az + (px * ct - py * st) / (float) Math.cos(el), el + px * st + py * ct, .003f + r.nextFloat() * .004f, mix(white, ember, f), 255, .2f);
            }
            emit(m, .03f, breathe(group * 2.1f, 1.6f + group * .7f, .7f), null);
        }
        for (int side = -1; side <= 1; side += 2) {
            List<float[]> jet = new ArrayList<>();
            int n = 14;
            float[] w = new float[n], al = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (float) (n - 1), dist = hole + u * .75f;
                jet.add(new float[] {az + side * dist * (float) Math.sin(-tilt) / (float) Math.cos(el), el + side * dist * (float) Math.cos(tilt)});
                w[i] = .012f + u * .05f;
                al[i] = 150 * (1f - u);
            }
            softStrip(m, jet, w, 0xFF8FD0FF, al);
        }
        emit(m, .03f, breathe(.5f, .9f, .35f), null);
        shootingStars(m, r, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------- Lantern Festival

    /**
     * Tempest: a painterly storm. No lightning and no flat bands -- the whole sky is brushed with long curved strokes
     * that follow a few slow swirls, dark masses underneath, paler streaks on top, with one side of the sky catching
     * more light. Several stroke layers turn at different speeds, so the forms visibly streak past each other.
     */
    private void buildTempest(Mesh m, int primary, int secondary) {
        Random r = new Random(7331);
        int black = mix(secondary, 0xFF000000, .35f), dark = secondary, slate = mix(secondary, primary, .28f);
        int grey = mix(secondary, primary, .62f), pale = mix(primary, 0xFFFFFFFF, .18f);
        // the swirls every stroke bends around: azimuth, elevation, reach, turning direction and strength
        float[][] eyes = new float[6][];
        for (int i = 0; i < eyes.length; i++) {
            eyes[i] = new float[] {i * TAU / eyes.length + r.nextFloat() * .7f, .22f + r.nextFloat() * .85f,
                    .42f + r.nextFloat() * .38f, (r.nextBoolean() ? 1f : -1f) * (.9f + r.nextFloat() * .9f)};
        }
        float lightAz = 1.1f, lightEl = .3f;      // the side of the sky the light breaks through on

        // 1) heavy masses: big soft blobs that give the strokes something dark to sit on
        for (int i = 0; i < 70; i++) {
            float az = r.nextFloat() * TAU, el = -.25f + r.nextFloat() * 1.75f, size = .34f + r.nextFloat() * .42f;
            puff(m, az, Math.min(el, 1.5f), 0, 0, size * 1.7f, size * .8f, mix(black, slate, r.nextFloat() * .7f), 150 + r.nextFloat() * 70, r);
        }
        emit(m, .22f, null, null);

        // 2) brush strokes in four layers, back (broad, dark, slow) to front (thin, pale, fast)
        float[] layerSpin = {.35f, -.28f, .62f, .95f};
        int[] layerCount = {110, 120, 130, 90};
        for (int layer = 0; layer < 4; layer++) {
            float depth = layer / 3f;
            for (int s = 0; s < layerCount[layer]; s++) {
                float az = r.nextFloat() * TAU, el = -.08f + r.nextFloat() * 1.28f;
                int steps = 7 + r.nextInt(7);
                float stride = .05f + r.nextFloat() * .05f;
                float heading = (r.nextFloat() - .5f) * .5f;          // mostly sideways, like wind
                List<float[]> rough = new ArrayList<>();
                for (int k = 0; k <= steps; k++) {
                    rough.add(new float[] {az, el});
                    float fx = (float) Math.cos(heading), fy = (float) Math.sin(heading) * .45f;
                    for (float[] eye : eyes) {                       // each swirl pushes the stroke around itself
                        float cosEl = Math.max(.25f, (float) Math.cos(el));
                        float dx = (float) Math.atan2(Math.sin(az - eye[0]), Math.cos(az - eye[0])) * cosEl, dy = el - eye[1];
                        float d2 = dx * dx + dy * dy, pull = eye[3] * (float) Math.exp(-d2 / (eye[2] * eye[2]));
                        float d = (float) Math.sqrt(d2) + .08f;
                        fx += -dy / d * pull;
                        fy += dx / d * pull;
                    }
                    float len = (float) Math.hypot(fx, fy) + 1e-4f;
                    az += fx / len * stride / Math.max(.3f, (float) Math.cos(el));
                    el = Math.clamp(el + fy / len * stride, -.12f, 1.3f);
                }
                // a stroke caught in the middle of a swirl just spins on the spot and would draw as a spiky knot: drop it
                float[] first = rough.getFirst(), last = rough.getLast();
                float travelled = (float) Math.hypot((last[0] - first[0]) * Math.cos(first[1]), last[1] - first[1]);
                if (travelled < stride * steps * .45f) continue;
                List<float[]> path = smooth(rough, 3);
                int n = path.size();
                float broad = (.11f - .07f * depth) * (.6f + r.nextFloat() * .9f);
                // how much light this stroke catches: more toward the bright side and in the front layers
                float towardLight = (float) Math.exp(-Math.pow(Math.atan2(Math.sin(az - lightAz), Math.cos(az - lightAz)), 2) / .9f
                        - Math.pow(el - lightEl, 2) / .5f);
                float tone = Math.clamp(r.nextFloat() * (.25f + .45f * depth) + towardLight * (.35f + .4f * depth), 0f, 1f);
                int color = tone < .5f ? mix(dark, grey, tone * 2f) : mix(grey, pale, (tone - .5f) * 2f);
                if (r.nextFloat() < .22f - .1f * depth) color = mix(black, dark, r.nextFloat());      // deep shadow strokes
                float peak = (120f - 40f * depth) * (.55f + r.nextFloat() * .6f) + towardLight * 50f;
                float[] width = new float[n], alphaAt = new float[n];
                for (int k = 0; k < n; k++) {
                    float t = k / (float) (n - 1), taper = (float) Math.sin(t * Math.PI);
                    width[k] = broad * (.35f + .65f * taper);
                    alphaAt[k] = peak * taper * taper;
                }
                softStrip(m, path, width, color, alphaAt);
            }
            // mottling: small soft dabs travelling with this layer, so the strokes read as cloud and not as silk
            for (int i = 0; i < 150; i++) {
                float az = r.nextFloat() * TAU, el = -.05f + r.nextFloat() * 1.3f, size = .05f + r.nextFloat() * .11f;
                float towardLight = (float) Math.exp(-Math.pow(Math.atan2(Math.sin(az - lightAz), Math.cos(az - lightAz)), 2) / .9f - Math.pow(el - lightEl, 2) / .5f);
                int dab = r.nextFloat() < .55f ? mix(black, slate, r.nextFloat()) : mix(slate, pale, r.nextFloat() * (.35f + .65f * towardLight));
                puff(m, az, el, 0, 0, size * (1.4f + r.nextFloat()), size, dab, 34 + r.nextFloat() * 50, r);
            }
            emit(m, layerSpin[layer], breathe(layer * 1.7f, .12f + .05f * layer, .22f), sway(.02f + .015f * layer, .07f + .03f * layer, layer * 2.1f));
        }

        // 3) the zenith, where the stroke coordinates pinch: closed with slow dark cloud instead
        for (int i = 0; i < 26; i++) {
            float az = r.nextFloat() * TAU, el = 1.18f + r.nextFloat() * .38f, size = .2f + r.nextFloat() * .26f;
            puff(m, az, Math.min(el, 1.55f), 0, 0, size * 1.5f, size, mix(black, grey, r.nextFloat() * .55f), 120 + r.nextFloat() * 60, r);
        }
        emit(m, .5f, breathe(.4f, .1f, .2f), null);

        // 4) the break in the cloud: a soft pale glow low on the bright side, and haze along the whole horizon
        puff(m, lightAz, lightEl, 0, 0, .95f, .42f, pale, 62, r);
        puff(m, lightAz + .25f, lightEl - .08f, 0, 0, .5f, .2f, 0xFFDADDE4, 40, r);
        emit(m, .22f, breathe(0f, .18f, .3f), null);
        horizonGlow(m, mix(slate, grey, .4f), 120, .16f, .1f);
        emit(m, 0f, null, null);
    }

    private void buildLanterns(Mesh m, int primary) {
        Random r = new Random(815);
        horizonGlow(m, 0xFFFF8A3D, 120, .4f, .15f);
        emit(m, 1f, breathe(0f, .4f, .15f), null);
        wisps(m, r, 14, new int[] {0xFF5A3A7A, 0xFF7A3A5A, 0xFF3A3F8A}, .35f, .75f, 10, 26);
        emit(m, .12f, breathe(1f, .25f, .2f), null);
        scatterStars(m, r, 0xFFFFFFFF, 3, 420, .08f);
        int[] paper = {primary, 0xFFFFC46B, 0xFFFF8F4D, 0xFFFFD98A};
        float[] rho = {80f, 56f, 34f}, size = {.42f, .75f, 1.25f}, speed = {1.6f, 2.5f, 3.6f};
        int[] count = {230, 120, 46};
        for (int layer = 0; layer < 3; layer++) {
            final float s = size[layer];
            fall(m, r, count[layer], rho[layer], speed[layer], .04f + layer * .02f, breathe(layer * 1.9f, 1.4f + layer * .5f, .16f),
                    sway(.05f + layer * .02f, .3f + layer * .1f, layer * 2f),
                    (mm, rr, az, y, rh) -> {
                        int c = paper[rr.nextInt(4)];
                        float k = s * (.75f + rr.nextFloat() * .5f);
                        speck(mm, az, y, rh, k * 2.6f, k * 2.9f, c, 46, 0f);                 // the glow around it
                        speck(mm, az, y, rh, k * .62f, k * .85f, mix(c, 0xFFFFF2C8, .35f), 255, .7f);   // the paper lantern
                        speck(mm, az, y - k * .55f, rh, k * .28f, k * .22f, 0xFFFFFFE0, 255, .4f);        // the flame at its base
                    });
        }
    }
    @Override public void close() {
        if (buffer != null) buffer.close();
        buffer = null;
        key = null;
        layers.clear();
        totalQuads = 0;
    }

    public static int colorAt(SkyPreset preset, long millis, float tickDelta) {
        return SkyColorMath.colorAt(preset, millis);
    }
}
