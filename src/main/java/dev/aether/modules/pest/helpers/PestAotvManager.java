package dev.aether.modules.pest.helpers;

import dev.aether.config.AetherConfig;

import dev.aether.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import dev.aether.modules.gear.GearManager;
import dev.aether.macro.MacroWorkerThread;
import dev.aether.modules.rotation.RotationManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class PestAotvManager {
    public static volatile boolean isSneakingForAotv = false;
    private static volatile boolean preparationAotvActive = false;
    private static volatile boolean preparationPlotRecovery = false;
    private static volatile double preparationAotvStartY = Double.NaN;
    private static volatile long preparationAotvRequestedAt = 0L;
    private static volatile long preparationAotvStartedAt = 0L;

    public static void resetState() {
        isSneakingForAotv = false;
        preparationAotvActive = false;
        preparationPlotRecovery = false;
        preparationAotvStartY = Double.NaN;
        preparationAotvRequestedAt = 0L;
        preparationAotvStartedAt = 0L;
    }

    public static void startPreparationAotv(Minecraft client) throws InterruptedException {
        preparationAotvActive = true;
        preparationPlotRecovery = false;
        preparationAotvStartY = Double.NaN;
        preparationAotvRequestedAt = System.currentTimeMillis();
        preparationAotvStartedAt = 0L;
        isSneakingForAotv = true;
        performAotvToRoof(client, true);
    }

    public static boolean isPreparationAotvActive() {
        return preparationAotvActive;
    }

    public static boolean consumePreparationPlotRecovery() {
        boolean recovery = preparationPlotRecovery;
        preparationPlotRecovery = false;
        return recovery;
    }

    public static void cancelPreparationAotv(Minecraft client) {
        finishPreparationAotv(client, false);
    }

    /**
     * Drives the roof AOTV used by the shared PRE stage. PestDestroyer keeps
     * ownership of roof rescans that happen later during automatic cleaning.
     */
    public static void updatePreparationAotv(Minecraft client) {
        if (!preparationAotvActive || client == null || client.player == null
                || client.level == null || client.options == null) {
            return;
        }

        if (!Double.isNaN(preparationAotvStartY)) {
            int aotvSlot = GearManager.findAspectOfTheVoidSlot(client);
            if (aotvSlot >= 0 && aotvSlot < 9) {
                ClientUtils.setKeyMappingState(client.options.keyUse, true);
            }

            if (!hasRoofAbove(client)) {
                ClientUtils.sendDebugMessage("Pest PRE stage: AOTV reached open air above the roof.");
                finishPreparationAotv(client, false);
                return;
            }
        } else if (!isSneakingForAotv) {
            finishPreparationAotv(client, false);
            return;
        }

        long now = System.currentTimeMillis();
        boolean startupTimedOut = Double.isNaN(preparationAotvStartY)
                && now - preparationAotvRequestedAt > 10_000L;
        boolean teleportTimedOut = !Double.isNaN(preparationAotvStartY)
                && now - preparationAotvStartedAt > 2000L;
        if (startupTimedOut || teleportTimedOut) {
            ClientUtils.sendDebugMessage("Pest PRE stage: roof AOTV timed out.");
            finishPreparationAotv(client, true);
        }
    }

    public static boolean shouldDoAotvOnCurrentPlot(Minecraft client, String currentInfestedPlot, boolean isSamePlot) {
        if (!AetherConfig.AOTV_TO_ROOF.get())
            return false;

        if (AetherConfig.AOTV_ROOF_PLOTS.get().isEmpty())
            return true;

        boolean inAllowedList = AetherConfig.AOTV_ROOF_PLOTS.get().contains(currentInfestedPlot);
        ClientUtils.sendDebugMessage(inAllowedList ? "plot in list, performing aotv" : "plot not in list, skipping aotv");
        return inAllowedList;
    }

    public static boolean hasRoofAbove(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return false;
        }

        BlockPos base = getRoofScanBase(client);
        for (int i = 2; i <= 20; i++) {
            if (!client.level.getBlockState(base.above(i)).isAir()) {
                return true;
            }
        }
        return false;
    }

    public static BlockPos getRoofScanBase(Minecraft client) {
        return BlockPos.containing(
                client.player.getX(),
                client.player.getY() - 0.3,
                client.player.getZ());
    }

    public static void performAotvToRoof(Minecraft client) throws InterruptedException {
        performAotvToRoof(client, false);
    }

    private static void performAotvToRoof(Minecraft client, boolean preparation) throws InterruptedException {
        if (client.player == null || client.gameMode == null) return;

        // Pre-check: ensure there is a roof (non-air block) above within 2..20 blocks.
        // If there's no roof, abort early to avoid firing AOTV into open sky.
        if (client.level != null && !hasRoofAbove(client)) {
            ClientUtils.sendDebugMessage("PestAotv: no roof detected above player (2..20). Aborting AOTV.");
            isSneakingForAotv = false;
            // Ensure sneak key is released on the client thread
            client.execute(() -> {
                if (client.options != null) ClientUtils.setKeyMappingState(client.options.keyShift, false);
                if (preparation) {
                    finishPreparationAotv(client, false);
                } else {
                    // Advance the PestDestroyer state machine so the destroyer continues
                    // after this aborted AOTV attempt.
                    try {
                        PestDestroyer.completeRoofAotv();
                    } catch (Throwable ignored) {
                    }
                }
            });
            return;
        }

        if (AetherConfig.BREAK_BLOCKS_BEFORE_AOTV.get()) {
            Vec3 breakTarget = Vec3.atCenterOf(client.player.blockPosition().above(2));
            client.execute(() -> ClientUtils.lookAt(client.player, breakTarget));
            ClientUtils.performAttackClick();
            Thread.sleep(100);
        }

        isSneakingForAotv = true;
        Vec3 eyePos = client.player.getEyePosition();
        float upwardYaw = client.player.getYRot() + randomYawOffset();
        float yawRad = (float) Math.toRadians(upwardYaw);
        int baseUpPitch = Math.max(20, Math.min(90, AetherConfig.AOTV_ROOF_PITCH.get()));
        int humanization = Math.max(0, Math.min(15, AetherConfig.AOTV_ROOF_PITCH_HUMANIZATION.get()));
        double randomizedUpPitch = baseUpPitch + ((Math.random() * 2.0) - 1.0) * humanization;
        randomizedUpPitch = Math.max(20.0, Math.min(90.0, randomizedUpPitch));
        float targetMcPitch = (float) -randomizedUpPitch;
        double pitchRad = Math.toRadians(targetMcPitch);

        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3 targetPos = eyePos.add(dirX * 100.0, dirY * 100.0, dirZ * 100.0);
        int rotTime = (int) (AetherConfig.ROTATION_TIME.get() * (0.92 + Math.random() * 0.16));

        RotationManager.initiateRotation(client, targetPos, rotTime);
        ClientUtils.waitForRotationToComplete(targetMcPitch, rotTime);

        int aotvSlot = GearManager.findAspectOfTheVoidSlot(client);
        if (aotvSlot != -1 && aotvSlot < 9) {
            GearManager.swapToAOTVSync(client);

            // Capture Y on the main thread (via execute) so visibility is guaranteed,
            // then fire the normal use key path immediately after.
            ClientUtils.performUseClick(() -> {
                double startY = client.player.getY();
                if (preparation) {
                    preparationAotvStartY = startY;
                    preparationAotvStartedAt = System.currentTimeMillis();
                } else {
                    PestDestroyer.setAotvStartY(startY);
                }
                ClientUtils.sendDebugMessage("[PestAotv] Firing AOTV (slot=" + aotvSlot
                        + ", startY=" + String.format("%.2f", startY) + ")");
            });
            // Worker thread exits immediately - no sleep needed
            } else {
                ClientUtils.sendDebugMessage("[PestAotv] No AOTV found in hotbar. Aborting AOTV sequence.");
                isSneakingForAotv = false;
                client.execute(() -> {
                    if (client.options != null) ClientUtils.setKeyMappingState(client.options.keyShift, false);
                    if (preparation) {
                        finishPreparationAotv(client, false);
                    } else {
                        // Continue the destroyer flow on the client thread after abort.
                        try {
                            if (PestDestroyer.getVacuumSlot() < 0) {
                                PestDestroyer.setState(PestDestroyer.State.EQUIP_VACUUM);
                            } else {
                                PestDestroyer.setState(PestDestroyer.State.CHECK_NEXT);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
    }

    /** Performs the post-roof view reset on the worker after its AOTV phase has ended. */
    public static void rotateDownAfterAotv(Minecraft client) throws InterruptedException {
        if (client == null || client.player == null) {
            return;
        }

        float targetYaw = client.player.getYRot() + randomYawOffset();
        float targetPitch = (float) (-30.0 + Math.random() * 60.0);
        int rotTime = (int) (AetherConfig.ROTATION_TIME.get() * (0.92 + Math.random() * 0.16));
        AtomicBoolean started = new AtomicBoolean(false);
        client.execute(() -> {
            if (client.player == null) {
                return;
            }
            RotationManager.rotateToYawPitch(client, targetYaw, targetPitch, rotTime, true);
            started.set(true);
        });

        long deadline = System.currentTimeMillis() + Math.max(1_500L, rotTime + 1_000L);
        while (!started.get() && System.currentTimeMillis() < deadline) {
            MacroWorkerThread.sleep(10);
        }
        while (started.get() && RotationManager.isRotating() && System.currentTimeMillis() < deadline) {
            MacroWorkerThread.sleep(20);
        }
    }

    private static float randomYawOffset() {
        return (float) (-10.0 + Math.random() * 20.0);
    }

    private static void finishPreparationAotv(Minecraft client, boolean recoverWithPlotTp) {
        if (!preparationAotvActive) {
            return;
        }
        preparationPlotRecovery = recoverWithPlotTp;
        preparationAotvActive = false;
        preparationAotvStartY = Double.NaN;
        preparationAotvRequestedAt = 0L;
        preparationAotvStartedAt = 0L;
        isSneakingForAotv = false;
        if (client != null && client.options != null) {
            ClientUtils.setKeyMappingState(client.options.keyShift, false);
            ClientUtils.setKeyMappingState(client.options.keyUse, false);
        }
    }
}
