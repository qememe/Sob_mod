package ru.qememe.sobmod;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

final class ScenarioBoons {
    private ScenarioBoons() {}

    static void supplyDrop(ScenarioRun run) {
        BlockPos pos = run.offset(run.origin, 7, run.heading);
        BlockPos surface = run.surface(pos);
        if (surface != null && Math.abs(surface.getY() - run.origin.getY()) < 6) {
            pos = surface;
        } else if (!run.loaded(pos) || !run.world.isAirBlock(pos)) {
            pos = run.origin;
        }
        if (run.elapsed < 40) {
            run.cloud(pos.up((40 - run.elapsed) / 5 + 1), EnumParticleTypes.CLOUD, 15, 0.45);
        } else if (run.progress == 0) {
            run.cloud(pos, EnumParticleTypes.END_ROD, 20, 0.65);
            run.message("Припасы · X " + pos.getX() + " Z " + pos.getZ());
            if (run.near(pos, 2.5, 3)) {
                run.progress = 1;
                run.give(new ItemStack(Items.BREAD, 2 + run.level * 2));
                run.give(new ItemStack(Items.ARROW, 4 + run.level * 4));
                run.give(new ItemStack(Blocks.TORCH, 4 + run.level * 2));
            }
        }
    }

    static void starfall(ScenarioRun run) {
        BlockPos pos = run.offset(run.origin, 5 + run.step % 3 * 3, run.heading + run.step * 2.4);
        BlockPos top = run.surface(pos);
        if (top != null && Math.abs(top.getY() - run.origin.getY()) <= 6) {
            pos = top;
        }
        run.mark(pos, 3.5, 35, EnumParticleTypes.END_ROD, hit -> {
            hit.player.addExperience(3 + 3 * hit.level);
            hit.sound(hit.player.getPosition(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5F);
        });
    }

    static void healingSpring(ScenarioRun run) {
        run.ring(run.origin, 4, EnumParticleTypes.HEART);
        if (run.near(run.origin, 4, 2) && run.player.isSneaking()) {
            run.player.heal(1 + run.level);
        }
    }

    static void tailwind(ScenarioRun run) {
        if (run.exposed() && run.player.isSprinting()) {
            run.potion(MobEffects.SPEED, 36, run.level - 1);
            run.cloud(run.player.getPosition(), EnumParticleTypes.CLOUD, 18, 1.2);
        }
    }

    static void updraft(ScenarioRun run) {
        run.ring(run.origin, 4, EnumParticleTypes.CLOUD);
        if (run.near(run.origin, 4, 12) && !run.player.isSneaking()) {
            run.potion(MobEffects.LEVITATION, 25, run.level);
            run.player.fallDistance = 0;
        }
    }

    static void cleansing(ScenarioRun run) {
        if (!run.player.isSneaking()) {
            return;
        }
        run.player.extinguish();
        run.player.removePotionEffect(MobEffects.POISON);
        run.player.removePotionEffect(MobEffects.WITHER);
        run.player.removePotionEffect(MobEffects.BLINDNESS);
        run.potion(MobEffects.RESISTANCE, 50 + run.level * 20, 0);
        run.cloud(run.player.getPosition(), EnumParticleTypes.VILLAGER_HAPPY, 14, 0.9);
    }

    static void forgeBlessing(ScenarioRun run) {
        if (!run.player.isSneaking()) {
            return;
        }
        ItemStack tool = run.player.getHeldItemMainhand();
        if (tool.isItemDamaged()) {
            tool.setItemDamage(Math.max(0, tool.getItemDamage() - 2 * run.level));
            run.sound(run.player.getPosition(), SoundEvents.BLOCK_ANVIL_USE, 1.25F);
            run.cloud(run.player.getPosition(), EnumParticleTypes.CRIT_MAGIC, 12, 0.6);
        }
    }

    static void diversGift(ScenarioRun run) {
        if (run.player.isInWater()) {
            run.potion(MobEffects.WATER_BREATHING, 60 + run.level * 20, 0);
            run.potion(MobEffects.NIGHT_VISION, 240, 0);
            run.potion(MobEffects.HASTE, 55, run.level - 1);
            run.cloud(run.player.getPosition(), EnumParticleTypes.WATER_BUBBLE, 18, 1.4);
        }
    }
}
