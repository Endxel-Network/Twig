// mc-dev import
package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;

public final class NBTTagByteArray implements NBTList {

    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final NBTTagType<NBTTagByteArray> TYPE = new NBTTagType.b<NBTTagByteArray>() {
        @Override
        public NBTTagByteArray load(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
            return new NBTTagByteArray(readAccounted(datainput, nbtreadlimiter));
        }

        @Override
        public StreamTagVisitor.b parse(DataInput datainput, StreamTagVisitor streamtagvisitor, NBTReadLimiter nbtreadlimiter) throws IOException {
            return streamtagvisitor.visit(readAccounted(datainput, nbtreadlimiter));
        }

        private static byte[] readAccounted(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
            nbtreadlimiter.accountBytes(24L);
            int i = datainput.readInt();
            com.google.common.base.Preconditions.checkArgument( i < 1 << 24); // Spigot

            nbtreadlimiter.accountBytes(1L, (long) i);
            byte[] abyte = new byte[i];

            datainput.readFully(abyte);
            return abyte;
        }

        @Override
        public void skip(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
            datainput.skipBytes(datainput.readInt() * 1);
        }

        @Override
        public String getName() {
            return "BYTE[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Byte_Array";
        }
    };
    private byte[] data;

    public NBTTagByteArray(byte[] abyte) {
        this.data = abyte;
    }

    @Override
    public void write(DataOutput dataoutput) throws IOException {
        dataoutput.writeInt(this.data.length);
        dataoutput.write(this.data);
    }

    @Override
    public int sizeInBytes() {
        return 24 + 1 * this.data.length;
    }

    @Override
    public byte getId() {
        return 7;
    }

    @Override
    public NBTTagType<NBTTagByteArray> getType() {
        return NBTTagByteArray.TYPE;
    }

    @Override
    public String toString() {
        StringTagVisitor stringtagvisitor = new StringTagVisitor();

        stringtagvisitor.visitByteArray(this);
        return stringtagvisitor.build();
    }

    @Override
    public NBTBase copy() {
        byte[] abyte = new byte[this.data.length];

        System.arraycopy(this.data, 0, abyte, 0, this.data.length);
        return new NBTTagByteArray(abyte);
    }

    public boolean equals(Object object) {
        return this == object ? true : object instanceof NBTTagByteArray && Arrays.equals(this.data, ((NBTTagByteArray) object).data);
    }

    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public void accept(TagVisitor tagvisitor) {
        tagvisitor.visitByteArray(this);
    }

    public byte[] getAsByteArray() {
        return this.data;
    }

    @Override
    public int size() {
        return this.data.length;
    }

    @Override
    public NBTTagByte get(int i) {
        return NBTTagByte.valueOf(this.data[i]);
    }

    @Override
    public boolean setTag(int i, NBTBase nbtbase) {
        if (nbtbase instanceof NBTNumber nbtnumber) {
            this.data[i] = nbtnumber.byteValue();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int i, NBTBase nbtbase) {
        if (nbtbase instanceof NBTNumber nbtnumber) {
            this.data = ArrayUtils.add(this.data, i, nbtnumber.byteValue());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public NBTTagByte remove(int i) {
        byte b0 = this.data[i];

        this.data = ArrayUtils.remove(this.data, i);
        return NBTTagByte.valueOf(b0);
    }

    @Override
    public void clear() {
        this.data = new byte[0];
    }

    @Override
    public Optional<byte[]> asByteArray() {
        return Optional.of(this.data);
    }

    @Override
    public StreamTagVisitor.b accept(StreamTagVisitor streamtagvisitor) {
        return streamtagvisitor.visit(this.data);
    }
}
