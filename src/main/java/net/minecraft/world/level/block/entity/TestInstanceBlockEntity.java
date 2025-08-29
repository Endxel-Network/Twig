package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.EnumChatFormat;
import net.minecraft.FileUtils;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.DebugReportNBT;
import net.minecraft.gametest.framework.FailedTestTracker;
import net.minecraft.gametest.framework.GameTestHarnessInfo;
import net.minecraft.gametest.framework.GameTestHarnessRunner;
import net.minecraft.gametest.framework.GameTestHarnessStructures;
import net.minecraft.gametest.framework.GameTestHarnessTestCommand;
import net.minecraft.gametest.framework.GameTestHarnessTicker;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.ARGB;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnumBlockRotation;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.levelgen.structure.StructureBoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.DefinedStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.DefinedStructureInfo;
import net.minecraft.world.phys.AxisAlignedBB;

public class TestInstanceBlockEntity extends TileEntity implements BeaconBeamOwner, BoundingBoxRenderable {

    private static final IChatBaseComponent INVALID_TEST_NAME = IChatBaseComponent.translatable("test_instance_block.invalid_test");
    private static final List<BeaconBeamOwner.a> BEAM_CLEARED = List.of();
    private static final List<BeaconBeamOwner.a> BEAM_RUNNING = List.of(new BeaconBeamOwner.a(ARGB.color(128, 128, 128)));
    private static final List<BeaconBeamOwner.a> BEAM_SUCCESS = List.of(new BeaconBeamOwner.a(ARGB.color(0, 255, 0)));
    private static final List<BeaconBeamOwner.a> BEAM_REQUIRED_FAILED = List.of(new BeaconBeamOwner.a(ARGB.color(255, 0, 0)));
    private static final List<BeaconBeamOwner.a> BEAM_OPTIONAL_FAILED = List.of(new BeaconBeamOwner.a(ARGB.color(255, 128, 0)));
    private static final BaseBlockPosition STRUCTURE_OFFSET = new BaseBlockPosition(0, 1, 1);
    private TestInstanceBlockEntity.a data;

    public TestInstanceBlockEntity(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.TEST_INSTANCE_BLOCK, blockposition, iblockdata);
        this.data = new TestInstanceBlockEntity.a(Optional.empty(), BaseBlockPosition.ZERO, EnumBlockRotation.NONE, false, TestInstanceBlockEntity.b.CLEARED, Optional.empty());
    }

    public void set(TestInstanceBlockEntity.a testinstanceblockentity_a) {
        this.data = testinstanceblockentity_a;
        this.setChanged();
    }

    public static Optional<BaseBlockPosition> getStructureSize(WorldServer worldserver, ResourceKey<GameTestInstance> resourcekey) {
        return getStructureTemplate(worldserver, resourcekey).map(DefinedStructure::getSize);
    }

    public StructureBoundingBox getStructureBoundingBox() {
        BlockPosition blockposition = this.getStructurePos();
        BlockPosition blockposition1 = blockposition.offset(this.getTransformedSize()).offset(-1, -1, -1);

        return StructureBoundingBox.fromCorners(blockposition, blockposition1);
    }

    public AxisAlignedBB getStructureBounds() {
        return AxisAlignedBB.of(this.getStructureBoundingBox());
    }

    private static Optional<DefinedStructure> getStructureTemplate(WorldServer worldserver, ResourceKey<GameTestInstance> resourcekey) {
        return worldserver.registryAccess().get(resourcekey).map((holder_c) -> {
            return ((GameTestInstance) holder_c.value()).structure();
        }).flatMap((minecraftkey) -> {
            return worldserver.getStructureManager().get(minecraftkey);
        });
    }

    public Optional<ResourceKey<GameTestInstance>> test() {
        return this.data.test();
    }

    public IChatBaseComponent getTestName() {
        return (IChatBaseComponent) this.test().map((resourcekey) -> {
            return (IChatBaseComponent) IChatBaseComponent.literal(resourcekey.location().toString()); // CraftBukkit - decompile error
        }).orElse(TestInstanceBlockEntity.INVALID_TEST_NAME);
    }

    private Optional<Holder.c<GameTestInstance>> getTestHolder() {
        Optional<ResourceKey<GameTestInstance>> optional = this.test(); // CraftBukkit - decompile error
        IRegistryCustom iregistrycustom = this.level.registryAccess();

        Objects.requireNonNull(iregistrycustom);
        return optional.flatMap(iregistrycustom::get);
    }

    public boolean ignoreEntities() {
        return this.data.ignoreEntities();
    }

    public BaseBlockPosition getSize() {
        return this.data.size();
    }

    public EnumBlockRotation getRotation() {
        return ((EnumBlockRotation) this.getTestHolder().map(Holder::value).map(GameTestInstance::rotation).orElse(EnumBlockRotation.NONE)).getRotated(this.data.rotation());
    }

    public Optional<IChatBaseComponent> errorMessage() {
        return this.data.errorMessage();
    }

    public void setErrorMessage(IChatBaseComponent ichatbasecomponent) {
        this.set(this.data.withError(ichatbasecomponent));
    }

    public void setSuccess() {
        this.set(this.data.withStatus(TestInstanceBlockEntity.b.FINISHED));
        this.removeBarriers();
    }

    public void setRunning() {
        this.set(this.data.withStatus(TestInstanceBlockEntity.b.RUNNING));
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level instanceof WorldServer) {
            this.level.sendBlockUpdated(this.getBlockPos(), Blocks.AIR.defaultBlockState(), this.getBlockState(), 3);
        }

    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        this.saveAdditional(nbttagcompound, holderlookup_a);
        return nbttagcompound;
    }

    @Override
    protected void loadAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        NBTBase nbtbase = nbttagcompound.get("data");

        if (nbtbase != null) {
            TestInstanceBlockEntity.a.CODEC.parse(DynamicOpsNBT.INSTANCE, nbtbase).ifSuccess(this::set);
        }
    }

    @Override
    protected void saveAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        DataResult<NBTBase> dataresult = TestInstanceBlockEntity.a.CODEC.encode(this.data, DynamicOpsNBT.INSTANCE, new NBTTagCompound());

        dataresult.ifSuccess((nbtbase) -> {
            nbttagcompound.put("data", nbtbase);
        });
    }

    @Override
    public BoundingBoxRenderable.a renderMode() {
        return BoundingBoxRenderable.a.BOX;
    }

    public BlockPosition getStructurePos() {
        return getStructurePos(this.getBlockPos());
    }

    public static BlockPosition getStructurePos(BlockPosition blockposition) {
        return blockposition.offset(TestInstanceBlockEntity.STRUCTURE_OFFSET);
    }

    @Override
    public BoundingBoxRenderable.b getRenderableBox() {
        return new BoundingBoxRenderable.b(new BlockPosition(TestInstanceBlockEntity.STRUCTURE_OFFSET), this.getTransformedSize());
    }

    @Override
    public List<BeaconBeamOwner.a> getBeamSections() {
        List list;

        switch (this.data.status().ordinal()) {
            case 0:
                list = TestInstanceBlockEntity.BEAM_CLEARED;
                break;
            case 1:
                list = TestInstanceBlockEntity.BEAM_RUNNING;
                break;
            case 2:
                list = this.errorMessage().isEmpty() ? TestInstanceBlockEntity.BEAM_SUCCESS : ((Boolean) this.getTestHolder().map(Holder::value).map(GameTestInstance::required).orElse(true) ? TestInstanceBlockEntity.BEAM_REQUIRED_FAILED : TestInstanceBlockEntity.BEAM_OPTIONAL_FAILED);
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return list;
    }

    private BaseBlockPosition getTransformedSize() {
        BaseBlockPosition baseblockposition = this.getSize();
        EnumBlockRotation enumblockrotation = this.getRotation();
        boolean flag = enumblockrotation == EnumBlockRotation.CLOCKWISE_90 || enumblockrotation == EnumBlockRotation.COUNTERCLOCKWISE_90;
        int i = flag ? baseblockposition.getZ() : baseblockposition.getX();
        int j = flag ? baseblockposition.getX() : baseblockposition.getZ();

        return new BaseBlockPosition(i, baseblockposition.getY(), j);
    }

    public void resetTest(Consumer<IChatBaseComponent> consumer) {
        this.removeBarriers();
        boolean flag = this.placeStructure();

        if (flag) {
            consumer.accept(IChatBaseComponent.translatable("test_instance_block.reset_success", this.getTestName()).withStyle(EnumChatFormat.GREEN));
        }

        this.set(this.data.withStatus(TestInstanceBlockEntity.b.CLEARED));
    }

    public Optional<MinecraftKey> saveTest(Consumer<IChatBaseComponent> consumer) {
        Optional<Holder.c<GameTestInstance>> optional = this.getTestHolder();
        Optional<MinecraftKey> optional1;

        if (optional.isPresent()) {
            optional1 = Optional.of(((GameTestInstance) ((Holder.c) optional.get()).value()).structure());
        } else {
            optional1 = this.test().map(ResourceKey::location);
        }

        if (optional1.isEmpty()) {
            BlockPosition blockposition = this.getBlockPos();

            consumer.accept(IChatBaseComponent.translatable("test_instance_block.error.unable_to_save", blockposition.getX(), blockposition.getY(), blockposition.getZ()).withStyle(EnumChatFormat.RED));
            return optional1;
        } else {
            World world = this.level;

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                TileEntityStructure.saveStructure(worldserver, (MinecraftKey) optional1.get(), this.getStructurePos(), this.getSize(), this.ignoreEntities(), "", true);
            }

            return optional1;
        }
    }

    public boolean exportTest(Consumer<IChatBaseComponent> consumer) {
        Optional<MinecraftKey> optional = this.saveTest(consumer);

        if (!optional.isEmpty()) {
            World world = this.level;

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                return export(worldserver, (MinecraftKey) optional.get(), consumer);
            }
        }

        return false;
    }

    public static boolean export(WorldServer worldserver, MinecraftKey minecraftkey, Consumer<IChatBaseComponent> consumer) {
        Path path = GameTestHarnessStructures.testStructuresDir;
        Path path1 = worldserver.getStructureManager().createAndValidatePathToGeneratedStructure(minecraftkey, ".nbt");
        Path path2 = DebugReportNBT.convertStructure(CachedOutput.NO_CACHE, path1, minecraftkey.getPath(), path.resolve(minecraftkey.getNamespace()).resolve("structure"));

        if (path2 == null) {
            consumer.accept(IChatBaseComponent.literal("Failed to export " + String.valueOf(path1)).withStyle(EnumChatFormat.RED));
            return true;
        } else {
            try {
                FileUtils.createDirectoriesSafe(path2.getParent());
            } catch (IOException ioexception) {
                consumer.accept(IChatBaseComponent.literal("Could not create folder " + String.valueOf(path2.getParent())).withStyle(EnumChatFormat.RED));
                return true;
            }

            String s = String.valueOf(minecraftkey);

            consumer.accept(IChatBaseComponent.literal("Exported " + s + " to " + String.valueOf(path2.toAbsolutePath())));
            return false;
        }
    }

    public void runTest(Consumer<IChatBaseComponent> consumer) {
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            Optional optional = this.getTestHolder();
            BlockPosition blockposition = this.getBlockPos();

            if (optional.isEmpty()) {
                consumer.accept(IChatBaseComponent.translatable("test_instance_block.error.no_test", blockposition.getX(), blockposition.getY(), blockposition.getZ()).withStyle(EnumChatFormat.RED));
            } else if (!this.placeStructure()) {
                consumer.accept(IChatBaseComponent.translatable("test_instance_block.error.no_test_structure", blockposition.getX(), blockposition.getY(), blockposition.getZ()).withStyle(EnumChatFormat.RED));
            } else {
                GameTestHarnessRunner.clearMarkers(worldserver);
                GameTestHarnessTicker.SINGLETON.clear();
                FailedTestTracker.forgetFailedTests();
                consumer.accept(IChatBaseComponent.translatable("test_instance_block.starting", ((Holder.c) optional.get()).getRegisteredName()));
                GameTestHarnessInfo gametestharnessinfo = new GameTestHarnessInfo((Holder.c) optional.get(), this.data.rotation(), worldserver, RetryOptions.noRetries());

                gametestharnessinfo.setTestBlockPos(blockposition);
                GameTestHarnessRunner gametestharnessrunner = GameTestHarnessRunner.a.fromInfo(List.of(gametestharnessinfo), worldserver).build();

                GameTestHarnessTestCommand.trackAndStartRunner(worldserver.getServer().createCommandSourceStack(), gametestharnessrunner);
            }
        }
    }

    public boolean placeStructure() {
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            Optional<DefinedStructure> optional = this.data.test().flatMap((resourcekey) -> {
                return getStructureTemplate(worldserver, resourcekey);
            });

            if (optional.isPresent()) {
                this.placeStructure(worldserver, (DefinedStructure) optional.get());
                return true;
            }
        }

        return false;
    }

    private void placeStructure(WorldServer worldserver, DefinedStructure definedstructure) {
        DefinedStructureInfo definedstructureinfo = (new DefinedStructureInfo()).setRotation(this.getRotation()).setIgnoreEntities(this.data.ignoreEntities()).setKnownShape(true);
        BlockPosition blockposition = this.getStartCorner();

        this.forceLoadChunks();
        this.removeEntities();
        definedstructure.placeInWorld(worldserver, blockposition, blockposition, definedstructureinfo, worldserver.getRandom(), 818);
    }

    private void removeEntities() {
        this.level.getEntities((Entity) null, this.getStructureBounds()).stream().filter((entity) -> {
            return !(entity instanceof EntityHuman);
        }).forEach((entity) -> entity.discard(null)); // CraftBukkit - add Bukkit remove cause
    }

    private void forceLoadChunks() {
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            this.getStructureBoundingBox().intersectingChunks().forEach((chunkcoordintpair) -> {
                worldserver.setChunkForced(chunkcoordintpair.x, chunkcoordintpair.z, true);
            });
        }

    }

    public BlockPosition getStartCorner() {
        BaseBlockPosition baseblockposition = this.getSize();
        EnumBlockRotation enumblockrotation = this.getRotation();
        BlockPosition blockposition = this.getStructurePos();
        BlockPosition blockposition1;

        switch (enumblockrotation) {
            case NONE:
                blockposition1 = blockposition;
                break;
            case CLOCKWISE_90:
                blockposition1 = blockposition.offset(baseblockposition.getZ() - 1, 0, 0);
                break;
            case CLOCKWISE_180:
                blockposition1 = blockposition.offset(baseblockposition.getX() - 1, 0, baseblockposition.getZ() - 1);
                break;
            case COUNTERCLOCKWISE_90:
                blockposition1 = blockposition.offset(0, 0, baseblockposition.getX() - 1);
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return blockposition1;
    }

    public void encaseStructure() {
        this.processStructureBoundary((blockposition) -> {
            if (!this.level.getBlockState(blockposition).is(Blocks.TEST_INSTANCE_BLOCK)) {
                this.level.setBlockAndUpdate(blockposition, Blocks.BARRIER.defaultBlockState());
            }

        });
    }

    public void removeBarriers() {
        this.processStructureBoundary((blockposition) -> {
            if (this.level.getBlockState(blockposition).is(Blocks.BARRIER)) {
                this.level.setBlockAndUpdate(blockposition, Blocks.AIR.defaultBlockState());
            }

        });
    }

    public void processStructureBoundary(Consumer<BlockPosition> consumer) {
        AxisAlignedBB axisalignedbb = this.getStructureBounds();
        boolean flag = !(Boolean) this.getTestHolder().map((holder_c) -> {
            return ((GameTestInstance) holder_c.value()).skyAccess();
        }).orElse(false);
        BlockPosition blockposition = BlockPosition.containing(axisalignedbb.minX, axisalignedbb.minY, axisalignedbb.minZ).offset(-1, -1, -1);
        BlockPosition blockposition1 = BlockPosition.containing(axisalignedbb.maxX, axisalignedbb.maxY, axisalignedbb.maxZ);

        BlockPosition.betweenClosedStream(blockposition, blockposition1).forEach((blockposition2) -> {
            boolean flag1 = blockposition2.getX() == blockposition.getX() || blockposition2.getX() == blockposition1.getX() || blockposition2.getZ() == blockposition.getZ() || blockposition2.getZ() == blockposition1.getZ() || blockposition2.getY() == blockposition.getY();
            boolean flag2 = blockposition2.getY() == blockposition1.getY();

            if (flag1 || flag2 && flag) {
                consumer.accept(blockposition2);
            }

        });
    }

    public static enum b implements INamable {

        CLEARED("cleared", 0), RUNNING("running", 1), FINISHED("finished", 2);

        private static final IntFunction<TestInstanceBlockEntity.b> ID_MAP = ByIdMap.<TestInstanceBlockEntity.b>continuous((testinstanceblockentity_b) -> {
            return testinstanceblockentity_b.index;
        }, values(), ByIdMap.a.ZERO);
        public static final Codec<TestInstanceBlockEntity.b> CODEC = INamable.<TestInstanceBlockEntity.b>fromEnum(TestInstanceBlockEntity.b::values);
        public static final StreamCodec<ByteBuf, TestInstanceBlockEntity.b> STREAM_CODEC = ByteBufCodecs.idMapper(TestInstanceBlockEntity.b::byIndex, (testinstanceblockentity_b) -> {
            return testinstanceblockentity_b.index;
        });
        private final String id;
        private final int index;

        private b(final String s, final int i) {
            this.id = s;
            this.index = i;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        public static TestInstanceBlockEntity.b byIndex(int i) {
            return (TestInstanceBlockEntity.b) TestInstanceBlockEntity.b.ID_MAP.apply(i);
        }
    }

    public static record a(Optional<ResourceKey<GameTestInstance>> test, BaseBlockPosition size, EnumBlockRotation rotation, boolean ignoreEntities, TestInstanceBlockEntity.b status, Optional<IChatBaseComponent> errorMessage) {

        public static final Codec<TestInstanceBlockEntity.a> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(ResourceKey.codec(Registries.TEST_INSTANCE).optionalFieldOf("test").forGetter(TestInstanceBlockEntity.a::test), BaseBlockPosition.CODEC.fieldOf("size").forGetter(TestInstanceBlockEntity.a::size), EnumBlockRotation.CODEC.fieldOf("rotation").forGetter(TestInstanceBlockEntity.a::rotation), Codec.BOOL.fieldOf("ignore_entities").forGetter(TestInstanceBlockEntity.a::ignoreEntities), TestInstanceBlockEntity.b.CODEC.fieldOf("status").forGetter(TestInstanceBlockEntity.a::status), ComponentSerialization.CODEC.optionalFieldOf("error_message").forGetter(TestInstanceBlockEntity.a::errorMessage)).apply(instance, TestInstanceBlockEntity.a::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, TestInstanceBlockEntity.a> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.TEST_INSTANCE)), TestInstanceBlockEntity.a::test, BaseBlockPosition.STREAM_CODEC, TestInstanceBlockEntity.a::size, EnumBlockRotation.STREAM_CODEC, TestInstanceBlockEntity.a::rotation, ByteBufCodecs.BOOL, TestInstanceBlockEntity.a::ignoreEntities, TestInstanceBlockEntity.b.STREAM_CODEC, TestInstanceBlockEntity.a::status, ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), TestInstanceBlockEntity.a::errorMessage, TestInstanceBlockEntity.a::new);

        public TestInstanceBlockEntity.a withSize(BaseBlockPosition baseblockposition) {
            return new TestInstanceBlockEntity.a(this.test, baseblockposition, this.rotation, this.ignoreEntities, this.status, this.errorMessage);
        }

        public TestInstanceBlockEntity.a withStatus(TestInstanceBlockEntity.b testinstanceblockentity_b) {
            return new TestInstanceBlockEntity.a(this.test, this.size, this.rotation, this.ignoreEntities, testinstanceblockentity_b, Optional.empty());
        }

        public TestInstanceBlockEntity.a withError(IChatBaseComponent ichatbasecomponent) {
            return new TestInstanceBlockEntity.a(this.test, this.size, this.rotation, this.ignoreEntities, TestInstanceBlockEntity.b.FINISHED, Optional.of(ichatbasecomponent));
        }
    }
}
