package dev.aether.macro;

import net.minecraft.client.Minecraft;

/**
 * Base lifecycle contract for macros.
 *
 * <p>Specialized macro families should extend this class and keep their
 * domain-specific state and behavior in their own base class.
 */
public abstract class AbstractMacro {

    /** Called once when the macro is enabled. */
    public abstract void onEnable(Minecraft mc);

    /** Called once when the macro is disabled. */
    public abstract void onDisable(Minecraft mc);

    /** Called once per client tick while the macro is active. */
    public abstract void onTick(Minecraft mc);
}
