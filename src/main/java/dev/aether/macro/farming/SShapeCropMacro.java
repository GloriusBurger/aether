package dev.aether.macro.farming;

import dev.aether.config.AetherConfig;

import java.util.Map;

/** Standard s-shape crop pattern: left row, forward lane, right row, forward lane. */
public class SShapeCropMacro extends AbstractFarmingMacro {
    private final StateCycle states = stateCycle(0.02, 2,
            State.LEFT, State.SWITCHING_LANE, State.RIGHT, State.SWITCHING_LANE);

    @Override
    protected DefaultAngle defaultAngle() {
        return new DefaultAngle(5, 0f);
    }

    @Override
    protected Map<State, StateKeys> stateKeys() {
        boolean holdForward = AetherConfig.MACRO_HOLD_W_WHILE_FARMING.get();
        return Map.of(
                State.LEFT, new StateKeys(true, false, holdForward, false, true, false, false),
                State.RIGHT, new StateKeys(false, true, holdForward, false, true, false, false));
    }

}
