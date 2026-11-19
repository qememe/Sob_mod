package ru.qememe.sobmod;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public final class EntityFab extends EntityDisasterDrop {
    public EntityFab(World world) {
        super(world);
    }

    EntityFab(WorldServer world, BlockPos launch, int level, boolean terrainDamage) {
        super(world, Type.FAB, launch, level, terrainDamage);
    }
}
