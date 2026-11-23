package com.pandemic7;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Хранение болезней и таймера в persistent-данных игрока (сохраняется вместе с миром). */
public final class PandemicData {
    public static final int INTERVAL = 7 * 60 * 20;
    private static final String KEY = "pandemic7";

    private PandemicData() {}

    public static CompoundTag root(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG, 10)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = pd.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEY, 10)) persisted.put(KEY, new CompoundTag());
        return persisted.getCompound(KEY);
    }

    public static int getTimer(Player p) {
        CompoundTag r = root(p);
        return r.contains("timer", 3) ? r.getInt("timer") : INTERVAL;
    }

    public static void setTimer(Player p, int v) { root(p).putInt("timer", v); }

    public static List<Disease> getList(Player p) {
        List<Disease> out = new ArrayList<>();
        for (int id : root(p).getIntArray("diseases")) {
            Disease d = Disease.byId(id);
            if (d != null && !out.contains(d)) out.add(d);
        }
        return out;
    }

    public static EnumSet<Disease> getSet(Player p) {
        EnumSet<Disease> s = EnumSet.noneOf(Disease.class);
        s.addAll(getList(p));
        return s;
    }

    public static void setList(Player p, List<Disease> l) {
        root(p).putIntArray("diseases", l.stream().mapToInt(Disease::getId).toArray());
    }

    public static boolean add(Player p, Disease d) {
        List<Disease> l = getList(p);
        if (l.contains(d)) return false;
        l.add(d);
        setList(p, l);
        return true;
    }

    public static int[] ids(Player p) {
        return getList(p).stream().mapToInt(Disease::getId).toArray();
    }
}
