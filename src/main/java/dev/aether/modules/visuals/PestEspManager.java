package dev.aether.modules.visuals;

import dev.aether.config.AetherConfig;
import dev.aether.macro.MacroState;
import dev.aether.modules.pest.helpers.PestTargetTracker;
import dev.aether.renderer.NVGRenderer;
import dev.aether.renderer.NanoVGManager;
import dev.aether.util.ClientUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/** Renders only currently loaded, confirmed pest entities in the Garden. */
public final class PestEspManager {
    private PestEspManager() {
    }

    public static boolean hasVisibleHighlights() {
        return AetherConfig.PEST_ESP_ENABLED.get()
                && ClientUtils.getCurrentLocation() == MacroState.Location.GARDEN
                && (AetherConfig.PEST_ESP_HIGHLIGHT.get() || AetherConfig.PEST_ESP_TRACER.get());
    }

    public static void renderWorld() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null
                || StreamerModeManager.isEnabled()
                || !hasVisibleHighlights()) {
            return;
        }

        for (PestData pest : getRenderablePests(client)) {
            int rgb = pestColor(AetherConfig.PEST_ESP_HIGHLIGHT_COLOR.get());
            if (AetherConfig.PEST_ESP_HIGHLIGHT.get()) {
                int stroke = argb(220, rgb);
                int fill = argb(45, rgb);
                Gizmos.cuboid(pest.box(), GizmoStyle.strokeAndFill(stroke, 2.0f, fill)).setAlwaysOnTop();
            }
            if (AetherConfig.PEST_ESP_TRACER.get()
                    && !client.options.getCameraType().isFirstPerson()) {
                int tracer = argb(255, pestColor(AetherConfig.PEST_ESP_TRACER_COLOR.get()));
                Gizmos.line(client.player.getEyePosition(), pest.position(), tracer, 5.0f).setAlwaysOnTop();
            }
        }
    }

    /** Draws first-person tracers using the final rendered camera for zero-lag movement. */
    public static void renderFirstPersonTracerOverlay() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null
                || client.screen != null
                || StreamerModeManager.isEnabled()
                || !AetherConfig.PEST_ESP_ENABLED.get()
                || !AetherConfig.PEST_ESP_TRACER.get()
                || !client.options.getCameraType().isFirstPerson()
                || ClientUtils.getCurrentLocation() != MacroState.Location.GARDEN
        ) {
            return;
        }

        List<PestData> pests = getRenderablePests(client);
        if (pests.isEmpty()) {
            return;
        }

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();
        Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        float width = client.getWindow().getGuiScaledWidth();
        float height = client.getWindow().getGuiScaledHeight();
        int tracerColor = argb(255, pestColor(AetherConfig.PEST_ESP_TRACER_COLOR.get()));

        if (!NanoVGManager.isInitialized()) {
            NanoVGManager.init();
        }
        NanoVGManager.beginFrame(width, height);
        NVGRenderer renderer = NanoVGManager.getRenderer();
        try {
            float startX = width * 0.5f;
            float startY = height * 0.5f;
            for (PestData pest : pests) {
                ScreenPoint screenPoint = projectToScreen(pest.position(), cameraPosition, viewProjection, width, height);
                if (screenPoint != null) {
                    renderer.line(startX, startY, screenPoint.x(), screenPoint.y(), 2.0f, tracerColor);
                }
            }
        } finally {
            NanoVGManager.endFrame();
        }
    }

    private static List<PestData> getRenderablePests(Minecraft client) {
        List<PestData> pests = new ArrayList<>();
        for (Entity entity : PestTargetTracker.getLoadedPests(client)) {
            if (entity == null || entity.isRemoved() || isDead(entity)) {
                continue;
            }
            pests.add(new PestData(entity.position(), entity.getBoundingBox()));
        }
        return pests;
    }

    private static ScreenPoint projectToScreen(Vec3 position, Vec3 cameraPosition,
                                                Matrix4f viewProjection, float width, float height) {
        Vector4f clip = new Vector4f(
                (float) (position.x - cameraPosition.x),
                (float) (position.y - cameraPosition.y),
                (float) (position.z - cameraPosition.z),
                1.0f).mul(viewProjection);
        if (clip.w <= 0.001f) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        return new ScreenPoint(
                (ndcX + 1.0f) * 0.5f * width,
                (1.0f - ndcY) * 0.5f * height);
    }

    private static int pestColor(int value) {
        return value & 0x00FFFFFF;
    }

    private static boolean isDead(Entity entity) {
        return entity instanceof LivingEntity living && living.isDeadOrDying();
    }

    private static int argb(int alpha, int rgb) {
        return ARGB.color(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private record PestData(Vec3 position, AABB box) {
    }

    private record ScreenPoint(float x, float y) {
    }
}
