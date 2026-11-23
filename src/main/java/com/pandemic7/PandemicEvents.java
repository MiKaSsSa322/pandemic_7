package com.pandemic7;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = PandemicMod.MODID)
public final class PandemicEvents {
    private PandemicEvents() {}

    // ---------- таймер и заражение ----------
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        if (!(e.player instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()) return;

        int timer = PandemicData.getTimer(p) - 1;
        if (timer <= 0) {
            timer = PandemicData.INTERVAL;
            PandemicData.setTimer(p, timer);
            infect(p);
        } else {
            PandemicData.setTimer(p, timer);
        }
        EnumSet<Disease> s = PandemicData.getSet(p);
        if (p.tickCount % 20 == 0) PandemicNet.sync(p);
        if (!s.isEmpty()) DiseaseEffects.tick(p, s);
    }

    public static boolean infect(ServerPlayer p) {
        List<Disease> cur = PandemicData.getList(p);
        List<Disease> pool = new ArrayList<>(List.of(Disease.values()));
        pool.removeAll(cur);
        if (pool.isEmpty()) {
            p.sendSystemMessage(Component.literal("§7Вы переболели всем. Хуже уже не будет... или будет?"));
            return false;
        }
        return infect(p, pool.get(p.getRandom().nextInt(pool.size())));
    }

    public static boolean infect(ServerPlayer p, Disease d) {
        if (!PandemicData.add(p, d)) return false;
        p.sendSystemMessage(Component.literal("§c☣ Новая болезнь: §f" + d.getName() + " §7— " + d.getDesc()));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§c" + d.getName())));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7" + d.getDesc())));
        Sounds.notify(p, "entity.elder_guardian.curse", 0.6f, 1f);
        DiseaseEffects.refreshAttributes(p, PandemicData.getSet(p));
        PandemicNet.sync(p);
        return true;
    }

    // ---------- жизненный цикл ----------
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            DiseaseEffects.refreshAttributes(p, PandemicData.getSet(p));
            PandemicNet.sync(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            DiseaseEffects.refreshAttributes(p, PandemicData.getSet(p));
            PandemicNet.sync(p);
        }
    }

    @SubscribeEvent
    public static void onDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PandemicNet.sync(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        PlayerState.MAP.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        var old = PandemicData.root(e.getOriginal());
        var nw = PandemicData.root(e.getEntity());
        nw.putInt("timer", old.contains("timer", 3) ? old.getInt("timer") : PandemicData.INTERVAL);
        nw.putIntArray("diseases", old.getIntArray("diseases"));
    }

    // ---------- пассивные эффекты ----------
    private static boolean has(Player p, Disease d) {
        return p.level().isClientSide ? ClientState.has(d) : PandemicData.getSet(p).contains(d);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && has(p, Disease.FRAGILE_BONES)) {
            e.setDamageMultiplier(e.getDamageMultiplier() * 2f);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && has(p, Disease.CRYSTAL)) {
            Sounds.notify(p, "block.amethyst_block.break", 1f, 1f);
            if (e.getAmount() >= 4f) e.setAmount(e.getAmount() + 1f);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && has(p, Disease.BAD_LUCK)
                && p.getRandom().nextFloat() < 0.4f) {
            e.getDrops().clear();
        }
    }

    // ---------- ампутации ----------
    static boolean blocked(Player p, InteractionHand hand) {
        Set<Disease> s = p.level().isClientSide ? ClientState.set : PandemicData.getSet(p);
        if (s.isEmpty()) return false;
        boolean right = hand == InteractionHand.MAIN_HAND
                ? p.getMainArm() == HumanoidArm.RIGHT
                : p.getMainArm() == HumanoidArm.LEFT;
        return right ? s.contains(Disease.AMPUTATION_RIGHT) : s.contains(Disease.AMPUTATION_LEFT);
    }

    @SubscribeEvent
    public static void onRightItem(PlayerInteractEvent.RightClickItem e) {
        if (blocked(e.getEntity(), e.getHand())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightBlock(PlayerInteractEvent.RightClickBlock e) {
        if (blocked(e.getEntity(), e.getHand())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (blocked(e.getEntity(), e.getHand())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        if (blocked(e.getEntity(), e.getHand())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (blocked(e.getEntity(), InteractionHand.MAIN_HAND)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (blocked(e.getEntity(), InteractionHand.MAIN_HAND)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (blocked(e.getEntity(), InteractionHand.MAIN_HAND)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        Player p = e.getPlayer();
        if (blocked(p, InteractionHand.MAIN_HAND)) {
            e.setCanceled(true);
            return;
        }
        if (has(p, Disease.BAD_LUCK) && !p.isCreative() && p.getRandom().nextFloat() < 0.15f
                && e.getLevel() instanceof ServerLevel sl) {
            e.setCanceled(true);
            sl.destroyBlock(e.getPos(), false, p);
            p.displayClientMessage(Component.literal("§7Не повезло — блок рассыпался впустую."), true);
        }
    }

    // ---------- команды ----------
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("pandemic")
                .then(Commands.literal("list").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    List<Disease> l = PandemicData.getList(p);
                    p.sendSystemMessage(Component.literal("§eВаши болезни (" + l.size() + "/50):"));
                    for (Disease d : l) {
                        p.sendSystemMessage(Component.literal("§f" + d.getId() + ". " + d.getName() + " §7— " + d.getDesc()));
                    }
                    return 1;
                }))
                .then(Commands.literal("catalog").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (Disease d : Disease.values()) {
                        p.sendSystemMessage(Component.literal("§f" + d.getId() + ". " + d.getName() + " §7— " + d.getDesc()));
                    }
                    return 1;
                }))
                .then(Commands.literal("add").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, 50)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Disease d = Disease.byId(IntegerArgumentType.getInteger(c, "id"));
                            return d != null && infect(p, d) ? 1 : 0;
                        })))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PandemicData.setList(p, new ArrayList<>());
                    DiseaseEffects.refreshAttributes(p, PandemicData.getSet(p));
                    PandemicNet.sync(p);
                    return 1;
                }))
                .then(Commands.literal("timer").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            PandemicData.setTimer(p, IntegerArgumentType.getInteger(c, "seconds") * 20);
                            PandemicNet.sync(p);
                            return 1;
                        }))));
    }
}
