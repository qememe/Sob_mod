package ru.qememe.sobmod;

import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

final class ScenarioHazards {
    private ScenarioHazards() {}

    static void thunderheads(ScenarioRun run) {
        BlockPos spot = run.surface(run.player.getPosition());
        if (spot == null || !run.exposed()) {
            return;
        }
        run.markImpact(spot, 2.5 + run.level, 26, EnumParticleTypes.FIREWORKS_SPARK, (strike, hit) -> {
            // Effect-only lightning leaves the terrain intact.
            strike.world.addWeatherEffect(new EntityLightningBolt(strike.world, spot.getX() + 0.5,
                spot.getY(), spot.getZ() + 0.5, true));
            if (hit && strike.exposed()) {
                strike.hurt(DamageSource.LIGHTNING_BOLT, 2 + strike.level);
            }
        });
    }

    static void hail(ScenarioRun run) {
        BlockPos center = run.player.getPosition();
        for (int i = 0; i <= run.level; i++) {
            BlockPos spot = run.surface(run.offset(center, i * 3, run.heading + run.step + i * 2));
            if (spot != null) {
                run.cloud(spot.up(4), EnumParticleTypes.SNOW_SHOVEL, 22, 1.8);
                run.mark(spot, 2.4, 18 + i * 3, EnumParticleTypes.SNOW_SHOVEL,
                    hit -> hit.hurt(DamageSource.FALLING_BLOCK, 2 + hit.level));
            }
        }
    }

    static void ashfall(ScenarioRun run) {
        if (run.exposed()) {
            run.cloud(run.player.getPosition(), EnumParticleTypes.SMOKE_LARGE, 36, 3);
            run.potion(MobEffects.BLINDNESS, 35 + run.level * 10, 0);
            if (run.player.isSprinting()) {
                run.potion(MobEffects.HUNGER, 70, run.level - 1);
            }
        }
    }

    static void heatwave(ScenarioRun run) {
        if (run.exposed() && !run.player.isWet()) {
            run.cloud(run.player.getPosition(), EnumParticleTypes.FLAME, 16, 2);
            run.potion(MobEffects.HUNGER, 70, run.level - 1);
            if (run.player.isSprinting()) {
                run.hurt(DamageSource.IN_FIRE, 1 + run.level);
            }
        }
    }

    static void whiteout(ScenarioRun run) {
        if (run.exposed() && !run.player.isBurning()) {
            run.cloud(run.player.getPosition(), EnumParticleTypes.CLOUD, 45, 4);
            run.potion(MobEffects.SLOWNESS, 44, run.level - 1);
            run.potion(MobEffects.BLINDNESS, 42, 0);
        }
    }

    static void acidDrizzle(ScenarioRun run) {
        if (run.exposed() && !run.player.isSneaking()) {
            run.cloud(run.player.getPosition().up(2), EnumParticleTypes.SLIME, 20, 3.5);
            run.potion(MobEffects.POISON, 35 + run.level * 10, 0);
        }
    }

    static void dustDevil(ScenarioRun run) {
        double angle = run.heading + run.step * 0.34;
        BlockPos spot = run.offset(run.origin, 5 + run.level * 2, angle);
        BlockPos surface = run.surface(spot);
        if (surface != null) {
            spot = surface;
        }
        run.cloud(spot.up(1), EnumParticleTypes.CLOUD, 25, 1.7);
        run.cloud(spot.up(3), EnumParticleTypes.SMOKE_NORMAL, 15, 1.2);
        if (run.near(spot, 4.5, 5)) {
            run.radial(spot, 0.55, 0.24 + run.level * 0.11);
        }
    }

    static void crosswind(ScenarioRun run) {
        if (run.exposed()) {
            double direction = run.heading + (run.step / 4 % 2) * Math.PI;
            run.cloud(run.player.getPosition(), EnumParticleTypes.CLOUD, 25, 4);
            if (!run.player.isSneaking()) {
                double strength = 0.27 + run.level * 0.16;
                run.push(Math.cos(direction) * strength, 0.04, Math.sin(direction) * strength);
            }
        }
    }

    static void tremors(ScenarioRun run) {
        int radius = 13 + run.level * 3;
        run.sound(run.origin, SoundEvents.BLOCK_GRAVEL_STEP, 0.55F);
        run.markImpact(run.origin, radius, 20, EnumParticleTypes.SMOKE_LARGE, (quake, hit) -> {
            quake.sound(quake.origin, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.55F);
            if (hit && quake.player.onGround) {
                quake.hurt(DamageSource.FALLING_BLOCK, 1 + quake.level);
                quake.push(0, 0.32, 0);
            }
        });
    }

    static void faultLine(ScenarioRun run) {
        for (int i = -3; i <= 3; i++) {
            BlockPos spot = run.offset(run.origin, i * 4, run.heading + run.step * 0.6);
            run.mark(spot, 2.5, 27, EnumParticleTypes.FLAME, hit -> {
                hit.hurt(DamageSource.IN_FIRE, 2 + hit.level);
                hit.radial(hit.origin, 0.22, 0.37);
            });
        }
    }

    static void geysers(ScenarioRun run) {
        BlockPos spot = run.offset(run.player.getPosition(), run.step % 2 * 4, run.heading + run.step);
        run.mark(spot, 2.5 + run.level, 20, EnumParticleTypes.WATER_SPLASH, hit -> {
            hit.sound(spot, SoundEvents.ENTITY_GENERIC_SPLASH, 0.8F);
            hit.push(0, 0.6 + hit.level * 0.18, 0);
        });
    }

    static void sinkhole(ScenarioRun run) {
        int radius = 9 + run.level * 2;
        run.ring(run.origin, radius, EnumParticleTypes.PORTAL);
        run.cloud(run.origin, EnumParticleTypes.PORTAL, 24, 1.8);
        if (run.near(run.origin, radius, 6)) {
            run.radial(run.origin, -0.22 - run.level * 0.11, 0);
            if (run.near(run.origin, 2.5, 3)) {
                run.hurt(DamageSource.IN_WALL, 1 + run.level);
            }
        }
    }

    static void frostfront(ScenarioRun run) {
        int distance = run.step % 10 * 3 - 15;
        BlockPos front = run.offset(run.origin, distance, run.heading);
        double sideways = run.heading + Math.PI / 2;
        for (int i = -3; i <= 3; i++) {
            BlockPos spot = run.offset(front, i * 4, sideways);
            run.cloud(spot, EnumParticleTypes.SNOW_SHOVEL, 8, 1.3);
            if (run.near(spot, 2.6, 3) && !run.player.isBurning()) {
                run.potion(MobEffects.SLOWNESS, 80, run.level - 1);
            }
        }
    }

    static void shockRings(ScenarioRun run) {
        int radius = 2 + (run.step % 9) * 2;
        run.ring(run.origin, radius, EnumParticleTypes.CRIT_MAGIC);
        double distance = Math.sqrt(DisasterEvents.horizontalDistanceSq(run.player, run.origin));
        if (Math.abs(distance - radius) < 1.6 && Math.abs(run.player.posY - run.origin.getY()) < 1.35) {
            run.hurt(DamageSource.MAGIC, 1 + run.level);
            run.radial(run.origin, 0.4, 0.22);
        }
    }

    static void emberSpiral(ScenarioRun run) {
        double angle = run.heading + run.step * 0.7;
        double radius = 3 + run.step % 7 * 2;
        for (int i = 0; i < run.level; i++) {
            BlockPos pos = run.offset(run.origin, radius, angle + i * Math.PI * 2 / run.level);
            run.mark(pos, 2.7, 14, EnumParticleTypes.FLAME, hit -> hit.player.setFire(3));
        }
    }

    static void staticCharge(ScenarioRun run) {
        if (run.player.isWet()) {
            run.cloud(run.player.getPosition(), EnumParticleTypes.FIREWORKS_SPARK, 22, 1.3);
            run.hurt(DamageSource.LIGHTNING_BOLT, 1 + run.level);
        }
    }

    static void bogPressure(ScenarioRun run) {
        if (run.player.isInWater()) {
            run.cloud(run.player.getPosition(), EnumParticleTypes.WATER_BUBBLE, 22, 1.3);
            run.push(0, -0.12 - run.level * 0.05, 0);
            run.potion(MobEffects.MINING_FATIGUE, 42, run.level - 1);
        }
    }

    static void caveIn(ScenarioRun run) {
        BlockPos spot = run.player.getPosition();
        if (!run.exposed()) {
            run.cloud(spot.up(2), EnumParticleTypes.SMOKE_LARGE, 24, 1.7);
            run.mark(spot, 2.4 + run.level, 24, EnumParticleTypes.CRIT,
                hit -> hit.hurt(DamageSource.FALLING_BLOCK, 2 + hit.level * 2));
        }
    }

    static void echoBombs(ScenarioRun run) {
        if (run.previous != null) {
            BlockPos spot = new BlockPos(run.previous);
            run.mark(spot, 2.5 + run.level, 18, EnumParticleTypes.SMOKE_NORMAL, hit -> {
                hit.hurt(DamageSource.MAGIC, 2 + hit.level);
                hit.radial(spot, 0.5, 0.3);
            });
        }
    }

    static void magneticSurge(ScenarioRun run) {
        if (ScenarioRun.metalArmor(run.player)) {
            run.cloud(run.player.getPosition(), EnumParticleTypes.CRIT_MAGIC, 25, 1.7);
            run.radial(run.origin, -0.35 - run.level * 0.1, 0.12);
            run.potion(MobEffects.SLOWNESS, 35, run.level - 1);
        }
    }
}
