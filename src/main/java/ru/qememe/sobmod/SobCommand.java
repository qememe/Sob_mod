package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

final class SobCommand extends CommandBase {
    private static final int EVENTS_PER_PAGE = 10;
    private static final DisasterEvents.Kind[] KINDS = DisasterEvents.Kind.values();
    private static final List<String> EVENT_IDS = Arrays.stream(KINDS)
        .map(DisasterEvents.Kind::id).collect(Collectors.toList());

    private final DisasterEvents disasters;
    private final EventScheduler scheduler;

    SobCommand(DisasterEvents disasters, EventScheduler scheduler) {
        this.disasters = disasters;
        this.scheduler = scheduler;
    }

    @Override
    public String getName() {
        return "sobmod";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sobmod list [страница] | /sobmod start <событие> [1–3] [игрок|all]"
            + " | /sobmod pause | /sobmod resume";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        if (args[0].equalsIgnoreCase("list")) {
            listEvents(sender, args);
            return;
        }
        boolean pause = args[0].equalsIgnoreCase("pause");
        if (pause || args[0].equalsIgnoreCase("resume")) {
            if (args.length != 1) {
                throw new WrongUsageException(getUsage(sender));
            }
            String message;
            if (scheduler.setPaused(pause)) {
                message = pause ? "События поставлены на паузу." : "События возобновлены.";
            } else {
                message = pause ? "События уже на паузе." : "События уже идут.";
            }
            sender.sendMessage(new TextComponentString("§a[SobMod] " + message));
            return;
        }

        String[] eventArgs = args[0].equalsIgnoreCase("start")
            ? Arrays.copyOfRange(args, 1, args.length) : args;
        startEvent(server, sender, eventArgs);
    }

    private void listEvents(ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        int pages = (KINDS.length + EVENTS_PER_PAGE - 1) / EVENTS_PER_PAGE;
        int page = args.length == 2 ? parseInt(args[1], 1, pages) : 1;
        sender.sendMessage(new TextComponentString("§6[SobMod] События · " + page + "/" + pages));
        int first = (page - 1) * EVENTS_PER_PAGE;
        for (int index = first; index < Math.min(first + EVENTS_PER_PAGE, KINDS.length); index++) {
            DisasterEvents.Kind kind = KINDS[index];
            sender.sendMessage(new TextComponentString("§e" + kind.id() + " §7— §f" + kind.title));
        }
    }

    private void startEvent(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 3) {
            throw new WrongUsageException(getUsage(sender));
        }
        if (scheduler.isPaused()) {
            throw new CommandException("SobMod: события на паузе. Для продолжения используй /sobmod resume.");
        }
        DisasterEvents.Kind kind = Arrays.stream(KINDS)
            .filter(candidate -> candidate.id().equalsIgnoreCase(args[0]))
            .findFirst()
            .orElseThrow(() -> new WrongUsageException(getUsage(sender)));
        boolean hasLevel = args.length >= 2 && args[1].matches("-?\\d+");
        int level = hasLevel ? parseInt(args[1], 1, 3) : 1;
        int targetIndex = hasLevel ? 2 : 1;
        if (args.length > targetIndex + 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP target = args.length > targetIndex && !args[targetIndex].equalsIgnoreCase("all")
            ? getPlayer(server, sender, args[targetIndex])
            : args.length == 1 && sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
        if (disasters.isRunning(server)) {
            throw new CommandException("SobMod: дождись завершения текущего события.");
        }
        if (EventHistory.get(server).contains(kind)) {
            throw new CommandException("SobMod: этот тип уже был среди трёх последних событий.");
        }
        if (!disasters.start(kind, server, target, level)) {
            throw new CommandException("SobMod: нет подходящего игрока или места для этого события.");
        }
        sender.sendMessage(new TextComponentString("§a[SobMod] Событие запущено — " + kind.title
            + (kind == DisasterEvents.Kind.FIRE ? "" : ", уровень " + level) + "."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> choices = new ArrayList<>(EVENT_IDS);
            choices.addAll(Arrays.asList("start", "list", "pause", "resume"));
            return getListOfStringsMatchingLastWord(args, choices);
        }
        if (args[0].equalsIgnoreCase("list") && args.length == 2) {
            List<String> pages = new ArrayList<>();
            for (int index = 0; index < KINDS.length; index += EVENTS_PER_PAGE) {
                pages.add(Integer.toString(index / EVENTS_PER_PAGE + 1));
            }
            return getListOfStringsMatchingLastWord(args, pages);
        }
        boolean explicitStart = args[0].equalsIgnoreCase("start");
        int eventIndex = explicitStart ? 1 : 0;
        if (explicitStart && args.length == 2) {
            return getListOfStringsMatchingLastWord(args, EVENT_IDS);
        }
        if (EVENT_IDS.stream().noneMatch(id -> id.equalsIgnoreCase(args[eventIndex]))) {
            return Collections.emptyList();
        }
        int eventArgCount = args.length - eventIndex;
        if (eventArgCount == 2 || (eventArgCount == 3 && args[eventIndex + 1].matches("[1-3]"))) {
            List<String> choices = new ArrayList<>(Arrays.asList(server.getOnlinePlayerNames()));
            choices.add("all");
            if (eventArgCount == 2) {
                choices.addAll(Arrays.asList("1", "2", "3"));
            }
            return getListOfStringsMatchingLastWord(args, choices);
        }
        return Collections.emptyList();
    }
}
