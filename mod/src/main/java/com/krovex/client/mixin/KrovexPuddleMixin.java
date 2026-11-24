package com.krovex.client.mixin;

import com.krovex.client.visuals.Puddles;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reflective Puddles hook: remembers the matrices the world is drawn with, then runs the puddle pass as soon as the
 * world is finished (before the hand is drawn, so puddles never paint over it). method_22710 = WorldRenderer.render.
 * require = 0: if the name ever stops matching, the module just does nothing instead of stopping the game.
 */
@Mixin(WorldRenderer.class)
abstract class KrovexPuddleMixin {
    @Inject(method = "method_22710", at = @At("HEAD"), remap = false, require = 0)
    private void krovex$captureMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
                                        Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix,
                                        GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        Puddles.capture(positionMatrix, projectionMatrix, camera.getCameraPos());
    }

    @Inject(method = "method_22710", at = @At("RETURN"), remap = false, require = 0)
    private void krovex$drawPuddles(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
                                    Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix,
                                    GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        Puddles.render();
    }
}
