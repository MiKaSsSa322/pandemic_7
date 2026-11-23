package com.pandemic7;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncPacket(int timer, int[] ids) {
    public static void encode(SyncPacket m, FriendlyByteBuf b) {
        b.writeInt(m.timer);
        b.writeVarIntArray(m.ids);
    }

    public static SyncPacket decode(FriendlyByteBuf b) {
        return new SyncPacket(b.readInt(), b.readVarIntArray());
    }

    public static void handle(SyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientState.set(m.timer, m.ids));
        ctx.get().setPacketHandled(true);
    }
}
