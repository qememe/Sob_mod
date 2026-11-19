package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = SobMod.MOD_ID)
public final class TsunamiWater extends BlockLiquid {
    public static final Block WATER = new TsunamiWater();
    private static final String CHUNK_TAG = "sobmodTsunamiWater";
    private static final int UPDATE_CLIENTS = 2 | 16;
    private static final Map<WorldServer, Set<BlockPos>> COLUMNS = new WeakHashMap<>();
    private static final Map<WorldServer, Map<ChunkPos, Integer>> WET_CHUNKS = new WeakHashMap<>();
    private static final Map<WorldServer, Set<ChunkPos>> STRANDED_CHUNKS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private TsunamiWater() {
        super(Material.WATER);
        setRegistryName(SobMod.MOD_ID, "tsunami_water");
        setHardness(100);
        setTickRandomly(false);
        setLightOpacity(3);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(WATER);
    }

    static boolean claim(WorldServer world, BlockPos column) {
        if (!COLUMNS.computeIfAbsent(world, key -> new HashSet<>()).add(column)) {
            return false;
        }
        WET_CHUNKS.computeIfAbsent(world, key -> new HashMap<>()).merge(new ChunkPos(column), 1, Integer::sum);
        return true;
    }

    static void release(WorldServer world, BlockPos column) {
        COLUMNS.get(world).remove(column);
        WET_CHUNKS.get(world).computeIfPresent(new ChunkPos(column), (key, count) -> count == 1 ? null : count - 1);
    }

    static void place(WorldServer world, BlockPos pos, int level) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == WATER || state.getBlock().isAir(state, world, pos)) {
            world.setBlockState(pos, WATER.getDefaultState().withProperty(LEVEL, level), UPDATE_CLIENTS);
        }
    }

    static void drain(WorldServer world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() == WATER) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), UPDATE_CLIENTS);
        }
    }

    @SubscribeEvent
    public static void saveChunk(ChunkDataEvent.Save event) {
        if (!(event.getWorld() instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.getWorld();
        ChunkPos pos = event.getChunk().getPos();
        Map<ChunkPos, Integer> wet = WET_CHUNKS.get(world);
        Set<ChunkPos> stranded = STRANDED_CHUNKS.get(world);
        event.getData().setBoolean(CHUNK_TAG,
            (wet != null && wet.containsKey(pos)) || (stranded != null && stranded.contains(pos)));
    }

    @SubscribeEvent
    public static void loadChunk(ChunkDataEvent.Load event) {
        if (event.getWorld() instanceof WorldServer && event.getData().getBoolean(CHUNK_TAG)) {
            WorldServer world = (WorldServer) event.getWorld();
            // Forge can fire chunk data loading off the server thread.
            STRANDED_CHUNKS.computeIfAbsent(world, key -> ConcurrentHashMap.newKeySet())
                .add(event.getChunk().getPos());
        }
    }

    static void recover(WorldServer world) {
        Set<ChunkPos> stranded = STRANDED_CHUNKS.get(world);
        if (stranded == null) {
            return;
        }
        for (ChunkPos pos : new ArrayList<>(stranded)) {
            recover(world, pos);
        }
    }

    static void recover(WorldServer world, ChunkPos pos) {
        Set<ChunkPos> stranded = STRANDED_CHUNKS.get(world);
        if (stranded == null || !stranded.contains(pos)) {
            return;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.x, pos.z);
        if (chunk == null) {
            return;
        }
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
            if (section == null || section.isEmpty()) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.get(x, y, z).getBlock() == WATER) {
                            block.setPos((pos.x << 4) + x, section.getYLocation() + y, (pos.z << 4) + z);
                            world.setBlockState(block, Blocks.AIR.getDefaultState(), UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
        stranded.remove(pos);
        chunk.markDirty();
    }
}
