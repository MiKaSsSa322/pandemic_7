package com.pandemic7;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

public final class Sounds {
    private Sounds() {}

    public static SoundEvent get(String id) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
    }

    public static void notify(ServerPlayer p, String id, float vol, float pitch) {
        SoundEvent s = get(id);
        if (s != null) p.playNotifySound(s, SoundSource.PLAYERS, vol, pitch);
    }
}
