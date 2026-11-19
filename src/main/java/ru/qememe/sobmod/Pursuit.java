package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;

final class Pursuit implements DisasterEvents.Effect {
    private static final int[] COUNTS = {2, 4, 6};
    private static final int[] RADII = {35, 55, 80};
    private static final int[] DURATIONS = {45 * 20, 70 * 20, 105 * 20};
    private static final int[] DETECTION = {45, 20, 8};
    private static final int[] SIGHT = {18, 27, 38};

    private final WorldServer world;
    private final UUID target;
    private final BlockPos center;
    private final int level;
    private final int warning;
    private final Runnable onClose;
    private final List<BlockPos> sites;
    private final List<Guard> guards = new ArrayList<>();
    private BlockPos shared;
    private int sharedAt;
    private boolean spawned;

    private static final class Guard {
        final EntityPursuer entity;
        final BlockPos post;
        BlockPos lastSeen;
        BlockPos searchOrigin;
        BlockPos waypoint;
        boolean reachedSearchOrigin;
        int nextSweep;
        int lastSeenAt;
        int seenTicks;
        int nextAttack;

        Guard(EntityPursuer entity, BlockPos post) {
            this.entity = entity;
            this.post = post;
        }
    }

    static Pursuit prepare(WorldServer world, EntityPlayerMP target, int level, int warning, Runnable onClose) {
        List<BlockPos> sites = new ArrayList<>();
        BlockPos center = target.getPosition();
        for (int attempt = 0; attempt < 140 && sites.size() < COUNTS[level - 1]; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2;
            int radius = 11 + world.rand.nextInt(7 + level * 2);
            BlockPos site = findStandingPosition(world,
                center.add(Math.round(Math.cos(angle) * radius), 0, Math.round(Math.sin(angle) * radius)));
            if (site != null && sites.stream().noneMatch(previous -> previous.distanceSq(site) < 16)) {
                sites.add(site);
            }
        }
        return sites.isEmpty() ? null : new Pursuit(world, target, level, warning, onClose, sites);
    }

    private Pursuit(WorldServer world, EntityPlayerMP player, int level, int warning, Runnable onClose,
                    List<BlockPos> sites) {
        this.world = world;
        this.target = player.getUniqueID();
        this.center = player.getPosition();
        this.level = level;
        this.warning = warning;
        this.onClose = onClose;
        this.sites = sites;
    }

    @Override
    public boolean tick(int age) {
        EntityPlayerMP player = world.getMinecraftServer().getPlayerList().getPlayerByUUID(target);
        if (player == null) {
            return false;
        }
        if (!player.isEntityAlive()) {
            return end(player, false, "Тебя поймали. Преследование проиграно.");
        }
        if (player.world != world || DisasterEvents.horizontalDistanceSq(player, center) > RADII[level - 1] * RADII[level - 1]) {
            return end(player, true, "Ты покинул зону преследования.");
        }
        if (!DisasterEvents.isEligibleTarget(player) || world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return end(player, false, "Преследование прервано.");
        }
        int elapsed = age - warning;
        if (elapsed < 0) {
            return true;
        }
        if (!spawned) {
            spawned = true;
            for (BlockPos site : sites) {
                if (canStand(world, site)) {
                    EntityPursuer guard = new EntityPursuer(world, site, level);
                    if (world.spawnEntity(guard)) {
                        guards.add(new Guard(guard, site));
                    }
                }
            }
            if (guards.isEmpty()) {
                return end(player, false, "Преследование прервано. Нет места для появления преследователей.");
            }
            player.sendMessage(new TextComponentString("§6[SobMod] Преследователей: " + guards.size()
                + ". Радиус зоны: " + RADII[level - 1] + " блоков. Время: " + DURATIONS[level - 1] / 20 + " с."));
        }
        if (elapsed >= DURATIONS[level - 1] || guards.stream().allMatch(guard -> guard.entity.isDead)) {
            return end(player, true, "Ты пережил преследование.");
        }
        for (Guard guard : guards) {
            if (!guard.entity.isDead) {
                updateGuard(guard, player, elapsed);
            }
        }
        return true;
    }

    private void updateGuard(Guard guard, EntityPlayerMP player, int age) {
        EntityPursuer npc = guard.entity;
        int sight = SIGHT[level - 1];
        double distance = npc.getDistanceSq(player);
        boolean visible = distance <= sight * sight && npc.getEntitySenses().canSee(player);
        guard.seenTicks = visible ? guard.seenTicks + 1 : Math.max(0, guard.seenTicks - 2);
        boolean tracking = visible && (guard.seenTicks >= DETECTION[level - 1]
            || (guard.lastSeen != null && age - guard.lastSeenAt <= 30));
        if (tracking) {
            guard.lastSeen = player.getPosition();
            guard.lastSeenAt = age;
            guard.searchOrigin = null;
            if (level == 3) {
                shared = guard.lastSeen;
                sharedAt = age;
            }
            npc.getLookHelper().setLookPositionWithEntity(player, 30, 30);
            if (age >= guard.nextAttack) {
                if (level == 1 && distance < 3.2) {
                    npc.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
                    npc.attackEntityAsMob(player);
                    guard.nextAttack = age + 20;
                } else if (level > 1 && npc.fireAt(player, level)) {
                    guard.nextAttack = age + (level == 2 ? 32 : 12);
                }
            }
        }
        if (age % 10 != 0) {
            return;
        }

        double speed = 0.9 + level * 0.13;
        if (tracking) {
            npc.getNavigator().tryMoveToEntityLiving(player, speed);
            return;
        }

        BlockPos search = guard.lastSeen != null && age - guard.lastSeenAt < 220 ? guard.lastSeen : null;
        if (level == 3 && shared != null && age - sharedAt < 240 && (search == null || sharedAt > guard.lastSeenAt)) {
            search = shared;
        }
        if (search == null) {
            moveTo(npc, guard.post, speed * 0.75);
            return;
        }
        if (!search.equals(guard.searchOrigin)) {
            guard.searchOrigin = search;
            guard.waypoint = null;
            guard.reachedSearchOrigin = false;
            guard.nextSweep = age;
        }
        if (!guard.reachedSearchOrigin) {
            if (npc.getDistanceSqToCenter(search) > 4 && moveTo(npc, search, speed)) {
                return;
            }
            guard.reachedSearchOrigin = true;
        }
        if (age < guard.nextSweep || (guard.waypoint != null && !npc.getNavigator().noPath())) {
            return;
        }
        guard.nextSweep = age + 20;
        BlockPos sweep = findStandingPosition(world,
            search.add(world.rand.nextInt(13) - 6, 0, world.rand.nextInt(13) - 6));
        if (sweep != null && center.distanceSq(sweep.getX(), center.getY(), sweep.getZ())
            <= RADII[level - 1] * RADII[level - 1]) {
            guard.waypoint = sweep;
            moveTo(npc, sweep, speed * 0.8);
        }
    }

    private static boolean moveTo(EntityPursuer npc, BlockPos pos, double speed) {
        return npc.getNavigator().tryMoveToXYZ(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    private static BlockPos findStandingPosition(WorldServer world, BlockPos column) {
        if (!world.isBlockLoaded(column)) {
            return null;
        }
        for (int offset = 0; offset <= 8; offset++) {
            if (canStand(world, column.up(offset))) {
                return column.up(offset);
            }
            if (offset > 0 && canStand(world, column.down(offset))) {
                return column.down(offset);
            }
        }
        BlockPos surface = world.getHeight(column);
        return canStand(world, surface) ? surface : null;
    }

    private static boolean canStand(WorldServer world, BlockPos pos) {
        return pos.getY() > 0 && pos.getY() < world.getActualHeight() - 1 && world.isBlockLoaded(pos)
            && world.getWorldBorder().contains(pos) && world.isAirBlock(pos) && world.isAirBlock(pos.up())
            && world.getBlockState(pos.down()).isTopSolid();
    }

    private boolean end(EntityPlayerMP player, boolean won, String message) {
        player.sendMessage(new TextComponentString((won ? "§a" : "§c") + "[SobMod] " + message));
        return false;
    }

    @Override
    public void close() {
        guards.forEach(guard -> guard.entity.setDead());
        onClose.run();
    }
}
