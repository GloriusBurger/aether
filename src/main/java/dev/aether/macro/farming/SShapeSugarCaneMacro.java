package dev.aether.macro.farming;

/** Farms alternating A/S rows. */
public class SShapeSugarCaneMacro extends AbstractFarmingMacro {
    private final StateCycle rows = stateCycle(0.005, 2, State.LEFT, State.BACKWARD);

    @Override
    protected DefaultAngle defaultAngle() {
        return new DefaultAngle(0f, 45f);
    }

}
