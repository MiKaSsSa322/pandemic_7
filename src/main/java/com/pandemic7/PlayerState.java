package com.pandemic7;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Временное (не сохраняемое) серверное состояние игрока. */
final class PlayerState {
    static final Map<UUID, PlayerState> MAP = new HashMap<>();

    static PlayerState of(Player p) {
        return MAP.computeIfAbsent(p.getUUID(), k -> new PlayerState());
    }

    final ArrayDeque<Vec3> history = new ArrayDeque<>();
    int panic;
}
