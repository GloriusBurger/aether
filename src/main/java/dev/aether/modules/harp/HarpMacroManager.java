package dev.aether.modules.harp;

import dev.aether.config.AetherConfig;
import dev.aether.config.ConfigHelpers;
import dev.aether.macro.MacroState;
import dev.aether.macro.MacroStateManager;
import dev.aether.macro.MacroWorkerThread;
import dev.aether.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HarpMacroManager {
    private static volatile boolean isRunning = false;
    private static volatile boolean shouldStop = false;
    private static volatile int runGeneration = 0;
    private static volatile long lastClickTime = 0;
    private static final long[] lastSlotClickTime = new long[54];
    private static final Queue<Long>[] scheduledClicks = new Queue[54];
    static {
        for (int i = 0; i < 54; i++) {
            scheduledClicks[i] = new ConcurrentLinkedQueue<>();
        }
    }
    private static final boolean[][] previousGrid = new boolean[7][4];
    private static final String[] previousItems = new String[54];
    private static volatile String lastGuiTitle = "";
    private static final java.util.Set<String> seenItems = new java.util.concurrent.ConcurrentSkipListSet<>();

    private static final int[] CLICK_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    public static void start(Minecraft client) {
        if (isRunning) {
            ClientUtils.sendMessage("\u00A7cHarp macro is already running.");
            return;
        }
        
        isRunning = true;
        shouldStop = false;
        int generation = ++runGeneration;
        seenItems.clear();
        for (Queue<Long> queue : scheduledClicks) {
            if (queue != null) queue.clear();
        }
        for (int i = 0; i < 7; i++) {
            previousGrid[i] = new boolean[4];
        }
        ClientUtils.sendMessage("\u00A7eStarting Harp macro...");

        MacroWorkerThread.getInstance().submit("HarpMacro", () -> {
            try {
                while (!shouldStop && generation == runGeneration) {
                    if (!AetherConfig.ENABLE_HARP_MACRO.get()) {
                        stop(client);
                        break;
                    }
                    
                    tick(client);
                    MacroWorkerThread.sleep(10); // Loop quickly for accuracy
                }
            } catch (Exception e) {
                ClientUtils.sendMessage("\u00A7cHarp macro error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (generation == runGeneration) {
                    isRunning = false;
                    shouldStop = false;
                }
            }
        });
    }

    public static void stop(Minecraft client) {
        if (!isRunning) return;
        shouldStop = true;
        ClientUtils.sendMessage("\u00A7eStopping Harp macro...");
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.screen == null) return;
        
        if (client.screen instanceof AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString();
            
            if (!title.equals(lastGuiTitle)) {
                ClientUtils.sendMessage("\u00A7b[Harp Debug] Opened GUI: " + title);
                lastGuiTitle = title;
            }
            
            if (title.contains("Melody's Harp")) {
                // Song selection menu - ignored
            } else if (screen.getMenu().slots.size() >= 54) {
                // Playing a song (assuming any 54-slot chest that isn't the selection menu is the play GUI)
                handlePlaying(client, screen);
            }
        }
    }

    private static void handlePlaying(Minecraft client, AbstractContainerScreen<?> screen) {
        
        long now = System.currentTimeMillis();
        
        for (int i = 0; i < 7; i++) {
            int clickSlotIndex = CLICK_SLOTS[i];
            int noteSlotIndex = clickSlotIndex - 9;
            
            if (noteSlotIndex >= 0 && clickSlotIndex < screen.getMenu().slots.size()) {
                ItemStack noteStack = screen.getMenu().slots.get(noteSlotIndex).getItem();
                ItemStack clickStack = screen.getMenu().slots.get(clickSlotIndex).getItem();
                int delay = AetherConfig.HARP_CLICK_DELAY.get();

                // 1. Process pending scheduled clicks
                Queue<Long> clicks = scheduledClicks[clickSlotIndex];
                Long nextClick = clicks.peek();
                if (nextClick != null && now >= nextClick) {
                    if (now - lastClickTime >= 10) { // Tiny stagger to prevent packet burst rejection
                        clicks.poll();
                        clickSlot(client, screen, clickSlotIndex);
                        lastSlotClickTime[clickSlotIndex] = now;
                        lastClickTime = now;
                    }
                }
                
                // 2. Track grid movement to detect notes
                boolean[] currentGrid = new boolean[4];
                for (int r = 0; r < 4; r++) {
                    int slot = CLICK_SLOTS[i] - 9 * (4 - r);
                    if (slot >= 0 && slot < screen.getMenu().slots.size()) {
                        currentGrid[r] = isNoteBlock(screen.getMenu().slots.get(slot).getItem());
                    }
                }
                
                boolean[] prevGrid = previousGrid[i];
                if (prevGrid != null && !java.util.Arrays.equals(currentGrid, prevGrid)) {
                    if (currentGrid[3]) { // Row 3 currently has wool
                        if (!prevGrid[3]) {
                            // Note fell into an empty Row 3
                            clicks.add(now + delay);
                        } else if (prevGrid[2]) {
                            // Back-to-back case: Note B just entered Row 3
                            clicks.add(now + delay);
                        }
                    }
                }
                previousGrid[i] = currentGrid;
            }
        }
    }

    private static boolean isNoteBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        
        if (item == net.minecraft.world.item.Items.AIR || 
            item == net.minecraft.world.item.Items.QUARTZ_BLOCK || 
            item.toString().contains("glass_pane")) {
            return false;
        }
        
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        seenItems.add(id);
        
        return id.contains("wool");
    }

    private static void clickSlot(Minecraft client, AbstractContainerScreen<?> screen, int slot) {
        client.execute(() -> {
            if (client.player == null) return;
            dev.aether.util.ClientUtils.performSlotClick(screen, slot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP);
        });
    }
}
