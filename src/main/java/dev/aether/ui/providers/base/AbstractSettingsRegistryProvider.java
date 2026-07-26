package dev.aether.ui.providers.base;

import dev.aether.ui.MainGUIRegistry;
import dev.aether.ui.MainGUIRegistryProvider;
import dev.aether.ui.settings.ModulesTab;

public abstract class AbstractSettingsRegistryProvider implements MainGUIRegistryProvider {
    private final int order;

    protected AbstractSettingsRegistryProvider(int order) {
        this.order = order;
    }

    @Override
    public final void register(MainGUIRegistry.Registrar registrar) {
        registrar.registerSettings(order, createSubTab());
    }

    protected abstract ModulesTab.SubTab createSubTab();
}
