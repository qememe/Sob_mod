package ru.qememe.sobmod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

final class Tsunami implements DisasterEvents.Effect {
    private static final int HALF_WIDTH = 80;
    private static final int DEPTH = 10;
    private static final int APPROACH = 100;
    private static final int TRAVEL = 200;
    private static final int FADE_DISTANCE = 24;
    private static final int TICKS_PER_BLOCK = 9;
    private static final int[] CREST_HEIGHTS = {8, 16, 24};

    private final WorldServer world;
    private final BlockPos target;
    private final EnumFacing direction;
    private final EnumFacing side;
    private final int height;
    private final int crest;
    private final Map<BlockPos, Column> columns = new HashMap<>();
    private final Set<ChunkPos> retained = new HashSet<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private static final class Column {
        int bottom;
        int top;
        int surfaceLevel;
    }

    Tsunami(WorldServer world, BlockPos target, int level) {
        this.world = world;
        this.target = new BlockPos(target.getX(), 0, target.getZ());
        height = CREST_HEIGHTS[level - 1];
        crest = Math.min(world.getActualHeight(), world.getPrecipitationHeight(target).getY() + height);
        direction = selectDirection();
        side = direction.rotateY();
    }

    private EnumFacing selectDirection() {
        EnumFacing[] directions = EnumFacing.HORIZONTALS;
        int offset = world.rand.nextInt(directions.length);
        EnumFacing selected = directions[offset];
        int best = -1;
        for (int i = 0; i < directions.length; i++) {
            EnumFacing candidate = directions[(i + offset) % directions.length];
            BlockPos start = target.offset(candidate, -APPROACH);
            int openLanes = 0;
            for (int lane = -HALF_WIDTH; lane < HALF_WIDTH; lane++) {
                BlockPos column = start.offset(candidate.rotateY(), lane);
                if (world.isBlockLoaded(column) && world.getWorldBorder().contains(column)
                    && world.getPrecipitationHeight(column).getY() < crest) {
                    openLanes++;
                }
            }
            if (openLanes > best) {
                selected = candidate;
                best = openLanes;
            }
        }
        return selected;
    }

    @Override
    public boolean tick(int age) {
        int elapsed = age - DisasterEvents.WARNING_TICKS;
        if (elapsed < 0 || elapsed % TICKS_PER_BLOCK != 0) {
            return true;
        }
        int advance = elapsed / TICKS_PER_BLOCK;
        if (advance >= TRAVEL) {
            return false;
        }
        BlockPos front = target.offset(direction, advance - APPROACH);
        if (advance == 0) {
            world.playSound(null, world.getPrecipitationHeight(target), SoundEvents.ENTITY_LIGHTNING_THUNDER,
                SoundCategory.WEATHER, 5, 0.65F);
        }
        move(front, Math.min(1.0, (TRAVEL - advance) / (double) FADE_DISTANCE));
        return true;
    }

    private void move(BlockPos front, double strength) {
        Iterator<Map.Entry<BlockPos, Column>> iterator = columns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Column> entry = iterator.next();
            BlockPos pos = entry.getKey();
            int depth = (front.getX() - pos.getX()) * direction.getFrontOffsetX()
                + (front.getZ() - pos.getZ()) * direction.getFrontOffsetZ();
            if (depth >= DEPTH) {
                drain(pos, entry.getValue());
                iterator.remove();
            }
        }

        Set<ChunkPos> needed = retainFront(front);
        for (int lane = -HALF_WIDTH; lane < HALF_WIDTH; lane++) {
            double edge = Math.abs(lane + 0.5) / HALF_WIDTH;
            double widthProfile = 1.0 - Math.pow(edge, 4);
            BlockPos leading = front.offset(side, lane);
            for (int depth = 0; depth < DEPTH; depth++) {
                BlockPos pos = leading.offset(direction, -depth);
                if (!needed.contains(new ChunkPos(pos)) || !world.getWorldBorder().contains(pos)) {
                    continue;
                }
                // A steep front rises to a crest and thins towards the rear.
                double depthProfile = depth == 0 ? 0.78 : (DEPTH - depth) / (double) (DEPTH - 1);
                double surface = height * widthProfile * depthProfile * strength;
                updateColumn(pos, surface);
            }
        }
    }

    private void updateColumn(BlockPos pos, double surface) {
        Column previous = columns.get(pos);
        int floor = world.getPrecipitationHeight(pos).getY();
        if (previous != null) {
            while (floor > 0) {
                at(pos, floor - 1);
                if (world.getBlockState(cursor).getBlock() != TsunamiWater.WATER && !world.isAirBlock(cursor)) {
                    break;
                }
                floor--;
            }
        }

        double waterHeight = Math.min(crest - floor, surface);
        if (floor <= 0 || waterHeight < 0.2) {
            if (previous != null) {
                drain(pos, previous);
                columns.remove(pos);
            }
            return;
        }
        int top = floor + (int) Math.ceil(waterHeight);
        int level = Math.max(1, Math.min(7, (int) Math.round(8 * (top - floor - waterHeight))));
        if (previous == null) {
            if (!TsunamiWater.claim(world, pos)) {
                return;
            }
            previous = new Column();
            columns.put(pos, previous);
        }
        for (int y = previous.bottom; y < previous.top; y++) {
            if (y < floor || y >= top) {
                TsunamiWater.drain(world, at(pos, y));
            }
        }
        for (int y = floor; y < top; y++) {
            boolean surfaceBlock = y == top - 1;
            int newLevel = surfaceBlock ? level : 8;
            int oldLevel = y == previous.top - 1 ? previous.surfaceLevel : 8;
            if (y < previous.bottom || y >= previous.top || newLevel != oldLevel) {
                TsunamiWater.place(world, at(pos, y), newLevel);
            }
        }
        previous.bottom = floor;
        previous.top = top;
        previous.surfaceLevel = level;
    }

    private BlockPos at(BlockPos pos, int y) {
        return cursor.setPos(pos.getX(), y, pos.getZ());
    }

    private Set<ChunkPos> retainFront(BlockPos front) {
        BlockPos a = front.offset(side, -HALF_WIDTH);
        BlockPos b = front.offset(side, HALF_WIDTH - 1).offset(direction, 1 - DEPTH);
        Set<ChunkPos> needed = new HashSet<>();
        for (int x = Math.min(a.getX(), b.getX()) >> 4; x <= Math.max(a.getX(), b.getX()) >> 4; x++) {
            for (int z = Math.min(a.getZ(), b.getZ()) >> 4; z <= Math.max(a.getZ(), b.getZ()) >> 4; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                if (!world.getWorldBorder().contains(pos)) {
                    continue;
                }
                if (retained.contains(pos) || FlightChunks.hold(world, pos)) {
                    if (retained.add(pos)) {
                        world.getChunkProvider().provideChunk(pos.x, pos.z);
                        TsunamiWater.recover(world, pos);
                    }
                    needed.add(pos);
                }
            }
        }
        retained.removeIf(pos -> {
            if (needed.contains(pos)) {
                return false;
            }
            release(pos);
            return true;
        });
        return needed;
    }

    private void drain(BlockPos pos, Column column) {
        for (int y = column.bottom; y < column.top; y++) {
            TsunamiWater.drain(world, at(pos, y));
        }
        TsunamiWater.release(world, pos);
    }

    private void release(ChunkPos pos) {
        FlightChunks.release(world, pos);
        Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.x, pos.z);
        if (chunk != null && !world.getPlayerChunkMap().contains(pos.x, pos.z)
            && !world.getPersistentChunks().containsKey(pos)) {
            world.getChunkProvider().queueUnload(chunk);
        }
    }

    @Override
    public void close() {
        columns.forEach(this::drain);
        columns.clear();
        retained.forEach(this::release);
        retained.clear();
    }
}
