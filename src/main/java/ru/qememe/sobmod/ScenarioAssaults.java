package ru.qememe.sobmod;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityEndermite;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.monster.EntityVindicator;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

final class ScenarioAssaults {
    private ScenarioAssaults() {}

    static void undeadSiege(ScenarioRun run) {
        for (int i = 0; i <= run.level; i++) {
            run.spawn(EntityZombie::new, run.heading + i * 2.4 + run.step, 6 + run.level);
        }
        if (run.step >= 2) {
            run.rally(MobEffects.SPEED, run.period);
        }
    }

    static void boneCrossfire(ScenarioRun run) {
        double angle = run.heading + (run.step % 2) * Math.PI / 2;
        run.spawn(world -> armed(new EntitySkeleton(world), Items.BOW), angle, 12);
        run.spawn(world -> armed(new EntitySkeleton(world), Items.BOW), angle + Math.PI, 12);
        if (run.level == 3) {
            run.spawn(world -> armed(new EntitySkeleton(world), Items.BOW), angle + Math.PI / 2, 12);
        }
    }

    static void spiderNest(ScenarioRun run) {
        BlockPos center = run.player.getPosition();
        for (int i = 0; i <= run.level; i++) {
            run.spawn(EntityCaveSpider::new, run.heading + i * 2.4, 5 + i);
        }
        run.mark(center, 3.5 + run.level, 20, EnumParticleTypes.SPELL,
            hit -> hit.potion(MobEffects.SLOWNESS, 100, hit.level));
    }

    static void witchCircle(ScenarioRun run) {
        run.spawn(EntityWitch::new, run.heading + run.step * Math.PI / 2, 7);
        if (run.step > 0) {
            run.mark(run.player.getPosition(), 2 + run.level, 26, EnumParticleTypes.SPELL_WITCH,
                hit -> hit.potion(MobEffects.WEAKNESS, 100, hit.level - 1));
        }
    }

    static void desertPatrol(ScenarioRun run) {
        double heading = run.heading + run.step * 0.48;
        for (int i = 0; i <= run.level; i++) {
            run.spawn(EntityHusk::new, heading + (i - 1) * 0.38, 8 + i);
        }
        if (run.player.isSprinting() && run.near(run.origin, 16, 5)) {
            run.potion(MobEffects.HUNGER, 80, run.level - 1);
        }
    }

    static void frozenArchers(ScenarioRun run) {
        run.spawn(world -> armed(new EntityStray(world), Items.BOW), run.heading + run.step * 2.4, 9);
        run.spawn(EntityZombie::new, run.heading + run.step * 2.4 + 0.65, 6);
        if (run.level >= 2) {
            run.spawn(world -> armed(new EntityStray(world), Items.BOW), run.heading + run.step * 2.4 - 0.65, 9);
        }
    }

    static void axeHunt(ScenarioRun run) {
        run.sound(run.player.getPosition(), SoundEvents.VINDICATION_ILLAGER_AMBIENT, 0.7F);
        run.spawn(world -> armed(new EntityVindicator(world), Items.IRON_AXE), run.heading + run.step * 2.4, 8);
        run.rally(MobEffects.GLOWING, run.period + 20);
        run.rally(MobEffects.SPEED, 50 + run.level * 15);
    }

    static void vexBreach(ScenarioRun run) {
        for (int i = 0; i < run.level; i++) {
            run.spawn(world -> armed(new EntityVex(world), Items.IRON_SWORD), run.heading + i * 2.4 + run.step, 7);
        }
        run.mark(run.player.getPosition(), 3, 20, EnumParticleTypes.SPELL_INSTANT,
            hit -> hit.potion(MobEffects.GLOWING, 90, 0));
    }

    static void netherVanguard(ScenarioRun run) {
        double angle = run.heading + run.step * 0.8;
        run.spawn(world -> armed(new EntityWitherSkeleton(world), Items.STONE_SWORD), angle, 8);
        for (int i = 0; i < run.level; i++) {
            run.spawn(world -> armed(new EntityPigZombie(world), Items.GOLDEN_SWORD),
                angle + (i - 1) * 0.64, 5);
        }
    }

    static void enderBrood(ScenarioRun run) {
        for (int i = 0; i <= run.level; i++) {
            run.spawn(EntityEndermite::new, run.heading + i * 2.4 + run.step, 5);
        }
        BlockPos spot = run.player.getPosition();
        run.mark(spot, 2.5 + run.level, 20, EnumParticleTypes.PORTAL,
            hit -> hit.radial(spot, -0.8, 0.05));
    }

    static void runners(ScenarioRun run) {
        for (int i = 0; i < run.level; i++) {
            run.spawn(world -> {
                EntityZombie runner = new EntityZombie(world);
                runner.setChild(true);
                return runner;
            }, run.heading + run.step * 0.7 + i * 0.5, 10);
        }
        run.rally(MobEffects.SPEED, run.period);
    }

    static void nightCompany(ScenarioRun run) {
        double angle = run.heading + run.step * 2.4;
        run.spawn(world -> armed(new EntityVindicator(world), Items.IRON_AXE), angle, 8);
        run.spawn(world -> armed(new EntitySkeleton(world), Items.BOW), angle + 2.4, 12);
        if (run.level >= 2) {
            run.spawn(EntityWitch::new, angle - 2.4, 11);
        }
        run.rally(MobEffects.RESISTANCE, 45 + run.level * 20);
    }

    private static <T extends EntityLiving> T armed(T mob, Item weapon) {
        mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(weapon));
        mob.setDropChance(EntityEquipmentSlot.MAINHAND, 0);
        return mob;
    }
}
