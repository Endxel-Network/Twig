// mc-dev import
package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;

public final class NBTTagIntArray implements NBTList {

    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final NBTTagType<NBTTagIntArray> TYPE = new NBTTagType.b<NBTTagIntArray>() {
        @Override
        public NBTTagIntArray load(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
            return new NBTTagIntArray(readAccounted(datainput, nbtreadlimiter));
        }

        @Override
        public StreamTagVisitor.b parse(DataInput datainput, StreamTagVisitor streamtagvisitor, NBTReadLimiter nbtreadlimiter) throws IOException {
            return streamtagvisitor.visit(readAccounted(datainput, nbtreadlimiter));
        }

        private static int[] readAccounted(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
            nbtreadlimiter.accountBytes(24L);
            int i = datainput.readInt();

            nbtreadlimiter.accountBytes(4L, (long) i);
            int[] aint = new int[i];

            for (int j = 0; j < i; ++j) {
                aint[j] = datainput.readInt();
            }

            return aint;
        }

        @Override
        public void skip(DataInput datainput, NBTReadLimiter nbtreadlimiter) throws IOException {
            datainput.skipBytes(datainput.readInt() * 4);
        }

        @Override
        public String getName() {
            return "INT[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Int_Array";
        }
    };
    private int[] data;

    public NBTTagIntArray(int[] aint) {
        this.data = aint;
    }

    @Override
    public void write(DataOutput dataoutput) throws IOException {
        dataoutput.writeInt(this.data.length);

        for (int i : this.data) {
            dataoutput.writeInt(i);
        }

    }

    @Override
    public int sizeInBytes() {
        return 24 + 4 * this.data.length;
    }

    @Override
    public byte getId() {
        return 11;
    }

    @Override
    public NBTTagType<NBTTagIntArray> getType() {
        return NBTTagIntArray.TYPE;
    }

    @Override
    public String toString() {
        StringTagVisitor stringtagvisitor = new StringTagVisitor();

        stringtagvisitor.visitIntArray(this);
        return stringtagvisitor.build();
    }

    @Override
    public NBTTagIntArray copy() {
        int[] aint = new int[this.data.length];

        System.arraycopy(this.data, 0, aint, 0, this.data.length);
        return new NBTTagIntArray(aint);
    }

    public boolean equals(Object object) {
        return this == object ? true : object instanceof NBTTagIntArray && Arrays.equals(this.data, ((NBTTagIntArray) object).data);
    }

    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    public int[] getAsIntArray() {
        return this.data;
    }

    @Override
    public void accept(TagVisitor tagvisitor) {
        tagvisitor.visitIntArray(this);
    }

    @Override
    public int size() {
        return this.data.length;
    }

    @Override
    public NBTTagInt get(int i) {
        return NBTTagInt.valueOf(this.data[i]);
    }

    @Override
    public boolean setTag(int i, NBTBase nbtbase) {
        if (nbtbase instanceof NBTNumber nbtnumber) {
            this.data[i] = nbtnumber.intValue();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int i, NBTBase nbtbase) {
        if (nbtbase instanceof NBTNumber nbtnumber) {
            this.data = ArrayUtils.add(this.data, i, nbtnumber.intValue());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public NBTTagInt remove(int i) {
        int j = this.data[i];

        this.data = ArrayUtils.remove(this.data, i);
        return NBTTagInt.valueOf(j);
    }

    @Override
    public void clear() {
        this.data = new int[0];
    }

    @Override
    public Optional<int[]> asIntArray() {
        return Optional.of(this.data);
    }

    @Override
    public StreamTagVisitor.b accept(StreamTagVisitor streamtagvisitor) {
        return streamtagvisitor.visit(this.data);
    }
}
