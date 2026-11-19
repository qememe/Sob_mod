package ru.qememe.sobmod;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = SobMod.MOD_ID)
public final class PursuerEquipment {
    public static final Item FIREARM = new Item()
        .setRegistryName(new ResourceLocation(SobMod.MOD_ID, "pursuer_firearm"))
        .setUnlocalizedName("sobmod.pursuer_firearm")
        .setMaxStackSize(1);

    private PursuerEquipment() {}

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(FIREARM);
    }
}
