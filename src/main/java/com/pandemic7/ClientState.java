package com.pandemic7;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Состояние локального игрока на клиенте (приходит с сервера). */
public final class ClientState {
    public static int timer = PandemicData.INTERVAL;
    public static List<Disease> list = new ArrayList<>();
    public static EnumSet<Disease> set = EnumSet.noneOf(Disease.class);

    private ClientState() {}

    public static void set(int t, int[] ids) {
        timer = t;
        List<Disease> l = new ArrayList<>();
        for (int id : ids) {
            Disease d = Disease.byId(id);
            if (d != null && !l.contains(d)) l.add(d);
        }
        EnumSet<Disease> s = EnumSet.noneOf(Disease.class);
        s.addAll(l);
        list = l;
        set = s;
    }

    public static void tickTimer() { if (timer > 0) timer--; }
    public static boolean has(Disease d) { return set.contains(d); }

    public static void clear() {
        timer = PandemicData.INTERVAL;
        list = new ArrayList<>();
        set = EnumSet.noneOf(Disease.class);
    }
}
