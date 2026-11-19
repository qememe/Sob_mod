package ru.qememe.sobmod;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class EntityDisasterDrop extends Entity {
    public enum Type { METEOR, FAB, LAVA, TNT, AIRCRAFT }

    static final String DEBRIS_TAG = "sobmodDisasterDebris";
    private static final Type[] TYPES = Type.values();
    private static final DataParameter<Integer> VARIANT =
        EntityDataManager.createKey(EntityDisasterDrop.class, DataSerializers.VARINT);
    private static final int[] TNT_SIZES = {2, 3, 5};

    private boolean terrainDamage;
    private BlockPos bombLaunch;
    private double routeX;
    private double routeZ;
    private double routeLength;
    private Vec3d previousServerPosition;
    private ChunkPos flightChunk;

    public EntityDisasterDrop(World world) {
        super(world);
        preventEntitySpawning = false;
        setNoGravity(true);
    }

    EntityDisasterDrop(WorldServer world, Type type, BlockPos launch, int level, boolean terrainDamage) {
        this(world);
        this.terrainDamage = terrainDamage;
        dataManager.set(VARIANT, type.ordinal() * 3 + level - 1);
        setPosition(launch.getX() + 0.5, launch.getY(), launch.getZ() + 0.5);
        motionY = type == Type.TNT ? -tntSpeed(level) : type == Type.LAVA ? -0.55 : -0.35;
    }

    static EntityDisasterDrop aircraft(WorldServer world, BlockPos launch, int level, boolean terrainDamage,
                                       double dx, double dz, double distance) {
        EntityDisasterDrop aircraft = new EntityDisasterDrop(world, Type.AIRCRAFT, launch, level, terrainDamage);
        aircraft.bombLaunch = launch;
        aircraft.routeX = dx;
        aircraft.routeZ = dz;
        aircraft.routeLength = distance + 30;
        aircraft.rotationYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        aircraft.moveAircraft(-aircraft.routeLength);
        return aircraft;
    }

    public Type type() {
        return TYPES[dataManager.get(VARIANT) / 3];
    }

    public int level() {
        return dataManager.get(VARIANT) % 3 + 1;
    }

    static float width(Type type, int level) {
        switch (type) {
            case METEOR: return level + 1;
            case FAB: return 0.95F;
            case LAVA: return 0.35F;
            case AIRCRAFT: return 10.0F;
            default: return 0.98F;
        }
    }

    static float height(Type type, int level) {
        switch (type) {
            case METEOR: return level + 1;
            case FAB: return 2.0F;
            case LAVA: return 0.35F;
            case AIRCRAFT: return 2.0F;
            default: return 0.98F;
        }
    }

    static IBlockState meteorMaterial(int level, int x, int y, int z) {
        if (level == 3) {
            return Blocks.OBSIDIAN.getDefaultState();
        }
        return ((x + y + z) & 1) == 0 ? Blocks.COBBLESTONE.getDefaultState() : Blocks.STONE.getDefaultState();
    }

    public IBlockState meteorMaterial(int x, int y, int z) {
        return meteorMaterial(level(), x, y, z);
    }

    @Override
    protected void entityInit() {
        dataManager.register(VARIANT, 0);
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (VARIANT.equals(key)) {
            setSize(width(type(), level()), height(type(), level()));
        }
    }

    void playLaunch() {
        Type type = type();
        if (type != Type.AIRCRAFT) {
            ChunkPos chunk = new ChunkPos(getPosition());
            if (FlightChunks.hold(world, chunk)) {
                flightChunk = chunk;
            }
        }
        if (type == Type.METEOR) {
            world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.WEATHER, 4, 0.75F);
        } else if (type == Type.TNT) {
            world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.2F, 1);
        } else {
            if (type == Type.AIRCRAFT || type == Type.FAB) {
                world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_GHAST_WARN, SoundCategory.WEATHER, 6, 0.65F);
            }
        }
    }

    @Override
    public void onUpdate() {
        if (SobMod.eventsPaused(world)) {
            // World increments the entity age before calling onUpdate.
            ticksExisted--;
            return;
        }
        super.onUpdate();
        if (world.isRemote) {
            if (previousServerPosition != null) {
                prevPosX = lastTickPosX = previousServerPosition.x;
                prevPosY = lastTickPosY = previousServerPosition.y;
                prevPosZ = lastTickPosZ = previousServerPosition.z;
            }
            previousServerPosition = getPositionVector();
            return;
        }

        if (type() == Type.AIRCRAFT) {
            fly();
            return;
        }
        if (posY < 0 || (type() != Type.TNT && ticksExisted > 500)) {
            setDead();
            return;
        }
        if (type() != Type.TNT) {
            motionY = Math.max(motionY - 0.035, type() == Type.FAB ? -1.05 : -1.65);
        }
        move(MoverType.SELF, 0, motionY, 0);
        WorldServer server = (WorldServer) world;
        if (type() == Type.LAVA) {
            for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox())) {
                if (entity.attackEntityFrom(DamageSource.LAVA, 4)) {
                    entity.setFire(4);
                }
            }
        }
        BlockPos contact = new BlockPos(posX, posY + 0.05, posZ);
        Material material = world.getBlockState(contact).getMaterial();
        if (onGround || collidedVertically || material == Material.WATER || material == Material.LAVA) {
            setDead();
            impact(server, contact);
            return;
        }
        trail(server);
    }

    private static double tntSpeed(int level) {
        return level == 3 ? 0.125 : 0.25;
    }

    private void moveAircraft(double offset) {
        double x = bombLaunch.getX() + 0.5 + offset * routeX;
        double z = bombLaunch.getZ() + 0.5 + offset * routeZ;
        BlockPos column = new BlockPos(x, 0, z);
        int ground = world.isBlockLoaded(column) ? world.getPrecipitationHeight(column).getY() : bombLaunch.getY() - 20;
        double y = Math.min(world.getActualHeight() - 2, Math.max(Math.max(bombLaunch.getY(), ground + 18), 120));
        setPosition(x, y, z);
    }

    private void fly() {
        double offset = ticksExisted * 1.65 - routeLength;
        moveAircraft(offset);
        WorldServer server = (WorldServer) world;
        if (offset >= 0 && offset < 1.65 && world.isBlockLoaded(bombLaunch)) {
            BlockPos release = new BlockPos(bombLaunch.getX(), posY - 2, bombLaunch.getZ());
            if (release.getY() >= world.getPrecipitationHeight(release).getY() + 15) {
                EntityFab bomb = new EntityFab(server, release, level(), terrainDamage);
                if (server.spawnEntity(bomb)) {
                    bomb.playLaunch();
                }
            }
        }
        if (ticksExisted % 4 == 0) {
            server.spawnParticle(EnumParticleTypes.CLOUD, true, posX, posY + 0.7, posZ, 4, 0.9, 0.15, 0.9, 0.03);
        }
        if (offset >= 32) {
            setDead();
        }
    }

    private void trail(WorldServer server) {
        if (ticksExisted % 2 != 0) {
            return;
        }
        switch (type()) {
            case METEOR:
                double spread = width * 0.35;
                server.spawnParticle(EnumParticleTypes.FLAME, true, posX, posY + height / 2, posZ,
                    level() * 5, spread, spread, spread, 0.045);
                server.spawnParticle(EnumParticleTypes.SMOKE_LARGE, true, posX, posY + height, posZ, 5,
                    spread, 0.4, spread, 0.02);
                if (ticksExisted % 16 == 0) {
                    server.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_FIREWORK_LAUNCH, SoundCategory.WEATHER, 3, 0.55F);
                }
                break;
            case FAB:
                server.spawnParticle(EnumParticleTypes.SMOKE_LARGE, true, posX, posY + height, posZ,
                    2 + level(), 0.25, 0.25, 0.25, 0.025);
                break;
            case LAVA:
                server.spawnParticle(EnumParticleTypes.LAVA, true, posX, posY + 0.25, posZ, 2, 0.1, 0.15, 0.1, 0);
                break;
            case TNT:
                server.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, posX, posY + 1, posZ, 1, 0.09, 0.09, 0.09, 0.005);
                break;
            default:
                break;
        }
    }

    private void impact(WorldServer server, BlockPos contact) {
        if (type() == Type.LAVA) {
            server.spawnParticle(EnumParticleTypes.LAVA, true, posX, posY, posZ, 8, 0.36, 0.12, 0.36, 0);
            TemporaryLava.land(server, contact);
            return;
        }
        boolean tnt = type() == Type.TNT;
        boolean fab = type() == Type.FAB;
        int level = level();
        int radius = fab ? FabBlast.radius(level) : 2 + level;
        int tntSize = TNT_SIZES[level - 1];
        float damage = tnt ? 7 + 4 * level : 5 + 3 * level;
        server.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS,
            fab ? (float) (FabBlast.shockRadius(level) / 16 + 2) : 2.5F, 1.05F - level * 0.10F);
        server.spawnParticle(fab || level == 3 ? EnumParticleTypes.EXPLOSION_HUGE : EnumParticleTypes.EXPLOSION_LARGE,
            true, posX, posY + 0.45, posZ, 1, 0.0, 0.0, 0.0, 0.0);
        if (!tnt) {
            server.spawnParticle(EnumParticleTypes.SMOKE_LARGE, true, posX, posY + 0.8, posZ,
                fab ? level * 65 : level * 12, Math.min(radius * 0.31, 22),
                Math.min(radius * 0.21, 16), Math.min(radius * 0.31, 22), 0.09);
        }

        Vec3d blast = new Vec3d(posX, posY + 0.15, posZ);
        if (fab) {
            FabBlast.detonate(server, contact, blast, level, terrainDamage);
            return;
        }
        AxisAlignedBB zone = tnt
            ? new AxisAlignedBB(contact.add(-(tntSize - 1) / 2, -tntSize / 2, -(tntSize - 1) / 2),
                contact.add(tntSize / 2 + 1, tntSize / 2 + 1, tntSize / 2 + 1))
            : new AxisAlignedBB(contact).grow(radius + 0.5);
        for (EntityLivingBase entity : server.getEntitiesWithinAABB(EntityLivingBase.class, zone)) {
            double distance = entity.getDistance(posX, posY, posZ);
            double reach = tnt ? tntSize : radius + 1;
            double exposure = server.getBlockDensity(blast, entity.getEntityBoundingBox());
            double strength = Math.max(0, 1 - distance / reach) * exposure;
            if (strength > 0 && entity.attackEntityFrom(DamageSource.causeExplosionDamage((EntityLivingBase) null),
                (float) (damage * strength))) {
                double divisor = Math.max(0.5, distance);
                entity.addVelocity((entity.posX - posX) / divisor * strength, 0.45 * strength,
                    (entity.posZ - posZ) / divisor * strength);
                entity.velocityChanged = true;
            }
        }
        if (!terrainDamage) {
            return;
        }
        if (tnt) {
            for (int x = -(tntSize - 1) / 2; x <= tntSize / 2; x++) {
                for (int z = -(tntSize - 1) / 2; z <= tntSize / 2; z++) {
                    for (int y = -(tntSize + 1) / 2; y <= 0; y++) {
                        destroy(server, contact.add(x, y, z), 10 * level);
                    }
                }
            }
            return;
        }
        meteorCrater(server, contact, radius, level);
    }

    private void meteorCrater(WorldServer server, BlockPos center, int radius, int level) {
        int debrisBudget = 4 + level * 4;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double radialDistance = Math.hypot(x, z);
                if (radialDistance >= radius) {
                    continue;
                }
                double profile = 1 - radialDistance / radius;
                int depth = 1 + (int) Math.round(level * profile);
                for (int y = -depth; y <= 1; y++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!server.isBlockLoaded(pos) || !server.getWorldBorder().contains(pos)
                        || !canBreak(server, pos, 24)) {
                        continue;
                    }
                    IBlockState state = server.getBlockState(pos);
                    if (state.getMaterial() == Material.AIR || !server.setBlockToAir(pos)) {
                        continue;
                    }
                    if (debrisBudget > 0 && state.isFullCube() && !state.getMaterial().isLiquid()) {
                        launchDebris(server, state, center.getX() + 0.5 + x * 0.23,
                            center.getY() + 1.2, center.getZ() + 0.5 + z * 0.23, rand, 1);
                        debrisBudget--;
                    }
                }
                BlockPos floor = center.add(x, -depth - 1, z);
                if (server.isBlockLoaded(floor) && server.getWorldBorder().contains(floor)
                    && !server.isAirBlock(floor) && canBreak(server, floor, 24)) {
                    server.setBlockState(floor, meteorMaterial(level, x, -depth, z), 3);
                }
            }
        }
    }

    static void launchDebris(WorldServer server, IBlockState state, double x, double y, double z,
                             Random random, double strength) {
        EntityFallingBlock debris = new EntityFallingBlock(server, x, y, z, state);
        // The source was already removed from the crater.
        debris.fallTime = 1;
        debris.getEntityData().setBoolean(DEBRIS_TAG, true);
        debris.shouldDropItem = false;
        debris.setHurtEntities(true);
        double angle = random.nextDouble() * Math.PI * 2;
        double speed = (0.16 + random.nextDouble() * 0.47) * strength;
        debris.motionX = Math.cos(angle) * speed;
        debris.motionY = (0.45 + random.nextDouble() * 0.47) * strength;
        debris.motionZ = Math.sin(angle) * speed;
        if (server.spawnEntity(debris)) {
            server.getEntityTracker().sendToTracking(debris, new net.minecraft.network.play.server.SPacketEntityVelocity(debris));
        }
    }

    private static void destroy(WorldServer world, BlockPos pos, float resistance) {
        if (world.isBlockLoaded(pos) && world.getWorldBorder().contains(pos) && !world.isAirBlock(pos)
            && canBreak(world, pos, resistance)) {
            world.setBlockToAir(pos);
        }
    }

    static boolean canBreak(WorldServer world, BlockPos pos, float resistance) {
        return canBreak(world, pos, world.getBlockState(pos), resistance);
    }

    static boolean canBreak(WorldServer world, BlockPos pos, IBlockState state, float resistance) {
        Block block = state.getBlock();
        return state.getBlockHardness(world, pos) >= 0 && !block.hasTileEntity(state)
            && block.getExplosionResistance((Entity) null) < resistance;
    }

    @Override
    public void setDead() {
        if (flightChunk != null) {
            FlightChunks.release(world, flightChunk);
            flightChunk = null;
        }
        super.setDead();
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {}
}
