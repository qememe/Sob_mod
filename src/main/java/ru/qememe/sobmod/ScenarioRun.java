package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

final class ScenarioRun implements DisasterEvents.Effect {
    private static final String TEMPORARY_MOB = "sobmodScenarioMob";
    private static final Set<EntityLiving> LIVE_MOBS = new HashSet<>();
    private static final int MAX_MOBS = 10;
    private static final int MAX_MARKS = 24;
    private static final int[] SPAWN_HEIGHTS = {0, 1, -1, 2, -2};

    final WorldServer world;
    final BlockPos origin;
    final int level;
    final int duration;
    final int period;
    final double heading;
    private final UUID target;
    private final Consumer<ScenarioRun> pulse;
    private final Consumer<ScenarioRun> betweenPulses;
    private final List<EntityLiving> mobs = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    EntityPlayerMP player;
    int elapsed;
    int step;
    int progress;
    Vec3d previous;

    private static final class Mark {
        final BlockPos pos;
        final double radius;
        final int due;
        final EnumParticleTypes particle;
        final BiConsumer<ScenarioRun, Boolean> impact;

        Mark(BlockPos pos, double radius, int due, EnumParticleTypes particle, BiConsumer<ScenarioRun, Boolean> impact) {
            this.pos = pos;
            this.radius = radius;
            this.due = due;
            this.particle = particle;
            this.impact = impact;
        }
    }

    ScenarioRun(EntityPlayerMP player, int level, int duration, int period,
                Consumer<ScenarioRun> pulse, Consumer<ScenarioRun> betweenPulses) {
        world = player.getServerWorld();
        origin = player.getPosition();
        target = player.getUniqueID();
        this.level = level;
        this.duration = duration;
        this.period = period;
        this.pulse = pulse;
        this.betweenPulses = betweenPulses;
        heading = world.rand.nextDouble() * Math.PI * 2;
    }

    @Override
    public boolean tick(int age) {
        player = world.getMinecraftServer().getPlayerList().getPlayerByUUID(target);
        if (player == null || player.world != world || !DisasterEvents.isEligibleTarget(player)) {
            return false;
        }
        elapsed = age - DisasterEvents.WARNING_TICKS;
        if (elapsed < 0) {
            return true;
        }
        if (elapsed < duration && elapsed % period == 0) {
            step = elapsed / period;
            pulse.accept(this);
            previous = player.getPositionVector();
        } else if (elapsed < duration && betweenPulses != null) {
            betweenPulses.accept(this);
        }
        for (Iterator<Mark> iterator = marks.iterator(); iterator.hasNext();) {
            Mark mark = iterator.next();
            if (!loaded(mark.pos)) {
                iterator.remove();
                continue;
            }
            if (elapsed >= mark.due) {
                cloud(mark.pos, mark.particle, 12, mark.radius * 0.36);
                mark.impact.accept(this, near(mark.pos, mark.radius, 2.5));
                iterator.remove();
            } else if ((mark.due - elapsed) % 5 == 0) {
                ring(mark.pos, mark.radius, mark.particle);
            }
        }
        return elapsed + 1 < duration || !marks.isEmpty();
    }

    @Override
    public void close() {
        for (EntityLiving mob : mobs) {
            LIVE_MOBS.remove(mob);
            mob.setDead();
        }
    }

    static boolean isAbandonedMob(Entity entity) {
        return entity instanceof EntityMob && entity.getEntityData().getBoolean(TEMPORARY_MOB)
            && !LIVE_MOBS.contains(entity);
    }

    static void setMobsPaused(boolean paused) {
        for (EntityLiving mob : LIVE_MOBS) {
            mob.setNoAI(paused);
            if (paused) {
                mob.getNavigator().clearPath();
            }
        }
    }

    boolean loaded(BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < world.getActualHeight()
            && world.isBlockLoaded(pos) && world.getWorldBorder().contains(pos);
    }

    BlockPos offset(BlockPos center, double distance, double angle) {
        return center.add(Math.round(Math.cos(angle) * distance), 0, Math.round(Math.sin(angle) * distance));
    }

    BlockPos surface(BlockPos pos) {
        if (!loaded(pos)) {
            return null;
        }
        BlockPos top = world.getPrecipitationHeight(pos);
        return loaded(top) ? top : null;
    }

    static boolean metalArmor(EntityPlayerMP player) {
        for (ItemStack stack : player.getArmorInventoryList()) {
            if (stack.getItem() instanceof ItemArmor) {
                ItemArmor.ArmorMaterial material = ((ItemArmor) stack.getItem()).getArmorMaterial();
                if (material == ItemArmor.ArmorMaterial.IRON || material == ItemArmor.ArmorMaterial.GOLD
                    || material == ItemArmor.ArmorMaterial.CHAIN) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean exposed() {
        return exposed(player);
    }

    static boolean exposed(EntityPlayerMP player) {
        BlockPos pos = player.getPosition();
        return pos.getY() >= player.world.getPrecipitationHeight(pos).getY();
    }

    boolean near(BlockPos pos, double radius, double height) {
        return Math.abs(player.posY - pos.getY()) <= height
            && DisasterEvents.horizontalDistanceSq(player, pos) <= radius * radius;
    }

    void mark(BlockPos pos, double radius, int delay, EnumParticleTypes particle, Consumer<ScenarioRun> hit) {
        markImpact(pos, radius, delay, particle, (run, hitPlayer) -> {
            if (hitPlayer) {
                hit.accept(run);
            }
        });
    }

    void markImpact(BlockPos pos, double radius, int delay, EnumParticleTypes particle,
                    BiConsumer<ScenarioRun, Boolean> impact) {
        if (pos != null && loaded(pos) && marks.size() < MAX_MARKS) {
            marks.add(new Mark(pos, radius, elapsed + delay, particle, impact));
        }
    }

    void ring(BlockPos pos, double radius, EnumParticleTypes type) {
        if (!loaded(pos)) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            world.spawnParticle(player, type, true, pos.getX() + 0.5 + Math.cos(angle) * radius, pos.getY() + 0.25,
                pos.getZ() + 0.5 + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    void cloud(BlockPos pos, EnumParticleTypes type, int count, double spread) {
        if (loaded(pos)) {
            world.spawnParticle(player, type, true, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, count,
                spread, 0.4, spread, 0.04);
        }
    }

    void sound(BlockPos pos, SoundEvent sound, float pitch) {
        world.playSound(null, pos, sound, SoundCategory.AMBIENT, 1.5F, pitch);
    }

    void message(String text) {
        player.sendStatusMessage(new TextComponentString("§e" + text), true);
    }

    void potion(Potion potion, int ticks, int amplifier) {
        player.addPotionEffect(new PotionEffect(potion, ticks, amplifier));
    }

    void hurt(DamageSource source, float amount) {
        player.attackEntityFrom(source, amount);
    }

    void push(double x, double y, double z) {
        if (!player.capabilities.isFlying && !player.isRiding()) {
            player.addVelocity(x, y, z);
            player.velocityChanged = true;
        }
    }

    void radial(BlockPos pos, double force, double y) {
        double x = player.posX - pos.getX() - 0.5;
        double z = player.posZ - pos.getZ() - 0.5;
        double length = Math.max(0.5, Math.hypot(x, z));
        push(x * force / length, y, z * force / length);
    }

    void give(ItemStack stack) {
        player.addItemStackToInventory(stack);
        if (!stack.isEmpty()) {
            player.dropItem(stack, false, true);
        }
        sound(player.getPosition(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.1F);
    }

    void spawn(Function<WorldServer, ? extends EntityLiving> factory, double angle, int distance) {
        mobs.removeIf(mob -> {
            if (!mob.isDead) {
                return false;
            }
            LIVE_MOBS.remove(mob);
            return true;
        });
        if (mobs.size() >= MAX_MOBS) {
            return;
        }
        BlockPos center = player.getPosition();
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos pos = offset(center, distance - attempt / 4, angle + attempt * 0.73);
            // Search at the player's elevation first, including inside caves and buildings.
            for (int dy : SPAWN_HEIGHTS) {
                if (trySpawn(factory, pos.up(dy))) {
                    return;
                }
            }
            BlockPos surface = surface(pos);
            if (surface != null) {
                int difference = Math.abs(surface.getY() - pos.getY());
                if (difference > 2 && difference <= 10 && trySpawn(factory, surface)) {
                    return;
                }
            }
        }
    }

    private boolean trySpawn(Function<WorldServer, ? extends EntityLiving> factory, BlockPos pos) {
        if (!loaded(pos) || !world.getBlockState(pos.down()).getMaterial().blocksMovement()
            || !world.isAirBlock(pos) || !world.isAirBlock(pos.up())) {
            return false;
        }
        EntityLiving mob = factory.apply(world);
        mob.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (!world.checkNoEntityCollision(mob.getEntityBoundingBox(), mob)
            || !world.getCollisionBoxes(mob, mob.getEntityBoundingBox()).isEmpty()) {
            return false;
        }
        mob.getEntityData().setBoolean(TEMPORARY_MOB, true);
        if (!player.capabilities.disableDamage) {
            mob.setAttackTarget(player);
        }
        LIVE_MOBS.add(mob);
        boolean spawned = false;
        try {
            spawned = world.spawnEntity(mob);
            if (!spawned) {
                return false;
            }
        } finally {
            if (!spawned) {
                LIVE_MOBS.remove(mob);
            }
        }
        mobs.add(mob);
        cloud(pos, EnumParticleTypes.SMOKE_LARGE, 8, 0.4);
        return true;
    }

    void rally(Potion effect, int ticks) {
        for (EntityLiving mob : mobs) {
            if (!mob.isDead) {
                mob.addPotionEffect(new PotionEffect(effect, ticks, level - 1));
                cloud(mob.getPosition(), EnumParticleTypes.CRIT_MAGIC, 8, 0.4);
            }
        }
    }
}
