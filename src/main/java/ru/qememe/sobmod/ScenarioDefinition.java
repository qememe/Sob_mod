package ru.qememe.sobmod;

import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.EnumDifficulty;

final class ScenarioDefinition {
    enum Site { ANY, SKY, COVER, WET, WATER, METAL, DARK, HOSTILE }

    private final int duration;
    private final int period;
    private final Site site;
    private final Consumer<ScenarioRun> pulse;
    private final Consumer<ScenarioRun> betweenPulses;

    ScenarioDefinition(int seconds, int period, Site site, Consumer<ScenarioRun> pulse) {
        this(seconds, period, site, pulse, null);
    }

    ScenarioDefinition(int seconds, int period, Site site, Consumer<ScenarioRun> pulse,
                       Consumer<ScenarioRun> betweenPulses) {
        this.duration = seconds * 20;
        this.period = period;
        this.site = site;
        this.pulse = pulse;
        this.betweenPulses = betweenPulses;
    }

    boolean canTarget(EntityPlayerMP player) {
        return site != Site.HOSTILE || player.world.getDifficulty() != EnumDifficulty.PEACEFUL;
    }

    boolean fitsSite(EntityPlayerMP player) {
        switch (site) {
            case SKY: return ScenarioRun.exposed(player);
            case COVER: return !ScenarioRun.exposed(player);
            case WET: return player.isWet();
            case WATER: return player.isInWater();
            case METAL: return ScenarioRun.metalArmor(player);
            case DARK: return player.world.getLightFromNeighbors(player.getPosition()) < 8;
            default: return true;
        }
    }

    ScenarioRun create(EntityPlayerMP player, int level) {
        return new ScenarioRun(player, level, duration, period, pulse, betweenPulses);
    }
}
