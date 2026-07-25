package dev.aether.modules.failsafe;

import dev.aether.config.AetherConfig;
import dev.aether.util.ClientUtils;
import dev.aether.util.WindowFocusHelper;
import net.minecraft.client.Minecraft;

public final class FailsafeWindowFocusManager {
    private FailsafeWindowFocusManager() {
    }

    public static void bringWindowToFront() {
        if (!AetherConfig.FAILSAFE_AUTO_ALT_TAB.get()) {
            ClientUtils.sendDebugMessage("FailsafeWindowFocus: auto alt-tab disabled");
            return;
        }
        focusWindowNow();
    }

    public static void focusWindowNow() {
        if (Minecraft.getInstance() == null) {
            ClientUtils.sendDebugMessage("FailsafeWindowFocus: client is null");
            return;
        }
        WindowFocusHelper.focusMinecraftWindow("FailsafeWindowFocus");
    }
}
