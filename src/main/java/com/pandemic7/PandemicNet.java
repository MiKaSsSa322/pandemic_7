package com.pandemic7;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PandemicNet {
    private static final String PROTO = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PandemicMod.MODID, "main"), () -> PROTO, PROTO::equals, PROTO::equals);

    private PandemicNet() {}

    public static void register() {
        CHANNEL.registerMessage(0, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
    }

    public static void sync(ServerPlayer p) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new SyncPacket(PandemicData.getTimer(p), PandemicData.ids(p)));
    }
}
