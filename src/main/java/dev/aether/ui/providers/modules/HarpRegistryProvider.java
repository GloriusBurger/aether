package dev.aether.ui.providers.modules;

import dev.aether.config.AetherConfig;
import dev.aether.ui.MainGUIRegistry;
import dev.aether.ui.providers.base.AbstractMiningRegistryProvider;
import dev.aether.ui.settings.DropdownSetting;
import dev.aether.ui.settings.ModulesTab;
import dev.aether.ui.settings.SettingGroup;
import dev.aether.ui.settings.SliderSetting;

import java.util.Arrays;
import java.util.List;

public final class HarpRegistryProvider extends AbstractMiningRegistryProvider {
    public HarpRegistryProvider() {
        super(15);
    }

    @Override
    protected ModulesTab.SubTab createSubTab() {
        SettingGroup group = SettingGroup.of(
                "Harp Macro Settings",
                "Automatically plays Melody's Harp",
                () -> AetherConfig.ENABLE_HARP_MACRO.get(),
                v -> {
                    AetherConfig.ENABLE_HARP_MACRO.set(v);
                    AetherConfig.save();
                    if (v) {
                        dev.aether.modules.harp.HarpMacroManager.start(net.minecraft.client.Minecraft.getInstance());
                    } else {
                        dev.aether.modules.harp.HarpMacroManager.stop(net.minecraft.client.Minecraft.getInstance());
                    }
                });
        
        List<String> songs = Arrays.asList(
                "Hymn to the Joy",
                "Frere Jacques",
                "Amazing Grace",
                "Brahms' Lullaby",
                "Happy Birthday to You",
                "Greensleeves",
                "Jeanie with the Light Brown Hair",
                "Minuet",
                "Joy to the World",
                "God Rest Ye Merry, Gentlemen",
                "La Vie en Rose",
                "Pachelbel's Canon",
                "Through the Campfire"
        );
        
        group.add(new DropdownSetting("Song", songs,
                () -> Math.max(0, songs.indexOf(AetherConfig.HARP_SONG.get())),
                v -> {
                    if (v >= 0 && v < songs.size()) {
                        AetherConfig.HARP_SONG.set(songs.get(v));
                        AetherConfig.save();
                    }
                }));

        group.add(new SliderSetting("Click Delay Min", 0f, 300f,
                () -> (float) AetherConfig.HARP_CLICK_DELAY_MIN.get(),
                v -> {
                    AetherConfig.HARP_CLICK_DELAY_MIN.set(Math.round(v));
                    AetherConfig.save();
                })
                .withDecimals(0).withSuffix("ms"));
                
        group.add(new SliderSetting("Click Delay Max", 0f, 300f,
                () -> (float) AetherConfig.HARP_CLICK_DELAY_MAX.get(),
                v -> {
                    AetherConfig.HARP_CLICK_DELAY_MAX.set(Math.round(v));
                    AetherConfig.save();
                })
                .withDecimals(0).withSuffix("ms"));

        return MainGUIRegistry.toggleSubTab(
                "Harp Macro",
                "Automatically plays Melody's Harp",
                () -> AetherConfig.ENABLE_HARP_MACRO.get(),
                v -> {
                    AetherConfig.ENABLE_HARP_MACRO.set(v);
                    AetherConfig.save();
                    if (v) {
                        dev.aether.modules.harp.HarpMacroManager.start(net.minecraft.client.Minecraft.getInstance());
                    } else {
                        dev.aether.modules.harp.HarpMacroManager.stop(net.minecraft.client.Minecraft.getInstance());
                    }
                },
                List.of(group));
    }
}
