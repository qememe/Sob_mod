package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = SobMod.MOD_ID)
public final class TemporaryLava extends WorldSavedData {
    private static final String DATA_NAME = "sobmod_lava";
    private static final int CAPACITY = 320;
    private static final int LIFE = 400;
    private static final int FLOW_DELAY = 15;
    private static final int FLOW_DISTANCE = 7;
    private static final int DROP_LIMIT = 20;

    private static final BlockLiquid LAVA = new RainLavaBlock();
    private final Map<BlockPos, Flow> blocks = new HashMap<>();
    private long pausedTicks;

    private static final class RainLavaBlock extends BlockLiquid {
        RainLavaBlock() {
            super(Material.LAVA);
            setLightLevel(1);
            setHardness(100);
            setTickRandomly(true);
            setUnlocalizedName("sobmod.rain_lava");
            setRegistryName(new ResourceLocation(SobMod.MOD_ID, "rain_lava"));
        }

        @Override
        public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
            if (world instanceof WorldServer) {
                TemporaryLava data = get((WorldServer) world, false);
                if (data == null || !data.blocks.containsKey(pos)) {
                    world.setBlockToAir(pos);
                }
            }
        }
    }

    public static Block rainBlock() {
        return LAVA;
    }

    private static final class Flow {
        final BlockPos origin;
        long expires;
        long nextFlow;
        final int level;
        boolean spread;

        Flow(BlockPos origin, long expires, long nextFlow, int level) {
            this.origin = origin;
            this.expires = expires;
            this.nextFlow = nextFlow;
            this.level = level;
        }
    }

    public TemporaryLava(String name) {
        super(name);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(LAVA);
    }

    private static TemporaryLava get(WorldServer world, boolean create) {
        TemporaryLava data = (TemporaryLava) world.getPerWorldStorage().getOrLoadData(TemporaryLava.class, DATA_NAME);
        if (data == null && create) {
            data = new TemporaryLava(DATA_NAME);
            world.getPerWorldStorage().setData(DATA_NAME, data);
        }
        return data;
    }

    static void land(WorldServer world, BlockPos pos) {
        if (!world.isBlockLoaded(pos) || !world.getWorldBorder().contains(pos)) {
            return;
        }
        Material material = world.getBlockState(pos).getMaterial();
        if (material == Material.WATER) {
            coolWater(world, pos);
        } else if (canFlowInto(world, pos)) {
            TemporaryLava lava = get(world, true);
            long now = world.getTotalWorldTime() - lava.pausedTicks;
            lava.place(world, pos, new Flow(pos, now + LIFE, now + FLOW_DELAY, 0));
        }
    }

    static boolean hasActiveFlow(WorldServer world) {
        TemporaryLava lava = get(world, false);
        if (lava == null) {
            return false;
        }
        long now = world.getTotalWorldTime() - lava.pausedTicks;
        return lava.blocks.values().stream().anyMatch(flow -> flow.expires > now);
    }

    static void tick(WorldServer world, boolean paused) {
        TemporaryLava lava = get(world, false);
        if (lava != null) {
            if (paused) {
                lava.pausedTicks++;
            } else if ((world.getTotalWorldTime() - lava.pausedTicks) % 5 == 0) {
                lava.update(world);
            }
        }
    }

    static void stop(WorldServer world) {
        TemporaryLava lava = get(world, false);
        if (lava != null) {
            lava.blocks.values().forEach(flow -> flow.expires = 0);
            lava.update(world);
            lava.markDirty();
        }
    }

    private void update(WorldServer world) {
        long now = world.getTotalWorldTime() - pausedTicks;
        for (BlockPos pos : new ArrayList<>(blocks.keySet())) {
            if (!world.isBlockLoaded(pos)) {
                continue;
            }
            Flow flow = blocks.get(pos);
            boolean present = world.getBlockState(pos).getBlock() == LAVA;
            if (now >= flow.expires || !present) {
                if (present) {
                    world.setBlockToAir(pos);
                }
                blocks.remove(pos);
                markDirty();
            } else if (now >= flow.nextFlow) {
                flow.nextFlow = now + FLOW_DELAY;
                if (!flow.spread) {
                    flow.spread = true;
                    spread(world, pos, flow, now);
                }
                ignite(world, pos);
            }
        }
    }

    private void spread(WorldServer world, BlockPos pos, Flow flow, long now) {
        int distanceDown = flow.origin.getY() - pos.getY();
        BlockPos below = pos.down();
        if (distanceDown < DROP_LIMIT && canFlowInto(world, below)) {
            flowInto(world, below, flow, 8 + flow.level % 8, now);
            return;
        }

        int nextLevel = (flow.level >= 8 ? 0 : flow.level) + 2;
        if (nextLevel >= 8) {
            return;
        }
        int best = Integer.MAX_VALUE;
        EnumFacing[] sides = EnumFacing.HORIZONTALS;
        int[] scores = new int[sides.length];
        for (int index = 0; index < sides.length; index++) {
            EnumFacing side = sides[index];
            BlockPos adjacent = pos.offset(side);
            scores[index] = Integer.MAX_VALUE;
            if (withinRange(flow, adjacent) && canFlowInto(world, adjacent)) {
                scores[index] = slope(world, adjacent, flow, side.getOpposite(), 0);
                best = Math.min(best, scores[index]);
            }
        }
        if (best == Integer.MAX_VALUE) {
            return;
        }
        for (int index = 0; index < sides.length; index++) {
            if (scores[index] == best) {
                flowInto(world, pos.offset(sides[index]), flow, nextLevel, now);
            }
        }
    }

    private static int slope(WorldServer world, BlockPos pos, Flow flow, EnumFacing from, int depth) {
        if (flow.origin.getY() - pos.getY() < DROP_LIMIT && canFlowInto(world, pos.down())) {
            return depth;
        }
        int best = 1000;
        if (depth < 2) {
            for (EnumFacing side : EnumFacing.HORIZONTALS) {
                BlockPos next = pos.offset(side);
                if (side != from && withinRange(flow, next) && canFlowInto(world, next)) {
                    best = Math.min(best, slope(world, next, flow, side.getOpposite(), depth + 1));
                }
            }
        }
        return best;
    }

    private static boolean withinRange(Flow flow, BlockPos pos) {
        return Math.abs(flow.origin.getX() - pos.getX()) + Math.abs(flow.origin.getZ() - pos.getZ()) <= FLOW_DISTANCE;
    }

    private static boolean canFlowInto(World world, BlockPos pos) {
        if (pos.getY() < 1 || pos.getY() >= world.getActualHeight() || !world.isBlockLoaded(pos)
            || !world.getWorldBorder().contains(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        return state.getMaterial() != Material.LAVA
            && (state.getMaterial() == Material.WATER || state.getBlock().isReplaceable(world, pos));
    }

    private void flowInto(WorldServer world, BlockPos pos, Flow source, int level, long now) {
        if (world.getBlockState(pos).getMaterial() == Material.WATER) {
            coolWater(world, pos);
            return;
        }
        place(world, pos, new Flow(source.origin, source.expires, now + FLOW_DELAY, level));
    }

    private void place(WorldServer world, BlockPos pos, Flow flow) {
        if (blocks.size() < CAPACITY && world.setBlockState(pos, LAVA.getDefaultState().withProperty(BlockLiquid.LEVEL, flow.level), 3)
            && world.getBlockState(pos).getBlock() == LAVA) {
            blocks.put(pos, flow);
            markDirty();
        }
    }

    private static void coolWater(WorldServer world, BlockPos pos) {
        if (world.setBlockState(pos, Blocks.STONE.getDefaultState(), 3)) {
            world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 2.4F);
            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                8, 0.25, 0, 0.25, 0);
        }
    }

    private static void ignite(WorldServer world, BlockPos pos) {
        if (!world.getGameRules().getBoolean("doFireTick") || world.rand.nextInt(4) != 0) {
            return;
        }
        BlockPos fire = pos.up().offset(EnumFacing.HORIZONTALS[world.rand.nextInt(4)]);
        if (!world.isBlockLoaded(fire) || !world.getWorldBorder().contains(fire) || !world.isAirBlock(fire)) {
            return;
        }
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos fuel = fire.offset(face);
            if (world.isBlockLoaded(fuel) && world.getBlockState(fuel).getBlock().isFlammable(world, fuel, face.getOpposite())) {
                if (Blocks.FIRE.canPlaceBlockAt(world, fire)) {
                    world.setBlockState(fire, Blocks.FIRE.getDefaultState(), 3);
                }
                return;
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        blocks.clear();
        NBTTagList entries = tag.getTagList("flow", 10);
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound entry = entries.getCompoundTagAt(index);
            // The rain itself is server-session state. Remove any leftover lava when its chunk returns.
            blocks.put(BlockPos.fromLong(entry.getLong("pos")),
                new Flow(BlockPos.ORIGIN, 0, 0, 0));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList entries = new NBTTagList();
        for (BlockPos pos : blocks.keySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("pos", pos.toLong());
            entries.appendTag(entry);
        }
        tag.setTag("flow", entries);
        return tag;
    }
}
