package ru.qememe.sobmod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldSavedData;

public final class FabBlast extends WorldSavedData {
    private static final String DATA_NAME = "sobmod_fab_craters";
    private static final int[] RADII = {50, 100, 225};
    private static final int[] LOADS = {500, 1500, 3000};
    private static final int CHUNKS_PER_TICK = 32;
    private static final int DEBRIS_PER_TICK = 48;
    private static final long WORK_NANOS = 18_000_000L;
    private static final int UPDATE_CLIENTS = 2 | 16;
    private static final int STONE_DEPTH = 3;
    private static final double DEPTH_PER_DIAMETER = 0.1;
    private static final double RIM_START = 0.85;
    private static final double DEBRIS_STRENGTH = 2.5;

    private final Queue<Crater> craters = new ArrayDeque<>();

    public FabBlast(String name) {
        super(name);
    }

    static String designation(int level) {
        return "ФАБ-" + LOADS[level - 1];
    }

    static int radius(int level) {
        return RADII[level - 1];
    }

    static double shockRadius(int level) {
        return radius(level) * 1.5;
    }

    static void detonate(WorldServer world, BlockPos contact, Vec3d origin, int level, boolean terrainDamage) {
        double reach = shockRadius(level);
        AxisAlignedBB bounds = new AxisAlignedBB(contact).grow(reach);
        double power = 24 + level * 12;
        for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, bounds)) {
            Vec3d eyes = entity.getPositionEyes(1);
            double distance = eyes.distanceTo(origin);
            if (distance >= reach) {
                continue;
            }
            boolean covered = world.rayTraceBlocks(origin, eyes, false, true, false) != null;
            double force = (1 - distance / reach) * (covered ? 0.45 : 1.0);
            if (entity.attackEntityFrom(DamageSource.causeExplosionDamage((EntityLivingBase) null),
                (float) (power * force))) {
                double scale = 1.8 * force / Math.max(1, distance);
                entity.addVelocity((entity.posX - origin.x) * scale, 0.65 * force, (entity.posZ - origin.z) * scale);
                entity.velocityChanged = true;
            }
        }
        if (terrainDamage) {
            FabBlast data = get(world, true);
            data.craters.add(new Crater(contact, level, false));
            data.markDirty();
        }
    }

    static boolean hasPendingWork(WorldServer world) {
        FabBlast data = get(world, false);
        return data != null && !data.craters.isEmpty();
    }

    static void tick(WorldServer world) {
        FabBlast data = get(world, false);
        if (data == null || data.craters.isEmpty()) {
            return;
        }
        Crater crater = data.craters.element();
        if (crater.advance(world)) {
            data.craters.remove();
        }
        data.markDirty();
    }

    private static FabBlast get(WorldServer world, boolean create) {
        FabBlast data = (FabBlast) world.getPerWorldStorage().getOrLoadData(FabBlast.class, DATA_NAME);
        if (data == null && create) {
            data = new FabBlast(DATA_NAME);
            world.getPerWorldStorage().setData(DATA_NAME, data);
        }
        return data;
    }

    private static final class Crater {
        final BlockPos center;
        final int radius;
        final int level;
        final List<ChunkPos> chunks = new ArrayList<>();
        final boolean oldFootprint;
        final double outerRadiusSq;
        int chunkIndex;
        int column;
        int y = -1;
        int debrisRemaining;
        boolean legacyProfile;

        Crater(BlockPos center, int level, boolean oldFootprint) {
            this.center = center;
            this.radius = radius(level);
            this.level = level;
            this.oldFootprint = oldFootprint;
            double reach = oldFootprint ? radius : shockRadius(level);
            this.outerRadiusSq = reach * reach;
            this.debrisRemaining = (16 + 16 * level) * 10;
            int centerX = center.getX() >> 4;
            int centerZ = center.getZ() >> 4;
            int range = (int) reach;
            for (int x = (center.getX() - range) >> 4; x <= (center.getX() + range) >> 4; x++) {
                for (int z = (center.getZ() - range) >> 4; z <= (center.getZ() + range) >> 4; z++) {
                    int dx = center.getX() - Math.max(x << 4, Math.min((x << 4) + 15, center.getX()));
                    int dz = center.getZ() - Math.max(z << 4, Math.min((z << 4) + 15, center.getZ()));
                    if (inside(dx * dx + dz * dz)) {
                        chunks.add(new ChunkPos(x, z));
                    }
                }
            }
            // Keep the chunk order used by saved craters from 1.x.
            chunks.sort(Comparator.comparingInt(pos -> (pos.x - centerX) * (pos.x - centerX)
                + (pos.z - centerZ) * (pos.z - centerZ)));
        }

        private boolean inside(int distanceSq) {
            return oldFootprint ? distanceSq < outerRadiusSq : distanceSq <= outerRadiusSq;
        }

        boolean advance(WorldServer world) {
            long deadline = System.nanoTime() + WORK_NANOS;
            int debrisBudget = DEBRIS_PER_TICK;
            int radiusSq = radius * radius;
            double maxDepth = legacyProfile ? radius : 2 * radius * DEPTH_PER_DIAMETER;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int visited = 0; visited < CHUNKS_PER_TICK && chunkIndex < chunks.size() && debrisBudget > 0; visited++) {
                if (visited > 0 && System.nanoTime() >= deadline) {
                    break;
                }
                ChunkPos coordinates = chunks.get(chunkIndex);
                if (!world.getWorldBorder().contains(coordinates)) {
                    nextChunk();
                    continue;
                }
                Chunk chunk = world.getChunkProvider().provideChunk(coordinates.x, coordinates.z);
                for (; column < 256 && debrisBudget > 0; column++) {
                    if ((column & 3) == 0 && column > 0 && System.nanoTime() >= deadline) {
                        break;
                    }
                    int x = (coordinates.x << 4) + (column & 15);
                    int z = (coordinates.z << 4) + (column >> 4);
                    int dx = x - center.getX();
                    int dz = z - center.getZ();
                    int distanceSq = dx * dx + dz * dz;
                    if (!inside(distanceSq) || !world.getWorldBorder().contains(pos.setPos(x, 1, z))) {
                        y = -1;
                        continue;
                    }
                    if (distanceSq < radiusSq) {
                        int depth = Math.max(1, (int) Math.round(maxDepth * (1 - distanceSq / (double) radiusSq)));
                        int bottom = Math.max(1, center.getY() - depth);
                        if (cutColumn(world, chunk, pos, x, z, bottom)) {
                            debrisBudget--;
                            debrisRemaining--;
                        }
                        if (!oldFootprint) {
                            stoneSurface(world, chunk, pos, x, z, bottom - 1, false);
                        } else if (!legacyProfile && distanceSq >= radiusSq * RIM_START * RIM_START
                            && world.rand.nextInt(3) != 0) {
                            int surface = chunk.getPrecipitationHeight(pos.setPos(x, 0, z)).getY() - 1;
                            if (surface < bottom) {
                                mixRim(world, pos.setPos(x, surface, z));
                            }
                        }
                    } else {
                        int surface = chunk.getPrecipitationHeight(pos.setPos(x, 0, z)).getY() - 1;
                        boolean ignite = Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0;
                        stoneSurface(world, chunk, pos, x, z, surface, ignite);
                    }
                    y = -1;
                }
                // Allow completed chunks to unload unless a player or Forge ticket needs them.
                if (column == 256 && !world.getPlayerChunkMap().contains(coordinates.x, coordinates.z)
                    && !world.getPersistentChunks().containsKey(coordinates)) {
                    world.getChunkProvider().queueUnload(chunk);
                }
                if (column < 256) {
                    break;
                }
                nextChunk();
            }
            return chunkIndex >= chunks.size();
        }

        private boolean cutColumn(WorldServer world, Chunk chunk, BlockPos.MutableBlockPos pos,
                                  int x, int z, int bottom) {
            // Precipitation height skips grass, vines and other non-solid blocks.
            int top = y >= 0 ? y : Math.min(world.getActualHeight() - 1, chunk.getTopFilledSegment() + 15);
            boolean eject = !legacyProfile && debrisRemaining > 0 && world.rand.nextInt(5) == 0;
            boolean ejected = false;
            for (int blockY = top; blockY >= bottom; blockY--) {
                pos.setPos(x, blockY, z);
                IBlockState state = chunk.getBlockState(pos);
                Material material = state.getMaterial();
                // Vanilla fluids resist small explosions but must leave the excavated volume.
                boolean fluid = material.isLiquid() && !state.getBlock().hasTileEntity(state);
                if (material == Material.AIR
                    || !fluid && !EntityDisasterDrop.canBreak(world, pos, state, 50 * level)
                    || !world.setBlockState(pos, Blocks.AIR.getDefaultState(), UPDATE_CLIENTS)) {
                    continue;
                }
                if (eject && state.isFullCube() && !material.isLiquid()) {
                    EntityDisasterDrop.launchDebris(world, state, x + 0.5, blockY + 1, z + 0.5,
                        world.rand, DEBRIS_STRENGTH);
                    eject = false;
                    ejected = true;
                }
            }
            return ejected;
        }

        private void stoneSurface(WorldServer world, Chunk chunk, BlockPos.MutableBlockPos pos,
                                  int x, int z, int start, boolean ignite) {
            if (start < 1) {
                return;
            }
            int surface = start;
            while (surface > 0) {
                pos.setPos(x, surface, z);
                IBlockState state = chunk.getBlockState(pos);
                Material material = state.getMaterial();
                if (material == Material.GROUND || material == Material.GRASS || material == Material.SAND
                    || material == Material.CLAY || material == Material.ROCK) {
                    break;
                }
                if (ignite && (material == Material.WOOD || material == Material.LEAVES || material == Material.PLANTS)
                    && state.getBlock().isFlammable(world, pos, EnumFacing.UP)) {
                    DisasterFire.ignite(world, pos.up());
                    ignite = false;
                }
                surface--;
            }
            for (int depth = 0; depth < STONE_DEPTH && surface - depth > 0; depth++) {
                pos.setPos(x, surface - depth, z);
                IBlockState state = chunk.getBlockState(pos);
                if (isSoil(state)) {
                    world.setBlockState(pos, Blocks.STONE.getDefaultState(), UPDATE_CLIENTS);
                }
            }
        }

        private void mixRim(WorldServer world, BlockPos surface) {
            IBlockState top = world.getBlockState(surface);
            if (!isGround(top)) {
                return;
            }
            int firstDepth = 1 + world.rand.nextInt(3);
            for (int offset = 0; offset < 3; offset++) {
                BlockPos lower = surface.down(1 + (firstDepth - 1 + offset) % 3);
                if (lower.getY() < 1) {
                    continue;
                }
                IBlockState buried = world.getBlockState(lower);
                if (buried != top && isGround(buried)) {
                    world.setBlockState(surface, buried, UPDATE_CLIENTS);
                    world.setBlockState(lower, top, UPDATE_CLIENTS);
                    return;
                }
            }
        }

        private boolean isSoil(IBlockState state) {
            Block block = state.getBlock();
            return block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.GRASS_PATH
                || block == Blocks.FARMLAND || block == Blocks.MYCELIUM || block == Blocks.GRAVEL
                || block == Blocks.SAND || block == Blocks.CLAY;
        }

        private boolean isGround(IBlockState state) {
            Block block = state.getBlock();
            return isSoil(state) || block == Blocks.STONE || block == Blocks.SANDSTONE
                || block == Blocks.RED_SANDSTONE || block == Blocks.HARDENED_CLAY
                || block == Blocks.STAINED_HARDENED_CLAY || block == Blocks.NETHERRACK;
        }

        private void nextChunk() {
            chunkIndex++;
            column = 0;
            y = -1;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        craters.clear();
        NBTTagList list = tag.getTagList("craters", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int level = entry.getInteger("level");
            if (level < 1 || level > RADII.length) {
                continue;
            }
            Crater crater = new Crater(BlockPos.fromLong(entry.getLong("center")), level, !entry.getBoolean("thermalFootprint"));
            crater.chunkIndex = entry.getInteger("chunk");
            crater.column = entry.getInteger("column");
            crater.y = entry.getInteger("y");
            // Let a crater started in 1.2 finish with the profile already cut into the world.
            crater.legacyProfile = !entry.hasKey("debris") || entry.getBoolean("legacyProfile");
            crater.debrisRemaining = entry.getInteger("debris");
            craters.add(crater);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Crater crater : craters) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("center", crater.center.toLong());
            entry.setInteger("level", crater.level);
            entry.setInteger("chunk", crater.chunkIndex);
            entry.setInteger("column", crater.column);
            entry.setInteger("y", crater.y);
            entry.setInteger("debris", crater.debrisRemaining);
            entry.setBoolean("legacyProfile", crater.legacyProfile);
            entry.setBoolean("thermalFootprint", !crater.oldFootprint);
            list.appendTag(entry);
        }
        tag.setTag("craters", list);
        return tag;
    }
}
