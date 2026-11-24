package com.krovex.client.visuals;

import com.krovex.client.features.Features;
import com.krovex.client.sky.SkyPreset;
import com.krovex.client.sky.SkyRenderer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.OptionalInt;

/**
 * Reflective Puddles: a post-process over the finished world (before the hand and the HUD are drawn). The shader reads
 * the depth buffer to find flat, upward-facing ground, lays organic noise-shaped puddles on it and fills them with a
 * mirror image of what is above them. The mirror is taken from the frame itself, so whatever sky is on screen -- vanilla
 * or any Krovex sky preset from {@link SkyRenderer} -- is what the water shows; where the mirrored direction is off
 * screen it falls back to that same preset's own sky colours. Nothing here knows about individual presets.
 */
public final class Puddles {
    public static final String ID = "puddles";

    private static RenderPipeline pipeline;
    private static GpuTexture copy;
    private static GpuTextureView copyView;
    private static int copyWidth, copyHeight;
    private static final GpuBuffer[] configs = new GpuBuffer[2];
    private static boolean failed;

    private static final Matrix4f view = new Matrix4f(), projection = new Matrix4f();
    private static Vec3d cameraPos = Vec3d.ZERO;
    private static boolean captured;

    /** How wet the ground still is: climbs while it rains, dries slowly afterwards. */
    private static float wetness;
    private static long lastNanos;

    private Puddles() {}

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.of("krovex-client", "pipeline/puddles"))
                    .withVertexShader(Identifier.of("krovex-client", "core/grade_quad"))
                    .withFragmentShader(Identifier.of("krovex-client", "post/puddles"))
                    .withSampler("InSampler")
                    .withSampler("DepthSampler")
                    .withUniform("PuddleConfig", UniformType.UNIFORM_BUFFER)
                    .withoutBlend()
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.QUADS)
                    .build();
        }
        return pipeline;
    }

    /** Called at the start of the world render with the matrices that frame is drawn with. */
    public static void capture(Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d camera) {
        view.set(positionMatrix);
        projection.set(projectionMatrix);
        cameraPos = camera;
        captured = true;
    }

    /** True if the effect hit a graphics error and switched itself off for this session. */
    public static boolean failed() { return failed; }

    /** 0..1: how much of the configured coverage is showing right now (weather link). */
    private static float level(MinecraftClient client) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(.25f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float rain = client.world == null ? 0f : client.world.getRainGradient(1f);
        if (rain > .05f) wetness = Math.min(1f, wetness + dt / 9f);        // puddles fill in about nine seconds of rain
        else wetness = Math.max(0f, wetness - dt / 150f);                  // and take a couple of minutes to dry
        if (Features.flag(ID, "always", false)) return 1f;
        return Math.max(wetness, rain * .6f);
    }

    /** 0..1: how deep in a puddle the player is standing right now (smoothed). Drives the extra reflectivity and the wet skin. */
    private static float standing;

    // The shader's noise, ported 1:1 (only fract / multiply / add, so the CPU and GPU agree) to find out whether the feet are in a puddle.
    private static float fract(float v) { return v - (float) Math.floor(v); }
    private static float hash(float x, float y) {
        float a = fract(x * .1031f), b = fract(y * .1031f), c = fract(x * .1031f);
        float d = a * (b + 33.33f) + b * (c + 33.33f) + c * (a + 33.33f);
        a += d; b += d; c += d;
        return fract((a + b) * c);
    }
    private static float noise(float x, float y) {
        float ix = (float) Math.floor(x), iy = (float) Math.floor(y), fx = x - ix, fy = y - iy;
        fx = fx * fx * (3f - 2f * fx); fy = fy * fy * (3f - 2f * fy);
        float top = hash(ix, iy) + (hash(ix + 1, iy) - hash(ix, iy)) * fx;
        float bottom = hash(ix, iy + 1) + (hash(ix + 1, iy + 1) - hash(ix, iy + 1)) * fx;
        return top + (bottom - top) * fy;
    }
    private static float fbm(float x, float y) {
        return noise(x, y) * .55f + noise(x * 2.13f + 7.1f, y * 2.13f + 7.1f) * .28f + noise(x * 4.7f + 3.3f, y * 4.7f + 3.3f) * .17f;
    }
    private static float puddleMaskAt(double worldX, double worldZ, float coverage) {
        float x = (float) (worldX - (cameraPos.x - cameraPos.x % 4096.0)), z = (float) (worldZ - (cameraPos.z - cameraPos.z % 4096.0));
        float field = fbm(x * .085f, z * .085f) * .74f + fbm(x * .31f + 31.7f, z * .31f + 31.7f) * .26f;
        float threshold = .74f + (.27f - .74f) * Math.clamp(coverage, 0f, 1f);
        float t = Math.clamp((field - threshold) / .07f, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** How strongly the storm look shows this frame (0 = not at all); set by the world pass, read by the grade pass. */
    private static float storm;

    /** Called right after the world is drawn (and before the hand): puddles, haze, sky dimming, glow and rain. */
    public static void render() {
        var client = MinecraftClient.getInstance();
        storm = 0f;
        if (failed || !captured || client.world == null || !Features.on(ID)) return;
        captured = false;
        float level = level(client);
        if (level < .02f) return;
        if (!client.world.getDimension().hasSkyLight()) return;
        // only where the sky can reach: no storm deep inside caves or buildings
        float open = client.world.getLightLevel(LightType.SKY, BlockPos.ofFloored(cameraPos)) / 15f;
        open = Math.clamp((open - .2f) / .5f, 0f, 1f);
        if (open < .02f) return;
        storm = level * open;
        float coverage = (float) Features.value(ID, "density", .55) * storm;
        float target = 0f;
        if (client.player != null && client.player.isOnGround()) target = puddleMaskAt(client.player.getX(), client.player.getZ(), coverage);
        standing += (target - standing) * .12f;                       // eased, so stepping in and out is gradual
        draw(client, 0f);
    }

    /**
     * Called just before the HUD is drawn (the hand is on screen by now): the storm's colour grade over the whole
     * view, so the hand and held item match the world instead of staying bright.
     */
    public static void grade(MinecraftClient client) {
        if (failed || storm < .02f || client.world == null || !Features.on(ID)) { standing = 0f; return; }
        draw(client, 1f);
    }

    /** One full-screen pass of the storm shader. mode 0 = the world pass (needs depth), 1 = the colour grade. */
    private static void draw(MinecraftClient client, float mode) {
        try {
            var target = client.getFramebuffer();
            if (target.getDepthAttachmentView() == null) return;
            int w = target.textureWidth, h = target.textureHeight;
            var device = RenderSystem.getDevice();
            if (copy == null || copyWidth != w || copyHeight != h) {
                releaseCopy();
                copy = device.createTexture("Krovex storm copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        target.getColorAttachment().getFormat(), w, h, 1, 1);
                copyView = device.createTextureView(copy);
                copyWidth = w;
                copyHeight = h;
            }
            int slot = mode < .5f ? 0 : 1;
            if (configs[slot] == null) configs[slot] = device.createBuffer(() -> "Krovex storm config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 256L);

            float coverage = (float) Features.value(ID, "density", .55) * storm;
            float reflectivity = (float) Features.value(ID, "reflect", .85);
            float rain = client.world.getRainGradient(1f);
            boolean grading = Features.flag(ID, "grade", true);
            int tint = 0xFF3E6F86;       // a fixed cold storm blue: the theme colour must never tint the game
            int[] sky = skyColours(client);
            var viewProjection = new Matrix4f(projection).mul(view);
            var inverse = new Matrix4f(viewProjection).invert();
            float time = (float) ((System.nanoTime() / 1e9) % 3600.0);

            var encoder = device.createCommandEncoder();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var data = Std140Builder.onStack(stack, 256)
                        .putMat4f(inverse)
                        .putMat4f(viewProjection)
                        .putVec4((float) (cameraPos.x % 4096.0), (float) cameraPos.y, (float) (cameraPos.z % 4096.0), time)
                        .putVec4(coverage, reflectivity, storm, rain)
                        .putVec4((sky[0] >> 16 & 255) / 255f, (sky[0] >> 8 & 255) / 255f, (sky[0] & 255) / 255f, 1f)
                        .putVec4((sky[1] >> 16 & 255) / 255f, (sky[1] >> 8 & 255) / 255f, (sky[1] & 255) / 255f, 1f)
                        .putVec4((float) Features.value(ID, "sky", .35), (float) Features.value(ID, "haze", 1.0),
                                (float) Features.value(ID, "glow", .65), Features.flag(ID, "rain", false) ? 1f : 0f)
                        .putVec4(grading ? (float) Features.value(ID, "saturation", .65) : 1f, grading ? (float) Features.value(ID, "brightness", .95) : 1f,
                                grading ? (float) Features.value(ID, "contrast", 1.05) : 1f, grading ? (float) Features.value(ID, "tint", .30) : 0f)
                        .putVec4((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, mode)
                        .putVec4(standing, 0f, 0f, 0f)
                        .get();
                encoder.writeToBuffer(configs[slot].slice(), data);
            }
            encoder.copyTextureToTexture(target.getColorAttachment(), copy, 0, 0, 0, 0, 0, w, h);
            try (var pass = encoder.createRenderPass(() -> "Krovex storm", target.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(pipeline());
                pass.bindTexture("InSampler", copyView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.bindTexture("DepthSampler", target.getDepthAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                pass.setUniform("PuddleConfig", configs[slot]);
                pass.draw(0, 3);
            }
        } catch (RuntimeException e) {
            failed = true;      // a graphics problem must never crash the game: switch the effect off for this session
            storm = 0f;
            releaseCopy();
            for (int i = 0; i < configs.length; i++) if (configs[i] != null) { configs[i].close(); configs[i] = null; }
            org.slf4j.LoggerFactory.getLogger("krovex-client").warn("Storm shader disabled: {}", e.toString());
        }
    }

    /** Zenith and horizon colour of the sky that is showing: the active Krovex preset's own palette, or vanilla's by time of day. */
    private static int[] skyColours(MinecraftClient client) {
        SkyPreset preset = SkyRenderer.activeCustom();
        if (preset != null) {
            int[] colours = SkyRenderer.previewColors(preset.kind());
            return new int[] {colours[0], colours[2]};
        }
        long time = client.world.getTimeOfDay() % 24000L;
        float day = (float) Math.clamp(Math.cos((time - 6000L) / 24000.0 * 2 * Math.PI) * 1.6 + .55, .06, 1.0);
        float dull = 1f - client.world.getRainGradient(1f) * .55f;
        return new int[] {scale(0xFF3F74C8, day * dull), scale(0xFFB4D2F0, day * dull)};
    }

    private static int scale(int argb, float f) {
        int r = Math.round((argb >> 16 & 255) * f), g = Math.round((argb >> 8 & 255) * f), b = Math.round((argb & 255) * f);
        return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    private static void releaseCopy() {
        if (copyView != null) copyView.close();
        if (copy != null) copy.close();
        copyView = null;
        copy = null;
    }
}
