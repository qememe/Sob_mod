package ru.qememe.sobmod;

import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

final class ScenarioTrials {
    private ScenarioTrials() {}

    static void evacuation(ScenarioRun run) {
        int radius = 18 + 6 * run.level;
        run.ring(run.origin, radius, EnumParticleTypes.FLAME);
        boolean outside = DisasterEvents.horizontalDistanceSq(run.player, run.origin) >= radius * radius;
        run.message(outside ? "Ты за границей зоны. Оставайся снаружи."
            : "Эвакуация · выйди за радиус " + radius + " блоков");
        if (run.elapsed + run.period >= run.duration) {
            if (outside) {
                run.give(new ItemStack(Items.IRON_INGOT, run.level * 2));
            } else {
                run.hurt(DamageSource.causeExplosionDamage((net.minecraft.entity.EntityLivingBase) null), 4 + run.level * 2);
            }
        }
    }

    static void holdout(ScenarioRun run) {
        run.ring(run.origin, 5, EnumParticleTypes.VILLAGER_HAPPY);
        if (run.near(run.origin, 5, 3) && run.player.onGround) {
            run.progress++;
        }
        int needed = run.duration / run.period - run.level;
        run.message("Удержание · " + Math.min(run.progress, needed) + "/" + needed + " проверок внутри круга");
        if (run.elapsed + run.period >= run.duration && run.progress >= needed) {
            run.give(new ItemStack(Items.GOLD_INGOT, run.level));
        }
    }

    static void highGround(ScenarioRun run) {
        int height = 3 + run.level * 2;
        int goalY = Math.min(run.world.getActualHeight() - 2, run.origin.getY() + height);
        run.message("Подъём · высота " + run.player.getPosition().getY() + " / " + goalY);
        if (run.elapsed + run.period >= run.duration) {
            if (run.player.posY >= goalY && run.player.onGround && goalY > run.origin.getY()) {
                run.give(new ItemStack(Items.FEATHER, 3 + 2 * run.level));
            } else {
                run.potion(MobEffects.SLOWNESS, 120, run.level - 1);
            }
        }
    }

    static void blackout(ScenarioRun run) {
        BlockPos pos = run.player.getPosition();
        if (run.world.getLightFromNeighbors(pos) < 8) {
            run.potion(MobEffects.BLINDNESS, 35, 0);
            if (++run.progress >= 4 - run.level) {
                run.hurt(DamageSource.MAGIC, 2);
                run.progress = 0;
            }
        } else {
            run.progress = 0;
            run.cloud(pos, EnumParticleTypes.VILLAGER_HAPPY, 12, 0.8);
        }
    }

    static void noFootsteps(ScenarioRun run) {
        if (run.previous != null && run.player.getPositionVector().squareDistanceTo(run.previous) > 1.6
            && !run.player.isSneaking()) {
            run.mark(run.player.getPosition(), 3.5, 14, EnumParticleTypes.NOTE, hit -> {
                hit.hurt(DamageSource.MAGIC, 2 + hit.level);
                hit.potion(MobEffects.GLOWING, 60, 0);
            });
        }
    }

    static void keepMoving(ScenarioRun run) {
        if (run.previous != null && run.player.getPositionVector().squareDistanceTo(run.previous) < 9) {
            run.cloud(run.player.getPosition(), EnumParticleTypes.SMOKE_LARGE, 15, 1);
            run.potion(MobEffects.WITHER, 42 + 8 * run.level, 0);
        }
    }

    static void redLight(ScenarioRun run) {
        if (run.step % 2 == 0) {
            run.message("§aЗелёный · можно идти");
            return;
        }
        run.message("§cКрасный · замри до следующего сигнала");
        run.progress = 0;
    }

    static void redLightTick(ScenarioRun run) {
        if (run.step % 2 != 0 && run.progress == 0 && run.elapsed % run.period >= 6
            && run.player.getPositionVector().squareDistanceTo(run.previous) > 0.64) {
            run.progress = 1;
            run.hurt(DamageSource.MAGIC, 2 + run.level);
            run.potion(MobEffects.SLOWNESS, 32, run.level - 1);
        }
    }

    static void breathingRoom(ScenarioRun run) {
        if (run.player.isInWater()) {
            int air = run.player.getAir();
            if (air > 0 && air < 300) {
                run.player.setAir(Math.max(0, air - 18 * run.level));
                run.potion(MobEffects.SLOWNESS, 30, 0);
            }
        } else if (run.player.isSneaking()) {
            run.potion(MobEffects.WATER_BREATHING, 45, 0);
        }
    }

    static void hotPursuit(ScenarioRun run) {
        int radius = 4 + run.step;
        run.ring(run.origin, radius, EnumParticleTypes.FLAME);
        if (run.near(run.origin, radius, 4) && !run.player.isWet()) {
            run.player.setFire(2 + run.level);
        }
    }

    static void waypoints(ScenarioRun run) {
        int objective = run.progress;
        double angle = run.heading + objective * 2.4;
        BlockPos pos = run.offset(run.origin, 5 + objective * 2, angle);
        BlockPos surface = run.surface(pos);
        if (surface != null && Math.abs(surface.getY() - run.origin.getY()) < 5) {
            pos = surface;
        }
        int needed = run.level + 1;
        if (objective >= needed) {
            return;
        }
        run.cloud(pos.up(), EnumParticleTypes.END_ROD, 12, 0.4);
        run.message("Метка " + (objective + 1) + "/" + needed + " · X " + pos.getX() + " Z " + pos.getZ());
        if (run.near(pos, 2.5, 4)) {
            run.sound(pos, SoundEvents.BLOCK_NOTE_BELL, 1.4F);
            if (++run.progress == needed) {
                run.give(new ItemStack(Items.EMERALD, run.level));
            }
        }
    }
}
