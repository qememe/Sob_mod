package ru.qememe.sobmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

final class FlightChunks {
    private static final Map<World, FlightChunks> WORLDS = new WeakHashMap<>();
    private static SobMod mod;

    private final Map<ChunkPos, HeldChunk> chunks = new HashMap<>();
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<>();

    private static final class HeldChunk {
        final ForgeChunkManager.Ticket ticket;
        int users = 1;

        HeldChunk(ForgeChunkManager.Ticket ticket) {
            this.ticket = ticket;
        }
    }

    private FlightChunks() {}

    static void init(SobMod instance) {
        mod = instance;
        ForgeChunkManager.setForcedChunkLoadingCallback(mod, (tickets, world) -> {
            for (ForgeChunkManager.Ticket ticket : tickets) {
                ForgeChunkManager.releaseTicket(ticket);
            }
        });
    }

    static boolean hold(World world, ChunkPos pos) {
        FlightChunks state = WORLDS.computeIfAbsent(world, key -> new FlightChunks());
        HeldChunk existing = state.chunks.get(pos);
        if (existing != null) {
            existing.users++;
            return true;
        }
        ForgeChunkManager.Ticket ticket = state.tickets.stream()
            .filter(candidate -> candidate.getChunkList().size() < candidate.getChunkListDepth())
            .findFirst().orElse(null);
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(mod, world, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) {
                return false;
            }
            if (ticket.getChunkListDepth() == 0) {
                ForgeChunkManager.releaseTicket(ticket);
                return false;
            }
            state.tickets.add(ticket);
        }
        ForgeChunkManager.forceChunk(ticket, pos);
        state.chunks.put(pos, new HeldChunk(ticket));
        return true;
    }

    static void release(World world, ChunkPos pos) {
        FlightChunks state = WORLDS.get(world);
        if (state == null) {
            return;
        }
        HeldChunk chunk = state.chunks.get(pos);
        if (chunk == null || --chunk.users > 0) {
            return;
        }
        state.chunks.remove(pos);
        ForgeChunkManager.unforceChunk(chunk.ticket, pos);
        if (chunk.ticket.getChunkList().isEmpty()) {
            ForgeChunkManager.releaseTicket(chunk.ticket);
            state.tickets.remove(chunk.ticket);
        }
        if (state.chunks.isEmpty()) {
            WORLDS.remove(world);
        }
    }

    static void stop() {
        for (FlightChunks state : WORLDS.values()) {
            state.tickets.forEach(ForgeChunkManager::releaseTicket);
        }
        WORLDS.clear();
    }
}
