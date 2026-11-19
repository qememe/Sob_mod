package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ru.qememe.sobmod.ScenarioDefinition.Site;
import java.util.stream.Collectors;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import org.apache.logging.log4j.Logger;
import ru.qememe.sobmod.ScenarioDefinition.Site;

final class DisasterEvents {
    static final String PURSUER_EXPIRY = "sobmodPursuerExpiry";
    static final int WARNING_TICKS = 60;
    private static final int FLIGHT_CHECK_INTERVAL = 20;
    private static final int STALLED_FLIGHT_CHECKS = 5;
    private static final int[] METEOR_COUNTS = {20, 40, 45};
    private static final int[] LAVA_PERIODS = {24, 14, 8};
    private static final int[] TNT_PERIODS = {12, 8, 5};

    enum Kind {
        TSUNAMI("Цунами", "Поднимайся на возвышенность!"),
        METEORS("Метеоритный дождь", "Уходи из зоны падения!"),
        FAB("Прилёт ФАБ", "Самолёт приближается. Покинь место удара!"),
        NKVD("Побег от НКВД", "Скройся, продержись до конца погони или покинь её зону."),
        LAVA_RAIN("Дождь из лавы", "Найди укрытие с крышей!"),
        TNT_RAIN("Падение ТНТ", "Следи за небом и уходи от падающего ТНТ!"),
        FIRE("Пожар", "Огненное кольцо сжимается. Выбирайся из зоны!"),
        THUNDERHEADS(new ScenarioDefinition(18, 55, Site.SKY,
            ScenarioHazards::thunderheads),
            "Грозовые очаги", "Молния метит открытый участок. Уйди с отмеченного места или найди крышу."),
        HAIL(new ScenarioDefinition(16, 38, Site.SKY,
            ScenarioHazards::hail),
            "Ледяной град", "Уходи от кругов снежных частиц и ищи крышу."),
        ASHFALL(new ScenarioDefinition(18, 28, Site.SKY,
            ScenarioHazards::ashfall),
            "Пеплопад", "Под крышей чистый воздух. На бегу пепел быстрее истощает силы."),
        HEATWAVE(new ScenarioDefinition(18, 32, Site.SKY,
            ScenarioHazards::heatwave),
            "Жаркая волна", "В тени и воде безопаснее. Не спринтуй на открытом месте."),
        WHITEOUT(new ScenarioDefinition(16, 30, Site.SKY,
            ScenarioHazards::whiteout),
            "Белая мгла", "На открытом месте видимость падает и ноги вязнут. Найди укрытие."),
        ACID_DRIZZLE(new ScenarioDefinition(18, 36, Site.SKY,
            ScenarioHazards::acidDrizzle),
            "Кислотная морось", "Присядь или спрячься под крышей, чтобы избежать яда."),
        DUST_DEVIL(new ScenarioDefinition(16, 10, Site.SKY,
            ScenarioHazards::dustDevil),
            "Пыльный вихрь", "Вихрь движется по кругу. Держись подальше от столба пыли."),
        CROSSWIND(new ScenarioDefinition(16, 20, Site.SKY,
            ScenarioHazards::crosswind),
            "Шквальный ветер", "Порывы меняют направление. Присядь или уйди под крышу."),
        TREMORS(new ScenarioDefinition(15, 45, Site.ANY,
            ScenarioHazards::tremors),
            "Подземные толчки", "Выйди из круга или прыгай, когда земля содрогается."),
        FAULT_LINE(new ScenarioDefinition(17, 48, Site.ANY,
            ScenarioHazards::faultLine),
            "Огненный разлом", "Отмеченные трещины вспыхнут. Перейди на другую сторону до вспышки."),
        GEYSERS(new ScenarioDefinition(15, 32, Site.ANY,
            ScenarioHazards::geysers),
            "Гейзерное поле", "Круги брызг предупреждают о струях, которые подбрасывают в воздух."),
        SINKHOLE(new ScenarioDefinition(14, 12, Site.ANY,
            ScenarioHazards::sinkhole),
            "Воронка в грунте", "Тебя тянет к центру. Двигайся к краю круга."),
        FROSTFRONT(new ScenarioDefinition(17, 10, Site.ANY,
            ScenarioHazards::frostfront),
            "Морозный фронт", "Снежный фронт пересекает зону и замедляет при контакте."),
        SHOCK_RINGS(new ScenarioDefinition(15, 9, Site.ANY,
            ScenarioHazards::shockRings),
            "Ударные волны", "Кольца расходятся от центра. Перепрыгивай через них или выйди из зоны."),
        EMBER_SPIRAL(new ScenarioDefinition(16, 16, Site.ANY,
            ScenarioHazards::emberSpiral),
            "Спираль углей", "Огненные метки закручиваются вокруг исходной точки. Не попадай под вспышки."),
        STATIC_CHARGE(new ScenarioDefinition(16, 45, Site.WET,
            ScenarioHazards::staticCharge),
            "Статический разряд", "Вода и дождь проводят ток. Обсохни до следующего разряда."),
        BOG_PRESSURE(new ScenarioDefinition(16, 20, Site.WATER,
            ScenarioHazards::bogPressure),
            "Водоворот", "В воде тянет ко дну и труднее копать. Выбирайся на сушу."),
        CAVE_IN(new ScenarioDefinition(16, 37, Site.COVER,
            ScenarioHazards::caveIn),
            "Гулкий обвал", "Под крышей опасны отмеченные места. Иди по тоннелю или выбирайся наружу."),
        ECHO_BOMBS(new ScenarioDefinition(17, 25, Site.ANY,
            ScenarioHazards::echoBombs),
            "Взрывное эхо", "Взрывы повторяют твой маршрут с задержкой. Не возвращайся по своим следам."),
        MAGNETIC_SURGE(new ScenarioDefinition(15, 18, Site.METAL,
            ScenarioHazards::magneticSurge),
            "Магнитная буря", "Железная, золотая и кольчужная броня тянет к центру. Сними её."),
        UNDEAD_SIEGE(new ScenarioDefinition(24, 110, Site.HOSTILE,
            ScenarioAssaults::undeadSiege),
            "Осада мертвецов", "Зомби прибывают волнами. Поздние волны ускоряют и старых бойцов."),
        BONE_CROSSFIRE(new ScenarioDefinition(23, 110, Site.HOSTILE,
            ScenarioAssaults::boneCrossfire),
            "Костяной перекрёстный огонь", "Лучники входят с противоположных сторон и меняют ось атаки."),
        SPIDER_NEST(new ScenarioDefinition(21, 106, Site.HOSTILE,
            ScenarioAssaults::spiderNest),
            "Паучье гнездо", "Пещерные пауки идут по земле. Отмеченные участки опутывают ноги."),
        WITCH_CIRCLE(new ScenarioDefinition(25, 120, Site.HOSTILE,
            ScenarioAssaults::witchCircle),
            "Ведьмин круг", "Ведьмы обходят тебя по кругу. Фиолетовые метки ослабляют удары."),
        DESERT_PATROL(new ScenarioDefinition(23, 110, Site.HOSTILE,
            ScenarioAssaults::desertPatrol),
            "Песчаный дозор", "Кадавры идут строем. Спринт вблизи места тревоги вызывает голод."),
        FROZEN_ARCHERS(new ScenarioDefinition(24, 115, Site.HOSTILE,
            ScenarioAssaults::frozenArchers),
            "Ледяная засада", "Зимогоры стреляют замедляющими стрелами из-за зомби."),
        AXE_HUNT(new ScenarioDefinition(21, 100, Site.HOSTILE,
            ScenarioAssaults::axeHunt),
            "Охота топорников", "Поборники переходят на бег при каждой новой волне. Следи за их подсветкой."),
        VEX_BREACH(new ScenarioDefinition(18, 108, Site.HOSTILE,
            ScenarioAssaults::vexBreach),
            "Прорыв вредин", "Вредины пройдут сквозь стены. Белые метки подсвечивают цель."),
        NETHER_VANGUARD(new ScenarioDefinition(24, 116, Site.HOSTILE,
            ScenarioAssaults::netherVanguard),
            "Авангард Нижнего мира", "Свинозомби прикрывают скелета-иссушителя. Держи дистанцию от его меча."),
        ENDER_BROOD(new ScenarioDefinition(21, 102, Site.HOSTILE,
            ScenarioAssaults::enderBrood),
            "Выводок Края", "Чешуйницы Края наступают, а портальные метки тянут назад."),
        RUNNERS(new ScenarioDefinition(20, 95, Site.HOSTILE,
            ScenarioAssaults::runners),
            "Бегуны", "Маленькие зомби получают ускорение и заходят с новых направлений."),
        NIGHT_COMPANY(new ScenarioDefinition(25, 118, Site.HOSTILE,
            ScenarioAssaults::nightCompany),
            "Ночная рота", "Лучники, топорники и на высоких уровнях ведьмы получают временную защиту."),
        EVACUATION(new ScenarioDefinition(15, 20, Site.ANY,
            ScenarioTrials::evacuation),
            "Эвакуация", "Покинь опасную зону до финального взрыва и получи железо."),
        HOLDOUT(new ScenarioDefinition(14, 20, Site.ANY,
            ScenarioTrials::holdout),
            "Удержание точки", "Стой на земле внутри зелёного круга, чтобы заработать золото."),
        HIGH_GROUND(new ScenarioDefinition(18, 20, Site.ANY,
            ScenarioTrials::highGround),
            "На высоту", "Доберись до указанной высоты и стой на опоре к концу отсчёта."),
        BLACKOUT(new ScenarioDefinition(18, 24, Site.DARK,
            ScenarioTrials::blackout),
            "Свет погас", "Зажги факел или выйди на свет. Долгая темнота наносит урон."),
        NO_FOOTSTEPS(new ScenarioDefinition(16, 15, Site.ANY,
            ScenarioTrials::noFootsteps),
            "Не шуми", "Крадущийся шаг безопасен. Быстрое движение оставляет опасные метки."),
        KEEP_MOVING(new ScenarioDefinition(14, 20, Site.ANY,
            ScenarioTrials::keepMoving),
            "Не стой на месте", "Каждую секунду удаляйся на три блока от предыдущей точки."),
        RED_LIGHT(new ScenarioDefinition(18, 40, Site.ANY,
            ScenarioTrials::redLight, ScenarioTrials::redLightTick),
            "Красный свет", "Двигайся на зелёный сигнал и замирай на красный."),
        BREATHING_ROOM(new ScenarioDefinition(18, 24, Site.WATER,
            ScenarioTrials::breathingRoom),
            "Перехват дыхания", "Под водой воздух уходит быстрее. Присядь на суше перед погружением."),
        HOT_PURSUIT(new ScenarioDefinition(15, 20, Site.ANY,
            ScenarioTrials::hotPursuit),
            "Горячий след", "Огненная зона растёт от исходной точки. Беги за границу или зайди в воду."),
        WAYPOINTS(new ScenarioDefinition(27, 10, Site.ANY,
            ScenarioTrials::waypoints),
            "Маршрут сигнальщика", "Доберись до меток по порядку за изумруды. Координаты над панелью предметов."),
        SUPPLY_DROP(new ScenarioDefinition(20, 10, Site.ANY,
            ScenarioBoons::supplyDrop),
            "Сброс припасов", "Подойди к месту посадки за едой, стрелами и факелами."),
        STARFALL(new ScenarioDefinition(19, 46, Site.ANY,
            ScenarioBoons::starfall),
            "Падающие звёзды", "Встань в светящийся круг до падения звезды и получи опыт."),
        HEALING_SPRING(new ScenarioDefinition(15, 24, Site.ANY,
            ScenarioBoons::healingSpring),
            "Целебный источник", "Присядь внутри кольца сердец, чтобы восстановить здоровье."),
        TAILWIND(new ScenarioDefinition(15, 20, Site.SKY,
            ScenarioBoons::tailwind),
            "Попутный ветер", "Выбеги на открытое место и спринтуй для ускорения."),
        UPDRAFT(new ScenarioDefinition(15, 12, Site.SKY,
            ScenarioBoons::updraft),
            "Восходящий поток", "Зайди в облачное кольцо, чтобы подняться. Приседание прекращает подъём."),
        CLEANSING(new ScenarioDefinition(12, 24, Site.ANY,
            ScenarioBoons::cleansing),
            "Поляна очищения", "Присядь, чтобы потушить огонь, снять яд, иссушение и слепоту."),
        FORGE_BLESSING(new ScenarioDefinition(14, 20, Site.ANY,
            ScenarioBoons::forgeBlessing),
            "Дар кузницы", "Присядь с повреждённой вещью в основной руке. Прочность восстановится по частям."),
        DIVERS_GIFT(new ScenarioDefinition(16, 20, Site.WATER,
            ScenarioBoons::diversGift),
            "Дар ныряльщика", "Войди в воду для подводного дыхания, ночного зрения и ускорения добычи.");

        private final String id = name().toLowerCase(Locale.ROOT);
        final ScenarioDefinition scenario;
        final String title;
        final String warning;

        Kind(String title, String warning) {
            this(null, title, warning);
        }

        Kind(ScenarioDefinition scenario, String title, String warning) {
            this.scenario = scenario;
            this.title = title;
            this.warning = warning;
        }

        String id() {
            return id;
        }
    }

    interface Effect {
        boolean tick(int age);
        default void close() {}
    }

    private static final class ActiveEffect {
        final WorldServer world;
        final Effect effect;
        int age;

        ActiveEffect(WorldServer world, Effect effect) {
            this.world = world;
            this.effect = effect;
        }
    }

    private static final class FlightProgress {
        int age;
        int stalledChecks;

        FlightProgress(int age) {
            this.age = age;
        }
    }

    private final boolean terrainDamage;
    private final Logger logger;
    private final List<ActiveEffect> active = new ArrayList<>();
    private final Set<UUID> pursuits = new HashSet<>();
    private final Map<Entity, FlightProgress> flights = new HashMap<>();
    private int ticksUntilFlightCheck;

    DisasterEvents(boolean terrainDamage, Logger logger) {
        this.terrainDamage = terrainDamage;
        this.logger = logger;
    }

    List<ServerEvent> events() {
        List<ServerEvent> result = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            result.add(new ServerEvent() {
                @Override
                public String getId() {
                    return kind.id();
                }

                @Override
                public boolean canRun(MinecraftServer server) {
                    return !EventHistory.get(server).contains(kind)
                        && server.getPlayerList().getPlayers().stream().anyMatch(p -> canTargetAutomatically(kind, p));
                }

                @Override
                public void execute(MinecraftServer server) {
                    start(kind, server, null, 1 + server.getEntityWorld().rand.nextInt(3), true);
                }
            });
        }
        return result;
    }

    boolean isRunning(MinecraftServer server) {
        if (!active.isEmpty()) {
            return true;
        }
        for (WorldServer world : server.worlds) {
            if (FabBlast.hasPendingWork(world) || TemporaryLava.hasActiveFlow(world)) {
                return true;
            }
        }
        return flights.keySet().stream().anyMatch(DisasterEvents::isLoadedFlight);
    }

    private static boolean isLoadedFlight(Entity entity) {
        return !entity.isDead && entity.world.getEntityByID(entity.getEntityId()) == entity;
    }

    void trackFlight(Entity entity) {
        if (entity instanceof EntityDisasterDrop
            || (entity instanceof EntityFallingBlock && entity.getEntityData().getBoolean(EntityDisasterDrop.DEBRIS_TAG))) {
            flights.putIfAbsent(entity, new FlightProgress(entity.ticksExisted));
        }
    }

    private void checkFlights() {
        Iterator<Map.Entry<Entity, FlightProgress>> iterator = flights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, FlightProgress> entry = iterator.next();
            Entity entity = entry.getKey();
            FlightProgress progress = entry.getValue();
            if (!isLoadedFlight(entity)) {
                entity.setDead();
                iterator.remove();
            } else if (progress.age != entity.ticksExisted) {
                progress.age = entity.ticksExisted;
                progress.stalledChecks = 0;
            } else if (++progress.stalledChecks >= STALLED_FLIGHT_CHECKS) {
                // Vanilla may leave an entity loaded but stop ticking it at a chunk boundary.
                entity.setDead();
                iterator.remove();
            }
        }
    }

    boolean start(Kind kind, MinecraftServer server, EntityPlayerMP target, int level) {
        return start(kind, server, target, level, false);
    }

    private boolean start(Kind kind, MinecraftServer server, EntityPlayerMP target, int level, boolean automatic) {
        EventHistory history = EventHistory.get(server);
        if (isRunning(server) || history.contains(kind)) {
            return false;
        }
        boolean started = target == null ? startAll(kind, server, level, automatic) : startForTarget(kind, target, level);
        if (started) {
            history.record(kind);
        }
        return started;
    }

    private boolean startAll(Kind kind, MinecraftServer server, int level, boolean automatic) {
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers().stream()
            .filter(player -> automatic ? canTargetAutomatically(kind, player) : canTarget(kind, player))
            .collect(Collectors.toList());
        if (kind == Kind.FAB) {
            Collections.shuffle(players, server.getEntityWorld().rand);
            return players.stream().anyMatch(player -> startForTarget(kind, player, level));
        }
        boolean started = false;
        for (EntityPlayerMP player : players) {
            started |= startForTarget(kind, player, level);
        }
        return started;
    }

    private boolean canTarget(Kind kind, EntityPlayerMP player) {
        if (!isEligibleTarget(player)) {
            return false;
        }
        if (kind == Kind.NKVD) {
            return player.world.getDifficulty() != EnumDifficulty.PEACEFUL && !pursuits.contains(player.getUniqueID());
        }
        return kind.scenario == null || kind.scenario.canTarget(player);
    }

    private boolean canTargetAutomatically(Kind kind, EntityPlayerMP player) {
        return canTarget(kind, player) && (kind.scenario == null || kind.scenario.fitsSite(player));
    }

    static boolean isEligibleTarget(EntityPlayerMP player) {
        return player.isEntityAlive() && !player.isSpectator()
            && player.world.provider.isSurfaceWorld();
    }

    private boolean startForTarget(Kind kind, EntityPlayerMP target, int level) {
        if (!canTarget(kind, target)) {
            return false;
        }
        WorldServer world = target.getServerWorld();
        BlockPos center = target.getPosition();
        UUID id = target.getUniqueID();
        Effect effect;
        BlockPos launch = null;
        switch (kind) {
            case TSUNAMI:
                effect = new Tsunami(world, center, level);
                break;
            case METEORS:
                effect = rain(world, id, EntityDisasterDrop.Type.METEOR, level, 22, METEOR_COUNTS[level - 1], 100);
                break;
            case FAB:
                launch = randomLaunch(world, center, 100, EntityDisasterDrop.Type.FAB, level, 56);
                if (launch == null) {
                    return false;
                }
                effect = fab(world, center, launch, level);
                break;
            case NKVD:
                Pursuit pursuit = Pursuit.prepare(world, target, level, WARNING_TICKS, () -> pursuits.remove(id));
                if (pursuit == null) {
                    return false;
                }
                pursuits.add(id);
                effect = pursuit;
                break;
            case LAVA_RAIN:
                int lavaPeriod = LAVA_PERIODS[level - 1];
                effect = rain(world, id, EntityDisasterDrop.Type.LAVA, level, lavaPeriod, level * 1200 / lavaPeriod,
                    40 + 20 * level);
                break;
            case TNT_RAIN:
                int tntPeriod = TNT_PERIODS[level - 1];
                effect = rain(world, id, EntityDisasterDrop.Type.TNT, level, tntPeriod,
                    (2400 - 1) / tntPeriod + 1, 100);
                break;
            case FIRE:
                effect = new FireRing(world, center);
                break;
            default:
                effect = kind.scenario.create(target, level);
        }
        active.add(new ActiveEffect(world, effect));
        String warning = "§6[SobMod] " + kind.title
            + (kind == Kind.FIRE ? "" : " — уровень " + level) + ". " + kind.warning;
        if (kind == Kind.FAB) {
            warning = warning.replace(kind.title, "Прилёт " + FabBlast.designation(level));
            double alertRadius = FabBlast.shockRadius(level) + 16;
            for (EntityPlayerMP player : world.getPlayers(EntityPlayerMP.class, p -> true)) {
                if (horizontalDistanceSq(player, launch) <= alertRadius * alertRadius) {
                    player.sendMessage(new TextComponentString(warning));
                }
            }
            if (horizontalDistanceSq(target, launch) > alertRadius * alertRadius) {
                target.sendMessage(new TextComponentString(warning));
            }
        } else {
            target.sendMessage(new TextComponentString(warning));
        }
        world.playSound(null, center, SoundEvents.BLOCK_NOTE_BELL, SoundCategory.HOSTILE, 2.0F, 0.7F);
        return true;
    }

    private Effect rain(WorldServer world, UUID target, EntityDisasterDrop.Type type, int level,
                        int period, int count, int radius) {
        return age -> {
            EntityPlayerMP player = world.getMinecraftServer().getPlayerList().getPlayerByUUID(target);
            if (player == null || player.world != world || !isEligibleTarget(player)) {
                return false;
            }
            int elapsed = age - WARNING_TICKS - 1;
            if (elapsed < 0 || elapsed % period != 0) {
                return true;
            }
            int height = type == EntityDisasterDrop.Type.TNT ? 10 : type == EntityDisasterDrop.Type.LAVA ? 25 : 46;
            int dropRadius = type == EntityDisasterDrop.Type.TNT && elapsed / period % 3 == 0 ? 24 : radius;
            BlockPos launch = randomLaunch(world, player.getPosition(), dropRadius, type, level, height);
            if (launch != null) {
                EntityDisasterDrop drop = new EntityDisasterDrop(world, type, launch, level, terrainDamage);
                if (world.spawnEntity(drop)) {
                    drop.playLaunch();
                }
            }
            return elapsed / period + 1 < count;
        };
    }

    private Effect fab(WorldServer world, BlockPos center, BlockPos launch, int level) {
        return age -> {
            if (age < WARNING_TICKS) {
                return true;
            }
            double dx = launch.getX() - center.getX();
            double dz = launch.getZ() - center.getZ();
            if (dx == 0 && dz == 0) {
                dx = 1;
            }
            double distance = Math.hypot(dx, dz);
            EntityDisasterDrop aircraft = EntityDisasterDrop.aircraft(world, launch, level, terrainDamage,
                dx / distance, dz / distance, distance);
            if (world.spawnEntity(aircraft)) {
                aircraft.playLaunch();
            }
            return false;
        };
    }

    private static BlockPos randomLaunch(WorldServer world, BlockPos center, int radius,
                                         EntityDisasterDrop.Type type, int level, int height) {
        double width = EntityDisasterDrop.width(type, level);
        double entityHeight = EntityDisasterDrop.height(type, level);
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(world.rand.nextDouble()) * (radius - 1);
            BlockPos column = center.add(Math.round(Math.cos(angle) * distance), 0,
                Math.round(Math.sin(angle) * distance));
            int top = 0;
            boolean loaded = true;
            for (int x = (int) Math.floor(0.5 - width / 2); x < 0.5 + width / 2; x++) {
                for (int z = (int) Math.floor(0.5 - width / 2); z < 0.5 + width / 2; z++) {
                    BlockPos adjacent = column.add(x, 0, z);
                    if (!world.isBlockLoaded(adjacent) || !world.getWorldBorder().contains(adjacent)) {
                        loaded = false;
                        continue;
                    }
                    top = Math.max(top, world.getPrecipitationHeight(adjacent).getY());
                }
            }
            if (!loaded || top == 0) {
                continue;
            }
            int launchY = Math.min(top + height, world.getActualHeight() - (int) Math.ceil(entityHeight) - 1);
            int minimum = type == EntityDisasterDrop.Type.TNT ? height : Math.min(20, height);
            if (launchY - top >= minimum) {
                return new BlockPos(column.getX(), launchY, column.getZ());
            }
        }
        return null;
    }

    void tick(MinecraftServer server, boolean paused) {
        for (WorldServer world : server.worlds) {
            TsunamiWater.recover(world);
            TemporaryLava.tick(world, paused);
            if (!paused) {
                FabBlast.tick(world);
            }
        }
        if (paused) {
            return;
        }
        Iterator<ActiveEffect> iterator = active.iterator();
        while (iterator.hasNext()) {
            ActiveEffect task = iterator.next();
            boolean running = false;
            try {
                if (DimensionManager.getWorld(task.world.provider.getDimension()) == task.world) {
                    running = task.effect.tick(++task.age);
                }
            } catch (RuntimeException exception) {
                logger.error("SobMod interrupted an active event.", exception);
            }
            if (!running) {
                iterator.remove();
                closeEffect(task);
            }
        }
        if (--ticksUntilFlightCheck <= 0) {
            checkFlights();
            ticksUntilFlightCheck = FLIGHT_CHECK_INTERVAL;
        }
    }

    private void closeEffect(ActiveEffect task) {
        try {
            task.effect.close();
        } catch (RuntimeException exception) {
            logger.error("SobMod could not clean up an active event.", exception);
        }
    }

    void close(MinecraftServer server) {
        active.forEach(this::closeEffect);
        active.clear();
        for (Entity entity : flights.keySet()) {
            if (entity instanceof EntityDisasterDrop) {
                entity.setDead();
            }
        }
        flights.clear();
        for (WorldServer world : server.worlds) {
            TemporaryLava.stop(world);
        }
        FlightChunks.stop();
    }

    static double horizontalDistanceSq(Entity entity, BlockPos center) {
        double dx = entity.posX - center.getX() - 0.5;
        double dz = entity.posZ - center.getZ() - 0.5;
        return dx * dx + dz * dz;
    }
}
