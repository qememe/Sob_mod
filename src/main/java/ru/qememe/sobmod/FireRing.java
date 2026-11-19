package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

final class FireRing implements DisasterEvents.Effect {
    private static final int START_RADIUS = 50;
    private static final int TICKS_PER_BLOCK = 10;
    private static final int SPACING = 3;

    private final WorldServer world;
    private final List<List<BlockPos>> rings = new ArrayList<>(START_RADIUS + 1);

    private final List<BlockPos> fires = new ArrayList<>();

    FireRing(WorldServer world, BlockPos center) {
        this.world = world;
        for (int radius = 0; radius <= START_RADIUS; radius++) {
            rings.add(new ArrayList<>());
        }
        for (int dx = -START_RADIUS; dx <= START_RADIUS; dx++) {
            int offset = Math.floorMod(START_RADIUS + dx, SPACING);
            for (int dz = -START_RADIUS + offset; dz <= START_RADIUS; dz += SPACING) {
                int distanceSq = dx * dx + dz * dz;
                if (distanceSq <= START_RADIUS * START_RADIUS) {
                    rings.get((int) Math.ceil(Math.sqrt(distanceSq))).add(center.add(dx, 0, dz));
                }
            }
        }
    }

    @Override
    public boolean tick(int age) {
        int elapsed = age - DisasterEvents.WARNING_TICKS;
        if (elapsed < 0) {
            return true;
        }

        int radius = START_RADIUS - elapsed / TICKS_PER_BLOCK;
        if (radius < 0) {
            return world.getGameRules().getBoolean("doFireTick") && fires.stream().anyMatch(pos ->
                world.isBlockLoaded(pos) && world.getBlockState(pos).getBlock() == DisasterFire.FIRE);
        }
        List<BlockPos> ring = rings.get(radius);
        int tick = elapsed % TICKS_PER_BLOCK;
        for (int index = tick; index < ring.size(); index += TICKS_PER_BLOCK) {
            BlockPos column = ring.get(index);
            if (world.isBlockLoaded(column) && world.getWorldBorder().contains(column)) {
                BlockPos pos = world.getPrecipitationHeight(column);
                if (DisasterFire.ignite(world, pos)) {
                    fires.add(pos);
                }
            }
        }
        return true;
    }
}
