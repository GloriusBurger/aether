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

public class HarpMacroManager {
    private static volatile boolean isRunning = false;
    private static volatile boolean shouldStop = false;
    private static volatile int runGeneration = 0;
    private static volatile long lastClickTime = 0;
    private static final long[] lastSlotClickTime = new long[54];
    private static final long[] scheduledClicks = new long[54];
    private static final boolean[] blockInSlot = new boolean[54];
    private static final String[] previousItems = new String[54];
    private static volatile String lastGuiTitle = "";
    private static final java.util.Set<String> seenItems = new java.util.concurrent.ConcurrentSkipListSet<>();
    
    // Slots for the click targets in the Harp minigame (Row 5 - slots 37 to 43)
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
                // If it's a 54-slot chest, it's the play GUI (or the song selection GUI which we ignore)
                if (screen.getMenu().slots.size() >= 54) {
                    handlePlaying(client, screen);
                }
            }
        }
    }

    private static void handlePlaying(Minecraft client, AbstractContainerScreen<?> screen) {
        // Look for notes in Row 3 (slots 28 to 34) or Row 4 (slots 37 to 43).
        // A standard approach is to detect falling blocks in the slot just above the quartz block (Row 3).
        // Or in the click block itself (Row 4). Usually, clicking when the note is in row 3 works if ping is high.
        // We will check Row 3 (slots 28-34) and Row 4 (slots 37-43) for falling items (e.g., Clay, Wool).
        
        long now = System.currentTimeMillis();
        
        // Debugger: track all items in Row 3 and Row 4
        for (int i = 28; i <= 43; i++) {
            if (i < screen.getMenu().slots.size()) {
                ItemStack stack = screen.getMenu().slots.get(i).getItem();
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                
                if (!id.equals(previousItems[i])) {
                    if (!id.equals("air") || previousItems[i] != null) {
                        String msg = "[Debug " + (now % 10000) + "] Slot " + i + ": " + previousItems[i] + " -> " + id;
                        dev.aether.util.ClientUtils.sendMessage("\u00A78" + msg);
                        dev.aether.Aether.LOGGER.info(msg);
                    }
                    previousItems[i] = id;
                }
            }
        }
        
        for (int i = 0; i < 7; i++) {
            int clickSlotIndex = CLICK_SLOTS[i];
            int noteSlotIndex = clickSlotIndex - 9; // Row 3
            
            if (noteSlotIndex >= 0 && clickSlotIndex < screen.getMenu().slots.size()) {
                ItemStack noteStack = screen.getMenu().slots.get(noteSlotIndex).getItem();
                ItemStack clickStack = screen.getMenu().slots.get(clickSlotIndex).getItem();
                int delay = AetherConfig.HARP_CLICK_DELAY.get();
                
                // 1. Process pending scheduled clicks
                if (scheduledClicks[clickSlotIndex] != 0 && now >= scheduledClicks[clickSlotIndex]) {
                    String msg = "[Prediction] Clicking string " + (i + 1);
                    dev.aether.util.ClientUtils.sendMessage("\u00A7a" + msg);
                    dev.aether.Aether.LOGGER.info(msg);
                    clickSlot(client, screen, clickSlotIndex);
                    scheduledClicks[clickSlotIndex] = 0; // Clear schedule
                    lastSlotClickTime[clickSlotIndex] = now;
                }
                
                // 2. Detect new notes and schedule them
                if (isNoteBlock(noteStack)) {
                    if (!blockInSlot[clickSlotIndex]) {
                        blockInSlot[clickSlotIndex] = true;
                        
                        // Schedule only if not clicked recently (anti-spam)
                        if (now - lastSlotClickTime[clickSlotIndex] >= 150) {
                            scheduledClicks[clickSlotIndex] = now + delay;
                        }
                    }
                } else {
                    blockInSlot[clickSlotIndex] = false;
                }
            }
        }
    }

    private static boolean isNoteBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        
        // Exclude background elements
        if (item == net.minecraft.world.item.Items.AIR || 
            item == net.minecraft.world.item.Items.QUARTZ_BLOCK || 
            item.toString().contains("glass_pane")) {
            return false;
        }
        
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        
        if (seenItems.add(id)) {
            ClientUtils.sendMessage("\u00A7b[Harp Debug] Saw item: " + id);
        }
        
        return id.contains("wool");
    }

    private static void clickSlot(Minecraft client, AbstractContainerScreen<?> screen, int slot) {
        client.execute(() -> {
            if (client.player == null) return;
            dev.aether.util.ClientUtils.performSlotClick(screen, slot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP);
        });
    }
}
