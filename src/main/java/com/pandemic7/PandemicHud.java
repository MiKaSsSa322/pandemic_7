package com.pandemic7;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/** Компактный HUD слева сверху: таймер + список болезней с иконками. */
public final class PandemicHud {
    private PandemicHud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.renderDebug) return;
        Font f = mc.font;

        List<Disease> list = ClientState.list;
        int sec = Math.max(0, ClientState.timer / 20);
        String head = "Новая болезнь через " + String.format("%d:%02d", sec / 60, sec % 60);
        int named = Math.min(8, list.size());
        int older = Math.max(0, list.size() - named);
        int rowsOlder = (older + 11) / 12;

        int w = Math.max(f.width(head), 112) + 8;
        for (int i = 0; i < named; i++) {
            Disease d = list.get(list.size() - 1 - i);
            w = Math.max(w, 14 + f.width(d.getName()) + 8);
        }
        int h = 3 + 10 + 6 + 3;
        if (!list.isEmpty()) h += 11 + named * 12 + (older > 0 ? 1 + rowsOlder * 9 : 0);

        int x = 4, y = 4;
        g.fill(x, y, x + w, y + h, 0x77000000);
        int cx = x + 4, cy = y + 3;

        g.drawString(f, head, cx, cy, sec < 30 ? 0xFFFF5555 : 0xFFFFFFFF, true);
        cy += 10;

        int bw = w - 8;
        g.fill(cx, cy, cx + bw, cy + 3, 0xFF333333);
        float frac = Math.min(1f, ClientState.timer / (float) PandemicData.INTERVAL);
        g.fill(cx, cy, cx + (int) (bw * frac), cy + 3, 0xFFB03030);
        cy += 6;

        if (!list.isEmpty()) {
            g.drawString(f, "Болезни: " + list.size() + "/50", cx, cy, 0xFFAAAAAA, true);
            cy += 11;
            for (int i = 0; i < named; i++) {
                Disease d = list.get(list.size() - 1 - i);
                icon(g, f, cx, cy, d, 10);
                g.drawString(f, d.getName(), cx + 14, cy + 1, 0xFF000000 | d.getCat().color, true);
                cy += 12;
            }
            if (older > 0) {
                cy += 1;
                for (int i = 0; i < older; i++) {
                    Disease d = list.get(i);
                    int col = i % 12, row = i / 12;
                    int ix = cx + col * 9, iy = cy + row * 9;
                    g.fill(ix, iy, ix + 7, iy + 7, 0xFF000000 | d.getCat().color);
                }
            }
        }
    }

    private static void icon(GuiGraphics g, Font f, int x, int y, Disease d, int size) {
        g.fill(x, y, x + size, y + size, 0xFF000000 | d.getCat().color);
        g.renderOutline(x, y, size, size, 0xFF000000);
        String n = String.valueOf(d.getId());
        float sc = 0.6f;
        float tw = f.width(n) * sc;
        g.pose().pushPose();
        g.pose().translate(x + (size - tw) / 2f, y + (size - 8 * sc) / 2f + 0.2f, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(f, n, 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
    }
}
