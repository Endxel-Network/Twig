package net.minecraft.world.level;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.PlayerChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.PersistentBase;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.Objects;
// CraftBukkit end

public class TicketStorage extends PersistentBase {

    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Pair<ChunkCoordIntPair, Ticket>> TICKET_ENTRY = Codec.mapPair(ChunkCoordIntPair.CODEC.fieldOf("chunk_pos"), Ticket.CODEC).codec();
    public static final Codec<TicketStorage> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(TicketStorage.TICKET_ENTRY.listOf().optionalFieldOf("tickets", List.of()).forGetter(TicketStorage::packTickets)).apply(instance, TicketStorage::fromPacked);
    });
    public static final SavedDataType<TicketStorage> TYPE = new SavedDataType<TicketStorage>("chunks", TicketStorage::new, TicketStorage.CODEC, DataFixTypes.SAVED_DATA_FORCED_CHUNKS);
    public final Long2ObjectOpenHashMap<List<Ticket>> tickets;
    private final Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets;
    private LongSet chunksWithForcedTickets;
    @Nullable
    private TicketStorage.a loadingChunkUpdatedListener;
    @Nullable
    private TicketStorage.a simulationChunkUpdatedListener;

    private TicketStorage(Long2ObjectOpenHashMap<List<Ticket>> long2objectopenhashmap, Long2ObjectOpenHashMap<List<Ticket>> long2objectopenhashmap1) {
        this.chunksWithForcedTickets = new LongOpenHashSet();
        this.tickets = long2objectopenhashmap;
        this.deactivatedTickets = long2objectopenhashmap1;
        this.updateForcedChunks();
    }

    public TicketStorage() {
        this(new Long2ObjectOpenHashMap(4), new Long2ObjectOpenHashMap());
    }

    private static TicketStorage fromPacked(List<Pair<ChunkCoordIntPair, Ticket>> list) {
        Long2ObjectOpenHashMap<List<Ticket>> long2objectopenhashmap = new Long2ObjectOpenHashMap();

        for (Pair<ChunkCoordIntPair, Ticket> pair : list) {
            ChunkCoordIntPair chunkcoordintpair = (ChunkCoordIntPair) pair.getFirst();
            List<Ticket> list1 = (List) long2objectopenhashmap.computeIfAbsent(chunkcoordintpair.toLong(), (i) -> {
                return new ObjectArrayList(4);
            });

            list1.add((Ticket) pair.getSecond());
        }

        return new TicketStorage(new Long2ObjectOpenHashMap(4), long2objectopenhashmap);
    }

    private List<Pair<ChunkCoordIntPair, Ticket>> packTickets() {
        List<Pair<ChunkCoordIntPair, Ticket>> list = new ArrayList();

        this.forEachTicket((chunkcoordintpair, ticket) -> {
            if (ticket.getType().persist()) {
                list.add(new Pair(chunkcoordintpair, ticket));
            }

        });
        return list;
    }

    private void forEachTicket(BiConsumer<ChunkCoordIntPair, Ticket> biconsumer) {
        forEachTicket(biconsumer, this.tickets);
        forEachTicket(biconsumer, this.deactivatedTickets);
    }

    private static void forEachTicket(BiConsumer<ChunkCoordIntPair, Ticket> biconsumer, Long2ObjectOpenHashMap<List<Ticket>> long2objectopenhashmap) {
        ObjectIterator objectiterator = Long2ObjectMaps.fastIterable(long2objectopenhashmap).iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();
            ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(long2objectmap_entry.getLongKey());

            for (Ticket ticket : long2objectmap_entry.getValue()) { // CraftBukkit - decompile error
                biconsumer.accept(chunkcoordintpair, ticket);
            }
        }

    }

    public void activateAllDeactivatedTickets() {
        ObjectIterator objectiterator = Long2ObjectMaps.fastIterable(this.deactivatedTickets).iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();

            for (Ticket ticket : long2objectmap_entry.getValue()) { // CraftBukkit - decompile error
                this.addTicket(long2objectmap_entry.getLongKey(), ticket);
            }
        }

        this.deactivatedTickets.clear();
    }

    public void setLoadingChunkUpdatedListener(@Nullable TicketStorage.a ticketstorage_a) {
        this.loadingChunkUpdatedListener = ticketstorage_a;
    }

    public void setSimulationChunkUpdatedListener(@Nullable TicketStorage.a ticketstorage_a) {
        this.simulationChunkUpdatedListener = ticketstorage_a;
    }

    public boolean hasTickets() {
        return !this.tickets.isEmpty();
    }

    public List<Ticket> getTickets(long i) {
        return (List) this.tickets.getOrDefault(i, List.of());
    }

    private List<Ticket> getOrCreateTickets(long i) {
        return (List) this.tickets.computeIfAbsent(i, (j) -> {
            return new ObjectArrayList(4);
        });
    }

    public void addTicketWithRadius(TicketType tickettype, ChunkCoordIntPair chunkcoordintpair, int i) {
        Ticket ticket = new Ticket(tickettype, ChunkLevel.byStatus(FullChunkStatus.FULL) - i);

        this.addTicket(chunkcoordintpair.toLong(), ticket);
    }

    public void addTicket(Ticket ticket, ChunkCoordIntPair chunkcoordintpair) {
        this.addTicket(chunkcoordintpair.toLong(), ticket);
    }

    public boolean addTicket(long i, Ticket ticket) {
        List<Ticket> list = this.getOrCreateTickets(i);

        for (Ticket ticket1 : list) {
            if (isTicketSameTypeAndLevel(ticket, ticket1)) {
                ticket1.resetTicksLeft();
                this.setDirty();
                return false;
            }
        }

        int j = getTicketLevelAt(list, true);
        int k = getTicketLevelAt(list, false);

        list.add(ticket);
        if (ticket.getType().doesSimulate() && ticket.getTicketLevel() < j && this.simulationChunkUpdatedListener != null) {
            this.simulationChunkUpdatedListener.update(i, ticket.getTicketLevel(), true);
        }

        if (ticket.getType().doesLoad() && ticket.getTicketLevel() < k && this.loadingChunkUpdatedListener != null) {
            this.loadingChunkUpdatedListener.update(i, ticket.getTicketLevel(), true);
        }

        if (ticket.getType().equals(TicketType.FORCED)) {
            this.chunksWithForcedTickets.add(i);
        }

        this.setDirty();
        return true;
    }

    private static boolean isTicketSameTypeAndLevel(Ticket ticket, Ticket ticket1) {
        return ticket1.getType() == ticket.getType() && ticket1.getTicketLevel() == ticket.getTicketLevel() && Objects.equals(ticket.key, ticket1.key); // CraftBukkit
    }

    public int getTicketLevelAt(long i, boolean flag) {
        return getTicketLevelAt(this.getTickets(i), flag);
    }

    private static int getTicketLevelAt(List<Ticket> list, boolean flag) {
        Ticket ticket = getLowestTicket(list, flag);

        return ticket == null ? ChunkLevel.MAX_LEVEL + 1 : ticket.getTicketLevel();
    }

    @Nullable
    private static Ticket getLowestTicket(@Nullable List<Ticket> list, boolean flag) {
        if (list == null) {
            return null;
        } else {
            Ticket ticket = null;

            for (Ticket ticket1 : list) {
                if (ticket == null || ticket1.getTicketLevel() < ticket.getTicketLevel()) {
                    if (flag && ticket1.getType().doesSimulate()) {
                        ticket = ticket1;
                    } else if (!flag && ticket1.getType().doesLoad()) {
                        ticket = ticket1;
                    }
                }
            }

            return ticket;
        }
    }

    public void removeTicketWithRadius(TicketType tickettype, ChunkCoordIntPair chunkcoordintpair, int i) {
        Ticket ticket = new Ticket(tickettype, ChunkLevel.byStatus(FullChunkStatus.FULL) - i);

        this.removeTicket(chunkcoordintpair.toLong(), ticket);
    }

    public void removeTicket(Ticket ticket, ChunkCoordIntPair chunkcoordintpair) {
        this.removeTicket(chunkcoordintpair.toLong(), ticket);
    }

    public boolean removeTicket(long i, Ticket ticket) {
        List<Ticket> list = (List) this.tickets.get(i);

        if (list == null) {
            return false;
        } else {
            boolean flag = false;
            Iterator<Ticket> iterator = list.iterator();

            while (iterator.hasNext()) {
                Ticket ticket1 = (Ticket) iterator.next();

                if (isTicketSameTypeAndLevel(ticket, ticket1)) {
                    iterator.remove();
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                if (list.isEmpty()) {
                    this.tickets.remove(i);
                }

                if (ticket.getType().doesSimulate() && this.simulationChunkUpdatedListener != null) {
                    this.simulationChunkUpdatedListener.update(i, getTicketLevelAt(list, true), false);
                }

                if (ticket.getType().doesLoad() && this.loadingChunkUpdatedListener != null) {
                    this.loadingChunkUpdatedListener.update(i, getTicketLevelAt(list, false), false);
                }

                if (ticket.getType().equals(TicketType.FORCED)) {
                    this.updateForcedChunks();
                }

                this.setDirty();
                return true;
            }
        }
    }

    private void updateForcedChunks() {
        this.chunksWithForcedTickets = this.getAllChunksWithTicketThat((ticket) -> {
            return ticket.getType().equals(TicketType.FORCED);
        });
    }

    public String getTicketDebugString(long i, boolean flag) {
        List<Ticket> list = this.getTickets(i);
        Ticket ticket = getLowestTicket(list, flag);

        return ticket == null ? "no_ticket" : ticket.toString();
    }

    public void purgeStaleTickets() {
        this.removeTicketIf((ticket) -> {
            ticket.decreaseTicksLeft();
            return ticket.isTimedOut();
        }, (Long2ObjectOpenHashMap) null);
        this.setDirty();
    }

    public void deactivateTicketsOnClosing() {
        this.removeTicketIf((ticket) -> {
            return ticket.getType() != TicketType.UNKNOWN;
        }, this.deactivatedTickets);
    }

    public void removeTicketIf(Predicate<Ticket> predicate, @Nullable Long2ObjectOpenHashMap<List<Ticket>> long2objectopenhashmap) {
        ObjectIterator<Long2ObjectMap.Entry<List<Ticket>>> objectiterator = this.tickets.long2ObjectEntrySet().fastIterator();
        boolean flag = false;

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();
            Iterator<Ticket> iterator = ((List) long2objectmap_entry.getValue()).iterator();
            boolean flag1 = false;
            boolean flag2 = false;

            while (iterator.hasNext()) {
                Ticket ticket = (Ticket) iterator.next();

                if (predicate.test(ticket)) {
                    if (long2objectopenhashmap != null) {
                        List<Ticket> list = (List) long2objectopenhashmap.computeIfAbsent(long2objectmap_entry.getLongKey(), (i) -> {
                            return new ObjectArrayList(((List) long2objectmap_entry.getValue()).size());
                        });

                        list.add(ticket);
                    }

                    iterator.remove();
                    if (ticket.getType().doesLoad()) {
                        flag2 = true;
                    }

                    if (ticket.getType().doesSimulate()) {
                        flag1 = true;
                    }

                    if (ticket.getType().equals(TicketType.FORCED)) {
                        flag = true;
                    }
                }
            }

            if (flag2 || flag1) {
                if (flag2 && this.loadingChunkUpdatedListener != null) {
                    this.loadingChunkUpdatedListener.update(long2objectmap_entry.getLongKey(), getTicketLevelAt((List) long2objectmap_entry.getValue(), false), false);
                }

                if (flag1 && this.simulationChunkUpdatedListener != null) {
                    this.simulationChunkUpdatedListener.update(long2objectmap_entry.getLongKey(), getTicketLevelAt((List) long2objectmap_entry.getValue(), true), false);
                }

                this.setDirty();
                if (((List) long2objectmap_entry.getValue()).isEmpty()) {
                    objectiterator.remove();
                }
            }
        }

        if (flag) {
            this.updateForcedChunks();
        }

    }

    public void replaceTicketLevelOfType(int i, TicketType tickettype) {
        List<Pair<Ticket, Long>> list = new ArrayList();
        ObjectIterator objectiterator = this.tickets.long2ObjectEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();

            for (Ticket ticket : long2objectmap_entry.getValue()) { // CraftBukkit - decompile error
                if (ticket.getType() == tickettype) {
                    list.add(Pair.of(ticket, long2objectmap_entry.getLongKey()));
                }
            }
        }

        for (Pair<Ticket, Long> pair : list) {
            Long olong = (Long) pair.getSecond();
            Ticket ticket1 = (Ticket) pair.getFirst();

            this.removeTicket(olong, ticket1);
            TicketType tickettype1 = ticket1.getType();

            this.addTicket(olong, new Ticket(tickettype1, i));
        }

    }

    public boolean updateChunkForced(ChunkCoordIntPair chunkcoordintpair, boolean flag) {
        Ticket ticket = new Ticket(TicketType.FORCED, PlayerChunkMap.FORCED_TICKET_LEVEL);

        return flag ? this.addTicket(chunkcoordintpair.toLong(), ticket) : this.removeTicket(chunkcoordintpair.toLong(), ticket);
    }

    public LongSet getForceLoadedChunks() {
        return this.chunksWithForcedTickets;
    }

    private LongSet getAllChunksWithTicketThat(Predicate<Ticket> predicate) {
        LongOpenHashSet longopenhashset = new LongOpenHashSet();
        ObjectIterator objectiterator = Long2ObjectMaps.fastIterable(this.tickets).iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();

            for (Ticket ticket : long2objectmap_entry.getValue()) { // CraftBukkit - decompile error
                if (predicate.test(ticket)) {
                    longopenhashset.add(long2objectmap_entry.getLongKey());
                    break;
                }
            }
        }

        return longopenhashset;
    }

    @FunctionalInterface
    public interface a {

        void update(long i, int j, boolean flag);
    }
}
