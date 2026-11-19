package ru.qememe.sobmod.client;

import net.minecraft.block.BlockLiquid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.qememe.sobmod.EntityDisasterDrop;
import ru.qememe.sobmod.EntityFab;
import ru.qememe.sobmod.EntityPursuer;
import ru.qememe.sobmod.SobMod;
import ru.qememe.sobmod.PursuerEquipment;
import ru.qememe.sobmod.TemporaryLava;
import ru.qememe.sobmod.TsunamiWater;

public final class ClientSetup {
    private ClientSetup() {}

    public static void registerRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntityFab.class, RenderFab::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityDisasterDrop.class, RenderDisasterDrop::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityPursuer.class, RenderPursuer::new);
        ModelLoader.setCustomStateMapper(TemporaryLava.rainBlock(), new StateMap.Builder().ignore(BlockLiquid.LEVEL).build());
        ModelLoader.setCustomStateMapper(TsunamiWater.WATER, new StateMap.Builder().ignore(BlockLiquid.LEVEL).build());
        ModelLoader.setCustomModelResourceLocation(PursuerEquipment.FIREARM, 0,
            new ModelResourceLocation(PursuerEquipment.FIREARM.getRegistryName(), "inventory"));
        MinecraftForge.EVENT_BUS.register(new ClientSetup());
    }

    public static void registerWaterColor() {
        BlockColors colors = Minecraft.getMinecraft().getBlockColors();
        colors.registerBlockColorHandler((state, world, pos, tint) -> colors.colorMultiplier(
            Blocks.WATER.getDefaultState(), world, pos, tint), TsunamiWater.WATER);
    }

    @SubscribeEvent
    public void stitchTextures(TextureStitchEvent.Pre event) {
        for (String face : new String[] {"top", "side", "bottom"}) {
            event.getMap().registerSprite(new ResourceLocation(SobMod.MOD_ID, "blocks/nuclear_tnt_" + face));
        }
    }
}
