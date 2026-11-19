package ru.qememe.sobmod;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = SobMod.MOD_ID)
public final class DisasterFire extends BlockFire {
    public static final Block FIRE = new DisasterFire();
    private static final int MIN_BURN_TICKS = 65;

    private DisasterFire() {
        setRegistryName(SobMod.MOD_ID, "disaster_fire");
        setLightLevel(1);
        setTickRandomly(false);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(FIRE);
    }

    static boolean ignite(World world, BlockPos pos) {
        if (pos.getY() <= 0 || pos.getY() >= world.getActualHeight()
            || !world.getWorldBorder().contains(pos) || !world.isBlockLoaded(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!block.isAir(state, world, pos)
            && !(block.isReplaceable(world, pos) && block.isFlammable(world, pos, EnumFacing.UP))) {
            return false;
        }
        BlockPos below = pos.down();
        if (world.getBlockState(below).isTopSolid() || Blocks.FIRE.canCatchFire(world, below, EnumFacing.UP)
            || world.isAreaLoaded(pos, 1) && FIRE.canPlaceBlockAt(world, pos)) {
            return world.setBlockState(pos, FIRE.getDefaultState(), 2);
        }
        return false;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            world.scheduleUpdate(pos, this, MIN_BURN_TICKS + world.rand.nextInt(50));
        }
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (!world.getGameRules().getBoolean("doFireTick")) {
            world.scheduleUpdate(pos, this, MIN_BURN_TICKS + random.nextInt(50));
            return;
        }
        boolean rain = world.isRainingAt(pos);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        if (rain) {
            return;
        }
        // A flame can consume its fuel, but it never creates another flame.
        EnumFacing[] faces = EnumFacing.VALUES;
        int first = random.nextInt(faces.length);
        for (int offset = 0; offset < faces.length; offset++) {
            EnumFacing face = faces[(first + offset) % faces.length];
            BlockPos fuel = pos.offset(face);
            if (!world.isBlockLoaded(fuel) || !world.getWorldBorder().contains(fuel)) {
                continue;
            }
            IBlockState neighbor = world.getBlockState(fuel);
            Block block = neighbor.getBlock();
            int flammability = block.getFlammability(world, fuel, face.getOpposite());
            if (!block.hasTileEntity(neighbor) && random.nextInt(60) < flammability) {
                world.setBlockToAir(fuel);
                break;
            }
        }
    }

    @Override
    public void onEntityCollidedWithBlock(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && !entity.isImmuneToFire() && !entity.isWet()) {
            entity.attackEntityFrom(DamageSource.IN_FIRE, 1);
            entity.setFire(4);
        }
    }
}
