package ru.qememe.sobmod;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class EntityPursuer extends EntityMob {
    public EntityPursuer(World world) {
        super(world);
        setSize(0.6F, 1.8F);
    }

    EntityPursuer(World world, BlockPos pos, int level) {
        this(world);
        setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.rand.nextFloat() * 360, 0);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(16 + level * 4);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(2 + level * 2);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.22 + level * 0.035);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(30 + level * 10);
        setHealth(getMaxHealth());
        setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(level == 1 ? Items.WOODEN_SWORD : PursuerEquipment.FIREARM));
        setDropChance(EntityEquipmentSlot.MAINHAND, 0);
        setCanPickUpLoot(false);
    }

    boolean fireAt(EntityPlayerMP player, int level) {
        WorldServer server = (WorldServer) world;
        Vec3d muzzle = getPositionEyes(1);
        Vec3d target = new Vec3d(player.posX, player.posY + player.height * 0.68, player.posZ);
        if (world.rayTraceBlocks(muzzle, target, false, true, false) != null) {
            return false;
        }
        swingArm(EnumHand.MAIN_HAND);
        world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_FIREWORK_BLAST, SoundCategory.HOSTILE,
            1.55F, level == 3 ? 1.12F : 0.86F);
        server.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, muzzle.x, muzzle.y, muzzle.z, 3, 0.12, 0.08, 0.12, 0.02);
        Vec3d delta = target.subtract(muzzle);
        for (int i = 1; i <= 4; i++) {
            Vec3d tracer = muzzle.add(delta.scale(i / 5.0));
            server.spawnParticle(EnumParticleTypes.CRIT, tracer.x, tracer.y, tracer.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        player.attackEntityFrom(DamageSource.causeMobDamage(this).setProjectile(), 4.0F);
        return true;
    }

    @Override
    public void onUpdate() {
        if (!SobMod.eventsPaused(world)) {
            super.onUpdate();
        }
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected boolean canDropLoot() {
        return false;
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false;
    }
}
