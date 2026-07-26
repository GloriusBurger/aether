package dev.aether.ui.providers.base;

import dev.aether.ui.MainGUIRegistry;
import dev.aether.ui.MainGUIRegistryProvider;
import dev.aether.ui.settings.ModulesTab;

public abstract class AbstractKeybindsRegistryProvider implements MainGUIRegistryProvider {
    private final int order;

    protected AbstractKeybindsRegistryProvider(int order) {
        this.order = order;
    }

    @Override
    public final void register(MainGUIRegistry.Registrar registrar) {
        registrar.registerKeybinds(order, createSubTab());
    }

    protected abstract ModulesTab.SubTab createSubTab();
}
