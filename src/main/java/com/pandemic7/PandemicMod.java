package com.pandemic7;

import net.minecraftforge.fml.common.Mod;

@Mod(PandemicMod.MODID)
public class PandemicMod {
    public static final String MODID = "pandemic7";

    public PandemicMod() {
        PandemicNet.register();
    }
}
