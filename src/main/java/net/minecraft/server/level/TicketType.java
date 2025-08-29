package net.minecraft.server.level;

import net.minecraft.core.IRegistry;
import net.minecraft.core.registries.BuiltInRegistries;

public record TicketType(long timeout, boolean persist, TicketType.a use) {

    public static final long NO_TIMEOUT = 0L;
    public static final TicketType START = register("start", 0L, false, TicketType.a.LOADING_AND_SIMULATION);
    public static final TicketType DRAGON = register("dragon", 0L, false, TicketType.a.LOADING_AND_SIMULATION);
    public static final TicketType PLAYER_LOADING = register("player_loading", 0L, false, TicketType.a.LOADING);
    public static final TicketType PLAYER_SIMULATION = register("player_simulation", 0L, false, TicketType.a.SIMULATION);
    public static final TicketType FORCED = register("forced", 0L, true, TicketType.a.LOADING_AND_SIMULATION);
    public static final TicketType PORTAL = register("portal", 300L, true, TicketType.a.LOADING_AND_SIMULATION);
    public static final TicketType ENDER_PEARL = register("ender_pearl", 40L, false, TicketType.a.LOADING_AND_SIMULATION);
    public static final TicketType UNKNOWN = register("unknown", 1L, false, TicketType.a.LOADING);
    // CraftBukkit start
    public static long pluginTimeout = 0L;
    public static final TicketType PLUGIN = register("plugin", 0L, false, TicketType.a.LOADING_AND_SIMULATION);
    public static final TicketType PLUGIN_TICKET = register("plugin_ticket", 0L, false, TicketType.a.LOADING_AND_SIMULATION);

    @Override
    public long timeout() {
        return (this != TicketType.PLUGIN) ? this.timeout : TicketType.pluginTimeout;
    }
    // CraftBukkit end

    private static TicketType register(String s, long i, boolean flag, TicketType.a tickettype_a) {
        return (TicketType) IRegistry.register(BuiltInRegistries.TICKET_TYPE, s, new TicketType(i, flag, tickettype_a));
    }

    public boolean doesLoad() {
        return this.use == TicketType.a.LOADING || this.use == TicketType.a.LOADING_AND_SIMULATION;
    }

    public boolean doesSimulate() {
        return this.use == TicketType.a.SIMULATION || this.use == TicketType.a.LOADING_AND_SIMULATION;
    }

    public boolean hasTimeout() {
        return this.timeout != 0L;
    }

    public static enum a {

        LOADING, SIMULATION, LOADING_AND_SIMULATION;

        private a() {}
    }
}
