package ru.qememe.sobmod;

import net.minecraft.server.MinecraftServer;

public interface ServerEvent {
    String getId();

    default boolean canRun(MinecraftServer server) {
        return true;
    }

    void execute(MinecraftServer server);
}
