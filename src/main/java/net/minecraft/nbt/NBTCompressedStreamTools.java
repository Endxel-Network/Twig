// mc-dev import
package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.SystemUtils;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;

public class NBTCompressedStreamTools {

    private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

    public NBTCompressedStreamTools() {}

    public static NBTTagCompound readCompressed(Path path, NBTReadLimiter nbtreadlimiter) throws IOException {
        NBTTagCompound nbttagcompound;

        try (InputStream inputstream = Files.newInputStream(path); InputStream inputstream1 = new FastBufferedInputStream(inputstream);) {
            nbttagcompound = readCompressed(inputstream1, nbtreadlimiter);
        }

        return nbttagcompound;
    }

    private static DataInputStream createDecompressorStream(InputStream inputstream) throws IOException {
        return new DataInputStream(new FastBufferedInputStream(new GZIPInputStream(inputstream)));
    }

    private static DataOutputStream createCompressorStream(OutputStream outputstream) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(outputstream)));
    }

    public static NBTTagCompound readCompressed(InputStream inputstream, NBTReadLimiter nbtreadlimiter) throws IOException {
        try (DataInputStream datainputstream = createDecompressorStream(inputstream)) {
            return read(datainputstream, nbtreadlimiter);
        }
    }

    public static void parseCompressed(Path path, StreamTagVisitor streamtagvisitor, NBTReadLimiter nbtreadlimiter) throws IOException {
        try (InputStream inputstream = Files.newInputStream(path); InputStream inputstream1 = new FastBufferedInputStream(inputstream);) {
            parseCompressed(inputstream1, streamtagvisitor, nbtreadlimiter);
        }

    }

    public static void parseCompressed(InputStream inputstream, StreamTagVisitor streamtagvisitor, NBTReadLimiter nbtreadlimiter) throws IOException {
        try (DataInputStream datainputstream = createDecompressorStream(inputstream)) {
            parse(datainputstream, streamtagvisitor, nbtreadlimiter);
        }

    }

    public static void writeCompressed(NBTTagCompound nbttagcompound, Path path) throws IOException {
        try (OutputStream outputstream = Files.newOutputStream(path, NBTCompressedStreamTools.SYNC_OUTPUT_OPTIONS); OutputStream outputstream1 = new BufferedOutputStream(outputstream);) {
            writeCompressed(nbttagcompound, outputstream1);
        }

    }

    public static void writeCompressed(NBTTagCompound nbttagcompound, OutputStream outputstream) throws IOException {
        try (DataOutputStream dataoutputstream = createCompressorStream(outputstream)) {
            write(nbttagcompound, (DataOutput) dataoutputstream);
        }

    }

    public static void write(NBTTagCompound nbttagcompound, Path path) throws IOException {
        try (OutputStream outputstream = Files.newOutputStream(path, NBTCompressedStreamTools.SYNC_OUTPUT_OPTIONS); OutputStream outputstream1 = new BufferedOutputStream(outputstream); DataOutputStream dataoutputstream = new DataOutputStream(outputstream1);) {
            write(nbttagcompound, (DataOutput) dataoutputstream);
        }

    }

    @Nullable
    public static NBTTagCompound read(Path path) throws IOException {
        if (!Files.exists(path, new LinkOption[0])) {
            return null;
        } else {
            NBTTagCompound nbttagcompound;

            try (InputStream inputstream = Files.newInputStream(path); DataInputStream datainputstream = new DataInputStream(inputstream);) {
                nbttagcompound = read(datainputstream, NBTReadLimiter.unlimitedHeap());
            }

            return nbttagcompound;
        }
    }

    public static NBTTagCompound read(DataInput datainput) throws IOException {
        return read(datainput, NBTReadLimiter.unlimitedHeap());
    }

    public static NBTTagCompound read(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
        NBTBase nbtbase = readUnnamedTag(datainput, nbtreadlimiter);

        if (nbtbase instanceof NBTTagCompound) {
            return (NBTTagCompound) nbtbase;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void write(NBTTagCompound nbttagcompound, DataOutput dataoutput) throws IOException {
        writeUnnamedTagWithFallback(nbttagcompound, dataoutput);
    }

    public static void parse(DataInput datainput, StreamTagVisitor streamtagvisitor, NBTReadLimiter nbtreadlimiter) throws IOException {
        NBTTagType<?> nbttagtype = NBTTagTypes.getType(datainput.readByte());

        if (nbttagtype == NBTTagEnd.TYPE) {
            if (streamtagvisitor.visitRootEntry(NBTTagEnd.TYPE) == StreamTagVisitor.b.CONTINUE) {
                streamtagvisitor.visitEnd();
            }

        } else {
            switch (streamtagvisitor.visitRootEntry(nbttagtype)) {
                case HALT:
                default:
                    break;
                case BREAK:
                    NBTTagString.skipString(datainput);
                    nbttagtype.skip(datainput, nbtreadlimiter);
                    break;
                case CONTINUE:
                    NBTTagString.skipString(datainput);
                    nbttagtype.parse(datainput, streamtagvisitor, nbtreadlimiter);
            }

        }
    }

    public static NBTBase readAnyTag(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
        byte b0 = datainput.readByte();

        return (NBTBase) (b0 == 0 ? NBTTagEnd.INSTANCE : readTagSafe(datainput, nbtreadlimiter, b0));
    }

    public static void writeAnyTag(NBTBase nbtbase, DataOutput dataoutput) throws IOException {
        dataoutput.writeByte(nbtbase.getId());
        if (nbtbase.getId() != 0) {
            nbtbase.write(dataoutput);
        }
    }

    public static void writeUnnamedTag(NBTBase nbtbase, DataOutput dataoutput) throws IOException {
        dataoutput.writeByte(nbtbase.getId());
        if (nbtbase.getId() != 0) {
            dataoutput.writeUTF("");
            nbtbase.write(dataoutput);
        }
    }

    public static void writeUnnamedTagWithFallback(NBTBase nbtbase, DataOutput dataoutput) throws IOException {
        writeUnnamedTag(nbtbase, new NBTCompressedStreamTools.a(dataoutput));
    }

    @VisibleForTesting
    public static NBTBase readUnnamedTag(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
        byte b0 = datainput.readByte();

        if (b0 == 0) {
            return NBTTagEnd.INSTANCE;
        } else {
            NBTTagString.skipString(datainput);
            return readTagSafe(datainput, nbtreadlimiter, b0);
        }
    }

    private static NBTBase readTagSafe(DataInput datainput, NBTReadLimiter nbtreadlimiter, byte b0) {
        try {
            return NBTTagTypes.getType(b0).load(datainput, nbtreadlimiter);
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.forThrowable(ioexception, "Loading NBT data");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("NBT Tag");

            crashreportsystemdetails.setDetail("Tag type", b0);
            throw new ReportedNbtException(crashreport);
        }
    }

    public static class a extends DelegateDataOutput {

        public a(DataOutput dataoutput) {
            super(dataoutput);
        }

        @Override
        public void writeUTF(String s) throws IOException {
            try {
                super.writeUTF(s);
            } catch (UTFDataFormatException utfdataformatexception) {
                SystemUtils.logAndPauseIfInIde("Failed to write NBT String", utfdataformatexception);
                super.writeUTF("");
            }

        }
    }
}
