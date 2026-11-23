package com.pandemic7;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.UUID;

/** Серверная часть эффектов болезней. Клиентская часть (камера, звуки, движение) — в ClientEffects. */
public final class DiseaseEffects {
    private DiseaseEffects() {}

    private static final MobEffect[] CHAOS_POOL = {
            MobEffects.MOVEMENT_SPEED, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.JUMP, MobEffects.DIG_SPEED,
            MobEffects.DIG_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.DAMAGE_BOOST, MobEffects.CONFUSION,
            MobEffects.HUNGER, MobEffects.GLOWING, MobEffects.NIGHT_VISION, MobEffects.LEVITATION,
            MobEffects.SLOW_FALLING, MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.UNLUCK, MobEffects.LUCK
    };

    // ---------- атрибуты ----------
    private static UUID uid(Disease d, String k) {
        return UUID.nameUUIDFromBytes(("pandemic7:" + d.name() + ":" + k).getBytes(StandardCharsets.UTF_8));
    }

    private static void mod(ServerPlayer p, Attribute a, Disease d, String k, double amt,
                            AttributeModifier.Operation op, boolean on) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        UUID id = uid(d, k);
        AttributeModifier ex = inst.getModifier(id);
        if (on && ex == null) {
            inst.addTransientModifier(new AttributeModifier(id, "pandemic7." + d.name() + "." + k, amt, op));
        } else if (!on && ex != null) {
            inst.removeModifier(id);
        }
    }

    public static void refreshAttributes(ServerPlayer p, EnumSet<Disease> s) {
        mod(p, Attributes.MAX_HEALTH, Disease.ANEMIA, "hp", -4.0, AttributeModifier.Operation.ADDITION, s.contains(Disease.ANEMIA));
        mod(p, Attributes.ATTACK_DAMAGE, Disease.MUSCLE_WEAKNESS, "dmg", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, s.contains(Disease.MUSCLE_WEAKNESS));
        mod(p, Attributes.MOVEMENT_SPEED, Disease.CHRONIC_FATIGUE, "spd", -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL, s.contains(Disease.CHRONIC_FATIGUE));
        mod(p, Attributes.MOVEMENT_SPEED, Disease.PARALYSIS, "spd", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, s.contains(Disease.PARALYSIS));
        mod(p, Attributes.ARMOR, Disease.CRYSTAL, "armor", 4.0, AttributeModifier.Operation.ADDITION, s.contains(Disease.CRYSTAL));
        mod(p, Attributes.MOVEMENT_SPEED, Disease.CRYSTAL, "spd", -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL, s.contains(Disease.CRYSTAL));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ---------- утилиты ----------
    private static void eff(ServerPlayer p, MobEffect e, int dur, int amp, boolean icon) {
        MobEffectInstance c = p.getEffect(e);
        if (c == null || c.getAmplifier() < amp || c.getDuration() < dur / 2) {
            p.addEffect(new MobEffectInstance(e, dur, amp, false, false, icon));
        }
    }

    private static void msg(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s), true);
    }

    // ---------- основной тик (сервер) ----------
    public static void tick(ServerPlayer p, EnumSet<Disease> s) {
        int t = p.tickCount;
        RandomSource r = p.getRandom();
        ServerLevel lvl = p.serverLevel();
        boolean sec = t % 20 == 0;
        PlayerState st = PlayerState.of(p);

        if (t % 100 == 0) refreshAttributes(p, s);

        // 2 Гиперметаболизм
        if (s.contains(Disease.HYPERMETABOLISM) && t % 10 == 0) p.causeFoodExhaustion(0.12f);

        // 5 Слабые лёгкие
        if (s.contains(Disease.WEAK_LUNGS)) {
            if (p.isSprinting() && t % 60 == 0) {
                eff(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true);
                p.setSprinting(false);
            }
            if (p.isEyeInFluid(FluidTags.WATER) && p.getAirSupply() > 2) p.setAirSupply(p.getAirSupply() - 1);
        }

        // 7 Лихорадка
        if (s.contains(Disease.FEVER) && t % 600 == 300) {
            eff(p, MobEffects.WEAKNESS, 200, 0, true);
            eff(p, MobEffects.CONFUSION, 120, 0, true);
        }

        // 8 Нарушение терморегуляции
        if (s.contains(Disease.THERMO) && t % 10 == 0) {
            float temp = lvl.getBiome(p.blockPosition()).value().getBaseTemperature();
            if (temp < 0.3f) {
                p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, p.getTicksFrozen() + 20));
            } else if (temp >= 1.5f && t % 100 == 0 && !p.isInWaterOrRain()) {
                p.hurt(lvl.damageSources().onFire(), 1f);
            }
        }

        // 9 Хроническая усталость
        if (s.contains(Disease.CHRONIC_FATIGUE) && t % 40 == 0) eff(p, MobEffects.DIG_SLOWDOWN, 100, 0, false);

        // 10 Фантомная боль
        if (s.contains(Disease.PHANTOM_PAIN) && sec && r.nextInt(40) == 0 && p.getHealth() > 3f) {
            p.hurt(lvl.damageSources().magic(), 1f);
        }

        // 11 Слепота
        if (s.contains(Disease.BLINDNESS) && sec && r.nextInt(60) == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false, true));
        }

        // 16 Дезориентация
        if (s.contains(Disease.DISORIENTATION) && sec && r.nextInt(80) == 0) {
            eff(p, MobEffects.CONFUSION, 140, 0, true);
        }

        // 22 Амнезия
        if (s.contains(Disease.AMNESIA) && sec && r.nextInt(100) == 0) {
            int a = r.nextInt(9), b = r.nextInt(9);
            if (a != b) {
                ItemStack tmp = p.getInventory().getItem(a);
                p.getInventory().setItem(a, p.getInventory().getItem(b));
                p.getInventory().setItem(b, tmp);
                msg(p, "§7Вы забыли, где что лежало...");
            }
        }

        // 23 Дежавю
        if (s.contains(Disease.DEJAVU)) {
            if (sec) {
                st.history.addLast(p.position());
                while (st.history.size() > 6) st.history.removeFirst();
            }
            if (sec && r.nextInt(100) == 0 && st.history.size() >= 5) {
                Vec3 v = st.history.peekFirst();
                Vec3 delta = v.subtract(p.position());
                if (lvl.noCollision(p, p.getBoundingBox().move(delta))) {
                    p.teleportTo(v.x, v.y, v.z);
                    p.fallDistance = 0f;
                    msg(p, "§dДежавю...");
                }
                st.history.clear();
            }
        } else {
            st.history.clear();
        }

        // 24 Панический синдром
        if (s.contains(Disease.PANIC)) {
            if (st.panic > 0) {
                st.panic--;
                if (st.panic % 12 == 0) Sounds.notify(p, "entity.warden.heartbeat", 1f, 1f);
            } else if (sec && r.nextInt(150) == 0) {
                st.panic = 120;
                eff(p, MobEffects.MOVEMENT_SLOWDOWN, 120, 1, true);
                eff(p, MobEffects.CONFUSION, 120, 0, true);
                eff(p, MobEffects.WEAKNESS, 120, 0, true);
                msg(p, "§cПриступ паники!");
            }
        }

        // 25 Мания преследования
        if (s.contains(Disease.PERSECUTION) && t % 40 == 0) {
            for (Monster m : lvl.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(28))) {
                if (m.getTarget() == null) m.setTarget(p);
            }
        }

        // 26 Нарушение концентрации
        if (s.contains(Disease.CONCENTRATION) && sec && r.nextInt(25) == 0) {
            int slot = r.nextInt(9);
            p.getInventory().selected = slot;
            p.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }

        // 27 Пространственная путаница
        if (s.contains(Disease.SPATIAL) && sec && r.nextInt(120) == 0) {
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot() + 90f + r.nextInt(180), p.getXRot());
        }

        // 28 Потеря ориентации во времени
        if (s.contains(Disease.TIME_LOSS) && t % 5 == 0) {
            long off = (long) (Math.sin(t / 240.0) * 6000.0);
            p.connection.send(new ClientboundSetTimePacket(lvl.getGameTime(), lvl.getDayTime() + off,
                    lvl.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        }

        // 30 Кошмарное восприятие
        if (s.contains(Disease.NIGHTMARE) && sec && r.nextInt(80) == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false, true));
        }

        // 36 Слабость пальцев
        if (s.contains(Disease.FINGER_WEAKNESS) && sec && r.nextInt(60) == 0) {
            ItemStack held = p.getMainHandItem();
            if (!held.isEmpty()) {
                ItemStack one = held.split(1);
                p.drop(one, false);
                msg(p, "§7Предмет выскользнул из пальцев...");
            }
        }

        // 41 Синдром двойника
        if (s.contains(Disease.DOUBLE)) {
            if (sec && r.nextInt(240) == 0) spawnDouble(p, lvl, r);
            if (t % 200 == 0) {
                for (Zombie z : lvl.getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(80),
                        z -> z.getTags().contains("pandemic_double"))) {
                    if (z.tickCount > 6000) z.discard();
                }
            }
        }

        // 42 Обратная физиология
        if (s.contains(Disease.REVERSE) && sec) {
            if (p.hasEffect(MobEffects.REGENERATION)) {
                p.removeEffect(MobEffects.REGENERATION);
                p.hurt(lvl.damageSources().magic(), 2f);
            }
            if ((p.hasEffect(MobEffects.POISON) || p.hasEffect(MobEffects.WITHER)) && p.getHealth() < p.getMaxHealth()) {
                p.heal(1.5f);
            }
        }

        // 43 Кровь времени
        if (s.contains(Disease.TIME_BLOOD) && sec) {
            boolean fast = ((t / 400) % 2) == 0;
            if (fast) {
                eff(p, MobEffects.MOVEMENT_SPEED, 60, 1, true);
                eff(p, MobEffects.DIG_SPEED, 60, 1, true);
            } else {
                eff(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true);
                eff(p, MobEffects.DIG_SLOWDOWN, 60, 1, true);
            }
        }

        // 44 Пустотная инфекция
        if (s.contains(Disease.VOID_INFECTION) && sec) {
            lvl.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 12, 0.6, 0.9, 0.6, 0.3);
            if (r.nextInt(60) == 0
                    && lvl.getEntitiesOfClass(Endermite.class, p.getBoundingBox().inflate(16)).size() < 3) {
                Endermite m = EntityType.ENDERMITE.create(lvl);
                if (m != null) {
                    m.moveTo(p.getX() + r.nextInt(7) - 3, p.getY(), p.getZ() + r.nextInt(7) - 3, r.nextFloat() * 360f, 0f);
                    if (lvl.noCollision(m)) lvl.addFreshEntity(m);
                }
            }
            if (r.nextInt(100) == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false, true));
            }
        }

        // 46 Синдром невезения
        if (s.contains(Disease.BAD_LUCK) && t % 40 == 0) eff(p, MobEffects.UNLUCK, 100, 2, false);

        // 47 Поглощение энергии
        if (s.contains(Disease.ENERGY_DRAIN) && t % 40 == 0) {
            if (p.totalExperience > 0) p.giveExperiencePoints(-1);
            else p.causeFoodExhaustion(0.6f);
        }

        // 50 Пандемический хаос
        if (s.contains(Disease.CHAOS) && t % 600 == 0) {
            for (int i = 0; i < 2; i++) {
                MobEffect e = CHAOS_POOL[r.nextInt(CHAOS_POOL.length)];
                int dur = (e == MobEffects.LEVITATION || e == MobEffects.BLINDNESS) ? 60 : 300;
                p.addEffect(new MobEffectInstance(e, dur, r.nextInt(2), false, true, true));
            }
            msg(p, "§5Хаос...");
        }
    }

    private static void spawnDouble(ServerPlayer p, ServerLevel lvl, RandomSource r) {
        boolean exists = !lvl.getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(48),
                z -> z.getTags().contains("pandemic_double")).isEmpty();
        if (exists) return;
        Zombie z = EntityType.ZOMBIE.create(lvl);
        if (z == null) return;
        double ang = r.nextDouble() * Math.PI * 2;
        double x = p.getX() + Math.cos(ang) * 14;
        double zz = p.getZ() + Math.sin(ang) * 14;
        int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) zz);
        z.moveTo(x, y, zz, r.nextFloat() * 360f, 0f);
        z.addTag("pandemic_double");
        z.setCustomName(p.getName());
        z.setCustomNameVisible(true);
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.getOrCreateTag().putString("SkullOwner", p.getGameProfile().getName());
        z.setItemSlot(EquipmentSlot.HEAD, head);
        z.setDropChance(EquipmentSlot.HEAD, 0f);
        for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack a = p.getItemBySlot(es);
            if (!a.isEmpty()) {
                z.setItemSlot(es, a.copy());
                z.setDropChance(es, 0f);
            }
        }
        z.setTarget(p);
        z.setPersistenceRequired();
        lvl.addFreshEntity(z);
        msg(p, "§4Кто-то идёт за вами... у него ваше лицо.");
    }
}
