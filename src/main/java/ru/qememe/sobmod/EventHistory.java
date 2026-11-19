package ru.qememe.sobmod;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public final class EventHistory extends WorldSavedData {
    private static final String NAME = "sobmod_event_history";
    private static final int WINDOW = 3;
    private final Deque<DisasterEvents.Kind> recent = new ArrayDeque<>();

    public EventHistory(String name) {
        super(name);
    }

    static EventHistory get(MinecraftServer server) {
        MapStorage storage = server.getWorld(0).getMapStorage();
        EventHistory history = (EventHistory) storage.getOrLoadData(EventHistory.class, NAME);
        if (history == null) {
            history = new EventHistory(NAME);
            storage.setData(NAME, history);
        }
        return history;
    }

    boolean contains(DisasterEvents.Kind kind) {
        return recent.contains(kind);
    }

    void record(DisasterEvents.Kind kind) {
        if (recent.size() == WINDOW) {
            recent.removeFirst();
        }
        recent.addLast(kind);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        recent.clear();
        NBTTagList list = tag.getTagList("recent", 8);
        for (int i = Math.max(0, list.tagCount() - WINDOW); i < list.tagCount(); i++) {
            String id = list.getStringTagAt(i);
            for (DisasterEvents.Kind kind : DisasterEvents.Kind.values()) {
                if (kind.id().equals(id)) {
                    recent.addLast(kind);
                    break;
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (DisasterEvents.Kind kind : recent) {
            list.appendTag(new NBTTagString(kind.id()));
        }
        tag.setTag("recent", list);
        return tag;
    }
}
