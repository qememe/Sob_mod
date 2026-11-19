package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;

public final class EventScheduler {
    private final MinecraftServer server;
    private final List<ServerEvent> events;
    private final DisasterEvents disasters;
    private final long intervalNanos;
    private final Logger logger;
    private final BossInfoServer countdown = new BossInfoServer(new TextComponentString(""),
        BossInfo.Color.BLUE, BossInfo.Overlay.PROGRESS);
    private long lastRunNanos;
    private long pausedAtNanos;
    private boolean paused;
    private int ticksUntilBarUpdate;

    EventScheduler(MinecraftServer server, List<ServerEvent> events, DisasterEvents disasters,
                   int intervalSeconds, Logger logger) {
        this.server = server;
        this.events = new ArrayList<>(events);
        this.disasters = disasters;
        this.intervalNanos = TimeUnit.SECONDS.toNanos(intervalSeconds);
        this.logger = logger;
        this.lastRunNanos = System.nanoTime();
        // Spawn chunks and their saved debris may have loaded before the scheduler registered.
        for (WorldServer world : server.worlds) {
            world.loadedEntityList.forEach(disasters::trackFlight);
        }
    }

    boolean isPaused() {
        return paused;
    }

    boolean setPaused(boolean pause) {
        if (paused == pause) {
            return false;
        }
        long now = System.nanoTime();
        if (pause) {
            pausedAtNanos = now;
        } else {
            lastRunNanos += now - pausedAtNanos;
        }
        paused = pause;
        ScenarioRun.setMobsPaused(pause);
        updateBar(now);
        return true;
    }

    void close() {
        countdown.setVisible(false);
        disasters.close(server);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote) {
            disasters.trackFlight(event.getEntity());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent tick) {
        if (tick.phase != TickEvent.Phase.END) {
            return;
        }

        disasters.tick(server, paused);

        long now = System.nanoTime();
        if (!paused && now - lastRunNanos >= intervalNanos && !disasters.isRunning(server)) {
            lastRunNanos = now;
            runScheduledEvent();
            ticksUntilBarUpdate = 0;
        }
        if (ticksUntilBarUpdate-- <= 0) {
            updateBar(now);
            ticksUntilBarUpdate = 4;
        }
    }

    private void updateBar(long now) {
        long elapsed = (paused ? pausedAtNanos : now) - lastRunNanos;
        long remaining = Math.max(0, intervalNanos - elapsed);
        long seconds = (remaining + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1);
        boolean waiting = remaining == 0 && disasters.isRunning(server);
        String title = waiting
            ? (paused ? "Текущее событие на паузе" : "Ожидаем завершения текущего события")
            : String.format(Locale.ROOT, "%s %d:%02d", paused ? "События на паузе · осталось"
                : "До следующего события", seconds / 60, seconds % 60);
        if (!title.equals(countdown.getName().getUnformattedText())) {
            countdown.setName(new TextComponentString(title));
        }
        countdown.setColor(paused ? BossInfo.Color.YELLOW : BossInfo.Color.BLUE);
        countdown.setPercent((float) remaining / intervalNanos);

        List<EntityPlayerMP> online = server.getPlayerList().getPlayers();
        for (EntityPlayerMP player : new ArrayList<>(countdown.getPlayers())) {
            if (!online.contains(player)) {
                countdown.removePlayer(player);
            }
        }
        online.forEach(countdown::addPlayer);
    }

    private void runScheduledEvent() {
        List<ServerEvent> available = events.stream()
            .filter(event -> event.canRun(server))
            .collect(Collectors.toList());
        if (available.isEmpty()) {
            logger.debug("SobMod scheduler found no eligible targets for any event.");
            return;
        }
        ServerEvent event = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        try {
            logger.debug("SobMod scheduled event {}.", event.getId());
            event.execute(server);
        } catch (RuntimeException exception) {
            logger.error("SobMod event '{}' failed.", event.getId(), exception);
        }
    }
}
