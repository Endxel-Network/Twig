package net.minecraft.server.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.SystemUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;

public class Ticket {

    public static final MapCodec<Ticket> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.TICKET_TYPE.byNameCodec().fieldOf("type").forGetter(Ticket::getType), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("level").forGetter(Ticket::getTicketLevel), Codec.LONG.optionalFieldOf("ticks_left", 0L).forGetter((ticket) -> {
            return ticket.ticksLeft;
        })).apply(instance, Ticket::new);
    });
    private final TicketType type;
    private final int ticketLevel;
    private long ticksLeft;
    // CraftBukkit start
    public Object key;

    public static Ticket of(TicketType tickettype, int i, Object key) {
        Ticket ticket = new Ticket(tickettype, i);
        ticket.key = key;
        return ticket;
    }
    // CraftBukkit end

    public Ticket(TicketType tickettype, int i) {
        this(tickettype, i, tickettype.timeout());
    }

    private Ticket(TicketType tickettype, int i, long j) {
        this.type = tickettype;
        this.ticketLevel = i;
        this.ticksLeft = j;
    }

    public String toString() {
        if (this.type.hasTimeout()) {
            String s = SystemUtils.getRegisteredName(BuiltInRegistries.TICKET_TYPE, this.type);

            return "Ticket[" + s + " " + this.ticketLevel + "] with " + this.ticksLeft + " ticks left ( out of" + this.type.timeout() + ")";
        } else {
            String s1 = SystemUtils.getRegisteredName(BuiltInRegistries.TICKET_TYPE, this.type);

            return "Ticket[" + s1 + " " + this.ticketLevel + "] with no timeout";
        }
    }

    public TicketType getType() {
        return this.type;
    }

    public int getTicketLevel() {
        return this.ticketLevel;
    }

    public void resetTicksLeft() {
        this.ticksLeft = this.type.timeout();
    }

    public void decreaseTicksLeft() {
        if (this.type.hasTimeout()) {
            --this.ticksLeft;
        }

    }

    public boolean isTimedOut() {
        return this.type.hasTimeout() && this.ticksLeft < 0L;
    }
}
