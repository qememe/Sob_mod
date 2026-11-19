package ru.qememe.sobmod;

import java.util.List;

import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.Logger;
import ru.qememe.sobmod.client.ClientSetup;

@Mod(
    modid = SobMod.MOD_ID,
    name = "SobMod",
    acceptedMinecraftVersions = "[1.12.2]"
)
public final class SobMod {
    public static final String MOD_ID = "sobmod";

    @Mod.Instance(MOD_ID)
    private static SobMod instance;

    static boolean eventsPaused(World world) {
        return !world.isRemote && instance != null && instance.scheduler != null && instance.scheduler.isPaused();
    }

    private Logger logger;
    private int intervalSeconds;
    private boolean terrainDamage;
    private EventScheduler scheduler;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        FlightChunks.init(this);

        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        intervalSeconds = config.getInt(
            "intervalSeconds", "events", 60, 1, Integer.MAX_VALUE,
            "Seconds between server events. Restart Minecraft or the dedicated server after editing."
        );
        terrainDamage = config.getBoolean(
            "terrainDamage", "events", true,
            "Allow meteors, FAB bombs and falling TNT to destroy blocks."
        );
        if (config.hasChanged()) {
            config.save();
        }

        if (event.getSide().isClient()) {
            ClientSetup.registerRenderers();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            ClientSetup.registerWaterColor();
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        DisasterEvents disasters = new DisasterEvents(terrainDamage, logger);
        List<ServerEvent> events = disasters.events();
        scheduler = new EventScheduler(event.getServer(), events, disasters, intervalSeconds, logger);
        event.registerServerCommand(new SobCommand(disasters, scheduler));
        MinecraftForge.EVENT_BUS.register(scheduler);
        logger.info("SobMod scheduler started with a {} second interval and {} events.",
            intervalSeconds, events.size());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (scheduler != null) {
            MinecraftForge.EVENT_BUS.unregister(scheduler);
            scheduler.close();
            scheduler = null;
        }
    }
}
