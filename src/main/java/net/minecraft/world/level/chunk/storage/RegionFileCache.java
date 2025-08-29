package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.FileUtils;
import net.minecraft.nbt.NBTCompressedStreamTools;
import net.minecraft.nbt.NBTReadLimiter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionSuppressor;
import net.minecraft.world.level.ChunkCoordIntPair;

public final class RegionFileCache implements AutoCloseable {

    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap();
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    RegionFileCache(RegionStorageInfo regionstorageinfo, Path path, boolean flag) {
        this.folder = path;
        this.sync = flag;
        this.info = regionstorageinfo;
    }

    private RegionFile getRegionFile(ChunkCoordIntPair chunkcoordintpair, boolean existingOnly) throws IOException { // CraftBukkit
        long i = ChunkCoordIntPair.asLong(chunkcoordintpair.getRegionX(), chunkcoordintpair.getRegionZ());
        RegionFile regionfile = (RegionFile) this.regionCache.getAndMoveToFirst(i);

        if (regionfile != null) {
            return regionfile;
        } else {
            if (this.regionCache.size() >= 256) {
                ((RegionFile) this.regionCache.removeLast()).close();
            }

            FileUtils.createDirectoriesSafe(this.folder);
            Path path = this.folder;
            int j = chunkcoordintpair.getRegionX();
            Path path1 = path.resolve("r." + j + "." + chunkcoordintpair.getRegionZ() + ".mca");
            if (existingOnly && !java.nio.file.Files.exists(path1)) return null; // CraftBukkit
            RegionFile regionfile1 = new RegionFile(this.info, path1, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(i, regionfile1);
            return regionfile1;
        }
    }

    @Nullable
    public NBTTagCompound read(ChunkCoordIntPair chunkcoordintpair) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionfile = this.getRegionFile(chunkcoordintpair, true);
        if (regionfile == null) {
            return null;
        }
        // CraftBukkit end

        try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkcoordintpair)) {
            if (datainputstream == null) {
                return null;
            } else {
                return NBTCompressedStreamTools.read((DataInput) datainputstream);
            }
        }
    }

    public void scanChunk(ChunkCoordIntPair chunkcoordintpair, StreamTagVisitor streamtagvisitor) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionfile = this.getRegionFile(chunkcoordintpair, true);
        if (regionfile == null) {
            return;
        }
        // CraftBukkit end

        try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkcoordintpair)) {
            if (datainputstream != null) {
                NBTCompressedStreamTools.parse(datainputstream, streamtagvisitor, NBTReadLimiter.unlimitedHeap());
            }
        }

    }

    protected void write(ChunkCoordIntPair chunkcoordintpair, @Nullable NBTTagCompound nbttagcompound) throws IOException {
        RegionFile regionfile = this.getRegionFile(chunkcoordintpair, false); // CraftBukkit

        if (nbttagcompound == null) {
            regionfile.clear(chunkcoordintpair);
        } else {
            try (DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(chunkcoordintpair)) {
                NBTCompressedStreamTools.write(nbttagcompound, (DataOutput) dataoutputstream);
            }
        }

    }

    public void close() throws IOException {
        ExceptionSuppressor<IOException> exceptionsuppressor = new ExceptionSuppressor<IOException>();
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            try {
                regionfile.close();
            } catch (IOException ioexception) {
                exceptionsuppressor.add(ioexception);
            }
        }

        exceptionsuppressor.throwIfPresent();
    }

    public void flush() throws IOException {
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            regionfile.flush();
        }

    }

    public RegionStorageInfo info() {
        return this.info;
    }
}
