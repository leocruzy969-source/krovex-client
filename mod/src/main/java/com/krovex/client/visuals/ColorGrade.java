package com.krovex.client.visuals;

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
import org.lwjgl.system.MemoryStack;

import java.util.OptionalInt;

/**
 * Colour grading as a real post-process: once per frame, after the world is drawn and before any HUD is, the
 * framebuffer is copied to a texture and drawn back through a small shader (saturation, contrast, warmth, shadows).
 * With neutral settings (or the switch off) nothing at all is done.
 */
public final class ColorGrade {
    private static RenderPipeline pipeline;
    private static GpuTexture copy;
    private static GpuTextureView copyView;
    private static int copyWidth, copyHeight;
    private static GpuBuffer config;
    private static int configHash;
    private static boolean failed;

    private ColorGrade() {}

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.of("krovex-client", "pipeline/color_grade"))
                    .withVertexShader(Identifier.of("krovex-client", "core/grade_quad"))
                    .withFragmentShader(Identifier.of("krovex-client", "post/color_grade"))
                    .withSampler("InSampler")
                    .withUniform("GradeConfig", UniformType.UNIFORM_BUFFER)
                    .withoutBlend()
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.QUADS)
                    .build();
        }
        return pipeline;
    }

    public static void apply(MinecraftClient client) {
        var store = ColorGradeStore.get();
        if (failed || !store.enabled() || store.neutral()) return;
        try {
            var target = client.getFramebuffer();
            int w = target.textureWidth, h = target.textureHeight;
            var device = RenderSystem.getDevice();
            if (copy == null || copyWidth != w || copyHeight != h) {
                release();
                copy = device.createTexture("Krovex grade copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        target.getColorAttachment().getFormat(), w, h, 1, 1);
                copyView = device.createTextureView(copy);
                copyWidth = w;
                copyHeight = h;
            }
            int hash = java.util.Objects.hash(store.saturation(), store.contrast(), store.warmth(), store.shadows());
            if (config == null || hash != configHash) {
                if (config != null) config.close();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var data = Std140Builder.onStack(stack, 16).putVec4(store.saturation(), store.contrast(), store.warmth(), store.shadows()).get();
                    config = device.createBuffer(() -> "Krovex grade config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
                }
                configHash = hash;
            }
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(target.getColorAttachment(), copy, 0, 0, 0, 0, 0, w, h);
            try (var pass = encoder.createRenderPass(() -> "Krovex color grade", target.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(pipeline());
                pass.bindTexture("InSampler", copyView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                pass.setUniform("GradeConfig", config);
                pass.draw(0, 3);
            }
        } catch (RuntimeException e) {
            failed = true;      // a graphics problem must never crash the game: switch the effect off for this session
            release();
            org.slf4j.LoggerFactory.getLogger("krovex-client").warn("Color grading disabled: {}", e.toString());
        }
    }

    private static void release() {
        if (copyView != null) copyView.close();
        if (copy != null) copy.close();
        copyView = null;
        copy = null;
        if (config != null) { config.close(); config = null; }
    }

    /** True if grading hit a graphics error and switched itself off (shown in the Color Grading tab). */
    public static boolean failed() { return failed; }
}