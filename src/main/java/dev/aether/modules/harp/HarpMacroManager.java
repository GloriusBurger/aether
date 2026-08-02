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
        
        MacroStateManager.setCurrentState(MacroState.State.HARPING);

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
                    if (MacroStateManager.getCurrentState() == MacroState.State.HARPING) {
                        MacroStateManager.setCurrentState(MacroState.State.OFF);
                    }
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
                // Song selection menu
                handleSongSelection(client, screen);
                MacroWorkerThread.sleep(500); // Wait after a selection check
            } else if (screen.getMenu().slots.size() >= 54) {
                // Playing a song (assuming any 54-slot chest that isn't the selection menu is the play GUI)
                handlePlaying(client, screen);
            }
        }
    }

    private static void handleSongSelection(Minecraft client, AbstractContainerScreen<?> screen) {
        String targetSong = AetherConfig.HARP_SONG.get();
        if (targetSong == null || targetSong.isEmpty()) return;
        
        // Ensure the string matches the item names in the game by removing formatting and checking equality/contains
        String targetNormalized = dev.aether.util.TablistUtils.stripColors(targetSong).toLowerCase();
        
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;
            
            Component hoverNameComp = stack.getHoverName();
            if (hoverNameComp == null) continue;
            
            String itemName = dev.aether.util.TablistUtils.stripColors(hoverNameComp.getString()).toLowerCase();
            if (itemName.equals(targetNormalized) || itemName.contains(targetNormalized)) {
                // Found the song, click it!
                int delay = ConfigHelpers.getRandomizedDelay(AetherConfig.HARP_CLICK_DELAY_MIN.get(), AetherConfig.HARP_CLICK_DELAY_MAX.get());
                MacroWorkerThread.sleep(delay);
                
                ClientUtils.sendMessage("\u00A7aSelecting song: " + targetSong);
                clickSlot(client, screen, slot.index);
                MacroWorkerThread.sleep(1000); // Wait for the new GUI to load
                return;
            }
        }
    }

    private static void handlePlaying(Minecraft client, AbstractContainerScreen<?> screen) {
        // Look for notes in Row 3 (slots 28 to 34) or Row 4 (slots 37 to 43).
        // A standard approach is to detect falling blocks in the slot just above the quartz block (Row 3).
        // Or in the click block itself (Row 4). Usually, clicking when the note is in row 3 works if ping is high.
        // We will check Row 3 (slots 28-34) and Row 4 (slots 37-43) for falling items (e.g., Clay, Wool).
        
        for (int i = 0; i < 7; i++) {
            int clickSlotIndex = CLICK_SLOTS[i];
            int noteSlotIndex = clickSlotIndex - 9; // Row 3
            
            if (noteSlotIndex >= 0 && clickSlotIndex < screen.getMenu().slots.size()) {
                ItemStack noteStack = screen.getMenu().slots.get(noteSlotIndex).getItem();
                ItemStack clickStack = screen.getMenu().slots.get(clickSlotIndex).getItem();
                
                if (isNoteBlock(noteStack) || isNoteBlock(clickStack)) {
                    // Click!
                    long now = System.currentTimeMillis();
                    if (now - lastClickTime < 50) return; // Prevent spamming too many clicks globally within 50ms
                    
                    int delay = ConfigHelpers.getRandomizedDelay(AetherConfig.HARP_CLICK_DELAY_MIN.get(), AetherConfig.HARP_CLICK_DELAY_MAX.get());
                    MacroWorkerThread.sleep(delay);
                    
                    dev.aether.util.ClientUtils.sendMessage("\u00A7aClicking string " + (i + 1) + " (Slot " + clickSlotIndex + ")");
                    clickSlot(client, screen, clickSlotIndex); // Click the corresponding bottom slot
                    lastClickTime = System.currentTimeMillis();
                    return; // Only process one click per tick to mimic human
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
        
        return id.contains("wool") || id.contains("clay") || id.contains("terracotta");
    }

    private static void clickSlot(Minecraft client, AbstractContainerScreen<?> screen, int slot) {
        client.execute(() -> {
            if (client.player == null) return;
            dev.aether.util.ClientUtils.performSlotClick(screen, slot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP);
        });
    }
}
