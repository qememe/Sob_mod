package ru.qememe.sobmod;

import net.minecraft.entity.monster.EntityVindicator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

@Mod.EventBusSubscriber(modid = SobMod.MOD_ID)
public final class ModEntities {
    private ModEntities() {}

    @SubscribeEvent
    public static void register(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.<EntityFab>create()
            .entity(EntityFab.class)
            .id(new ResourceLocation(SobMod.MOD_ID, "fab"), 0)
            .name(SobMod.MOD_ID + ".fab")
            .tracker(160, 1, false)
            .build());
        event.getRegistry().register(EntityEntryBuilder.<EntityDisasterDrop>create()
            .entity(EntityDisasterDrop.class)
            .id(new ResourceLocation(SobMod.MOD_ID, "disaster_drop"), 1)
            .name(SobMod.MOD_ID + ".disaster_drop")
            .tracker(160, 1, false)
            .build());
        event.getRegistry().register(EntityEntryBuilder.<EntityPursuer>create()
            .entity(EntityPursuer.class)
            .id(new ResourceLocation(SobMod.MOD_ID, "pursuer"), 2)
            .name(SobMod.MOD_ID + ".pursuer")
            .tracker(96, 2, true)
            .build());
    }

    @SubscribeEvent
    public static void discardAbandonedMob(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote && ScenarioRun.isAbandonedMob(event.getEntity())) {
            // A deserialized mob may already be in the chunk even when world insertion is canceled.
            event.getEntity().setDead();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void updatePursuer(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityVindicator) || event.getEntityLiving().world.isRemote) {
            return;
        }
        EntityVindicator pursuer = (EntityVindicator) event.getEntityLiving();
        String expiryKey = DisasterEvents.PURSUER_EXPIRY;
        if (pursuer.getEntityData().hasKey(expiryKey)
            && pursuer.world.getTotalWorldTime() >= pursuer.getEntityData().getLong(expiryKey)) {
            pursuer.setDead();
        }
    }
}
