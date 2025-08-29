package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.Particles;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.EntityFallingBlock;
import net.minecraft.world.entity.projectile.EntityThrownTrident;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.block.state.properties.BlockStateEnum;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.material.FluidTypes;
import net.minecraft.world.level.pathfinder.PathMode;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.OperatorBoolean;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.phys.shapes.VoxelShapes;

public class PointedDripstoneBlock extends Block implements Fallable, IBlockWaterlogged {

    public static final MapCodec<PointedDripstoneBlock> CODEC = simpleCodec(PointedDripstoneBlock::new);
    public static final BlockStateEnum<EnumDirection> TIP_DIRECTION = BlockProperties.VERTICAL_DIRECTION;
    public static final BlockStateEnum<DripstoneThickness> THICKNESS = BlockProperties.DRIPSTONE_THICKNESS;
    public static final BlockStateBoolean WATERLOGGED = BlockProperties.WATERLOGGED;
    private static final int MAX_SEARCH_LENGTH_WHEN_CHECKING_DRIP_TYPE = 11;
    private static final int DELAY_BEFORE_FALLING = 2;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK = 0.02F;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK_IF_UNDER_LIQUID_SOURCE = 0.12F;
    private static final int MAX_SEARCH_LENGTH_BETWEEN_STALACTITE_TIP_AND_CAULDRON = 11;
    private static final float WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.17578125F;
    private static final float LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.05859375F;
    private static final double MIN_TRIDENT_VELOCITY_TO_BREAK_DRIPSTONE = 0.6D;
    private static final float STALACTITE_DAMAGE_PER_FALL_DISTANCE_AND_SIZE = 1.0F;
    private static final int STALACTITE_MAX_DAMAGE = 40;
    private static final int MAX_STALACTITE_HEIGHT_FOR_DAMAGE_CALCULATION = 6;
    private static final float STALAGMITE_FALL_DISTANCE_OFFSET = 2.5F;
    private static final int STALAGMITE_FALL_DAMAGE_MODIFIER = 2;
    private static final float AVERAGE_DAYS_PER_GROWTH = 5.0F;
    private static final float GROWTH_PROBABILITY_PER_RANDOM_TICK = 0.011377778F;
    private static final int MAX_GROWTH_LENGTH = 7;
    private static final int MAX_STALAGMITE_SEARCH_RANGE_WHEN_GROWING = 10;
    private static final VoxelShape SHAPE_TIP_MERGE = Block.column(6.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_TIP_UP = Block.column(6.0D, 0.0D, 11.0D);
    private static final VoxelShape SHAPE_TIP_DOWN = Block.column(6.0D, 5.0D, 16.0D);
    private static final VoxelShape SHAPE_FRUSTUM = Block.column(8.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_MIDDLE = Block.column(10.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_BASE = Block.column(12.0D, 0.0D, 16.0D);
    private static final double STALACTITE_DRIP_START_PIXEL = PointedDripstoneBlock.SHAPE_TIP_DOWN.min(EnumDirection.EnumAxis.Y);
    private static final float MAX_HORIZONTAL_OFFSET = (float) PointedDripstoneBlock.SHAPE_BASE.min(EnumDirection.EnumAxis.X);
    private static final VoxelShape REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK = Block.column(4.0D, 0.0D, 16.0D);

    @Override
    public MapCodec<PointedDripstoneBlock> codec() {
        return PointedDripstoneBlock.CODEC;
    }

    public PointedDripstoneBlock(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) ((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(PointedDripstoneBlock.TIP_DIRECTION, EnumDirection.UP)).setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP)).setValue(PointedDripstoneBlock.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(PointedDripstoneBlock.TIP_DIRECTION, PointedDripstoneBlock.THICKNESS, PointedDripstoneBlock.WATERLOGGED);
    }

    @Override
    protected boolean canSurvive(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        return isValidPointedDripstonePlacement(iworldreader, blockposition, (EnumDirection) iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION));
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        if ((Boolean) iblockdata.getValue(PointedDripstoneBlock.WATERLOGGED)) {
            scheduledtickaccess.scheduleTick(blockposition, (FluidType) FluidTypes.WATER, FluidTypes.WATER.getTickDelay(iworldreader));
        }

        if (enumdirection != EnumDirection.UP && enumdirection != EnumDirection.DOWN) {
            return iblockdata;
        } else {
            EnumDirection enumdirection1 = (EnumDirection) iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION);

            if (enumdirection1 == EnumDirection.DOWN && scheduledtickaccess.getBlockTicks().hasScheduledTick(blockposition, this)) {
                return iblockdata;
            } else if (enumdirection == enumdirection1.getOpposite() && !this.canSurvive(iblockdata, iworldreader, blockposition)) {
                if (enumdirection1 == EnumDirection.DOWN) {
                    scheduledtickaccess.scheduleTick(blockposition, (Block) this, 2);
                } else {
                    scheduledtickaccess.scheduleTick(blockposition, (Block) this, 1);
                }

                return iblockdata;
            } else {
                boolean flag = iblockdata.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP_MERGE;
                DripstoneThickness dripstonethickness = calculateDripstoneThickness(iworldreader, blockposition, enumdirection1, flag);

                return (IBlockData) iblockdata.setValue(PointedDripstoneBlock.THICKNESS, dripstonethickness);
            }
        }
    }

    @Override
    protected void onProjectileHit(World world, IBlockData iblockdata, MovingObjectPositionBlock movingobjectpositionblock, IProjectile iprojectile) {
        if (!world.isClientSide) {
            BlockPosition blockposition = movingobjectpositionblock.getBlockPos();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if (iprojectile.mayInteract(worldserver, blockposition) && iprojectile.mayBreak(worldserver) && iprojectile instanceof EntityThrownTrident && iprojectile.getDeltaMovement().length() > 0.6D) {
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(iprojectile, blockposition, Blocks.AIR.defaultBlockState())) {
                        return;
                    }
                    // CraftBukkit end
                    world.destroyBlock(blockposition, true);
                }
            }

        }
    }

    @Override
    public void fallOn(World world, IBlockData iblockdata, BlockPosition blockposition, Entity entity, double d0) {
        if (iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION) == EnumDirection.UP && iblockdata.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP) {
            entity.causeFallDamage(d0 + 2.5D, 2.0F, world.damageSources().stalagmite().directBlock(world, blockposition)); // CraftBukkit
        } else {
            super.fallOn(world, iblockdata, blockposition, entity, d0);
        }

    }

    @Override
    public void animateTick(IBlockData iblockdata, World world, BlockPosition blockposition, RandomSource randomsource) {
        if (canDrip(iblockdata)) {
            float f = randomsource.nextFloat();

            if (f <= 0.12F) {
                getFluidAboveStalactite(world, blockposition, iblockdata).filter((pointeddripstoneblock_a) -> {
                    return f < 0.02F || canFillCauldron(pointeddripstoneblock_a.fluid);
                }).ifPresent((pointeddripstoneblock_a) -> {
                    spawnDripParticle(world, blockposition, iblockdata, pointeddripstoneblock_a.fluid);
                });
            }
        }
    }

    @Override
    protected void tick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if (isStalagmite(iblockdata) && !this.canSurvive(iblockdata, worldserver, blockposition)) {
            worldserver.destroyBlock(blockposition, true);
        } else {
            spawnFallingStalactite(iblockdata, worldserver, blockposition);
        }

    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        maybeTransferFluid(iblockdata, worldserver, blockposition, randomsource.nextFloat());
        if (randomsource.nextFloat() < 0.011377778F && isStalactiteStartPos(iblockdata, worldserver, blockposition)) {
            growStalactiteOrStalagmiteIfPossible(iblockdata, worldserver, blockposition, randomsource);
        }

    }

    @VisibleForTesting
    public static void maybeTransferFluid(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, float f) {
        if (f <= 0.17578125F || f <= 0.05859375F) {
            if (isStalactiteStartPos(iblockdata, worldserver, blockposition)) {
                Optional<PointedDripstoneBlock.a> optional = getFluidAboveStalactite(worldserver, blockposition, iblockdata);

                if (!optional.isEmpty()) {
                    FluidType fluidtype = ((PointedDripstoneBlock.a) optional.get()).fluid;
                    float f1;

                    if (fluidtype == FluidTypes.WATER) {
                        f1 = 0.17578125F;
                    } else {
                        if (fluidtype != FluidTypes.LAVA) {
                            return;
                        }

                        f1 = 0.05859375F;
                    }

                    if (f < f1) {
                        BlockPosition blockposition1 = findTip(iblockdata, worldserver, blockposition, 11, false);

                        if (blockposition1 != null) {
                            if (((PointedDripstoneBlock.a) optional.get()).sourceState.is(Blocks.MUD) && fluidtype == FluidTypes.WATER) {
                                IBlockData iblockdata1 = Blocks.CLAY.defaultBlockState();

                                worldserver.setBlockAndUpdate(((PointedDripstoneBlock.a) optional.get()).pos, iblockdata1);
                                Block.pushEntitiesUp(((PointedDripstoneBlock.a) optional.get()).sourceState, iblockdata1, worldserver, ((PointedDripstoneBlock.a) optional.get()).pos);
                                worldserver.gameEvent(GameEvent.BLOCK_CHANGE, ((PointedDripstoneBlock.a) optional.get()).pos, GameEvent.a.of(iblockdata1));
                                worldserver.levelEvent(1504, blockposition1, 0);
                            } else {
                                BlockPosition blockposition2 = findFillableCauldronBelowStalactiteTip(worldserver, blockposition1, fluidtype);

                                if (blockposition2 != null) {
                                    worldserver.levelEvent(1504, blockposition1, 0);
                                    int i = blockposition1.getY() - blockposition2.getY();
                                    int j = 50 + i;
                                    IBlockData iblockdata2 = worldserver.getBlockState(blockposition2);

                                    worldserver.scheduleTick(blockposition2, iblockdata2.getBlock(), j);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        GeneratorAccess generatoraccess = blockactioncontext.getLevel();
        BlockPosition blockposition = blockactioncontext.getClickedPos();
        EnumDirection enumdirection = blockactioncontext.getNearestLookingVerticalDirection().getOpposite();
        EnumDirection enumdirection1 = calculateTipDirection(generatoraccess, blockposition, enumdirection);

        if (enumdirection1 == null) {
            return null;
        } else {
            boolean flag = !blockactioncontext.isSecondaryUseActive();
            DripstoneThickness dripstonethickness = calculateDripstoneThickness(generatoraccess, blockposition, enumdirection1, flag);

            return dripstonethickness == null ? null : (IBlockData) ((IBlockData) ((IBlockData) this.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, enumdirection1)).setValue(PointedDripstoneBlock.THICKNESS, dripstonethickness)).setValue(PointedDripstoneBlock.WATERLOGGED, generatoraccess.getFluidState(blockposition).getType() == FluidTypes.WATER);
        }
    }

    @Override
    protected Fluid getFluidState(IBlockData iblockdata) {
        return (Boolean) iblockdata.getValue(PointedDripstoneBlock.WATERLOGGED) ? FluidTypes.WATER.getSource(false) : super.getFluidState(iblockdata);
    }

    @Override
    protected VoxelShape getOcclusionShape(IBlockData iblockdata) {
        return VoxelShapes.empty();
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        VoxelShape voxelshape;

        switch ((DripstoneThickness) iblockdata.getValue(PointedDripstoneBlock.THICKNESS)) {
            case TIP_MERGE:
                voxelshape = PointedDripstoneBlock.SHAPE_TIP_MERGE;
                break;
            case TIP:
                voxelshape = iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION) == EnumDirection.DOWN ? PointedDripstoneBlock.SHAPE_TIP_DOWN : PointedDripstoneBlock.SHAPE_TIP_UP;
                break;
            case FRUSTUM:
                voxelshape = PointedDripstoneBlock.SHAPE_FRUSTUM;
                break;
            case MIDDLE:
                voxelshape = PointedDripstoneBlock.SHAPE_MIDDLE;
                break;
            case BASE:
                voxelshape = PointedDripstoneBlock.SHAPE_BASE;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        VoxelShape voxelshape1 = voxelshape;

        return voxelshape1.move(iblockdata.getOffset(blockposition));
    }

    @Override
    protected boolean isCollisionShapeFullBlock(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition) {
        return false;
    }

    @Override
    protected float getMaxHorizontalOffset() {
        return PointedDripstoneBlock.MAX_HORIZONTAL_OFFSET;
    }

    @Override
    public void onBrokenAfterFall(World world, BlockPosition blockposition, EntityFallingBlock entityfallingblock) {
        if (!entityfallingblock.isSilent()) {
            world.levelEvent(1045, blockposition, 0);
        }

    }

    @Override
    public DamageSource getFallDamageSource(Entity entity) {
        return entity.damageSources().fallingStalactite(entity);
    }

    private static void spawnFallingStalactite(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition) {
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = blockposition.mutable();

        for (IBlockData iblockdata1 = iblockdata; isStalactite(iblockdata1); iblockdata1 = worldserver.getBlockState(blockposition_mutableblockposition)) {
            EntityFallingBlock entityfallingblock = EntityFallingBlock.fall(worldserver, blockposition_mutableblockposition, iblockdata1);

            if (isTip(iblockdata1, true)) {
                int i = Math.max(1 + blockposition.getY() - blockposition_mutableblockposition.getY(), 6);
                float f = 1.0F * (float) i;

                entityfallingblock.setHurtsEntities(f, 40);
                break;
            }

            blockposition_mutableblockposition.move(EnumDirection.DOWN);
        }

    }

    @VisibleForTesting
    public static void growStalactiteOrStalagmiteIfPossible(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        IBlockData iblockdata1 = worldserver.getBlockState(blockposition.above(1));
        IBlockData iblockdata2 = worldserver.getBlockState(blockposition.above(2));

        if (canGrow(iblockdata1, iblockdata2)) {
            BlockPosition blockposition1 = findTip(iblockdata, worldserver, blockposition, 7, false);

            if (blockposition1 != null) {
                IBlockData iblockdata3 = worldserver.getBlockState(blockposition1);

                if (canDrip(iblockdata3) && canTipGrow(iblockdata3, worldserver, blockposition1)) {
                    if (randomsource.nextBoolean()) {
                        grow(worldserver, blockposition1, EnumDirection.DOWN);
                    } else {
                        growStalagmiteBelow(worldserver, blockposition1);
                    }

                }
            }
        }
    }

    private static void growStalagmiteBelow(WorldServer worldserver, BlockPosition blockposition) {
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = blockposition.mutable();

        for (int i = 0; i < 10; ++i) {
            blockposition_mutableblockposition.move(EnumDirection.DOWN);
            IBlockData iblockdata = worldserver.getBlockState(blockposition_mutableblockposition);

            if (!iblockdata.getFluidState().isEmpty()) {
                return;
            }

            if (isUnmergedTipWithDirection(iblockdata, EnumDirection.UP) && canTipGrow(iblockdata, worldserver, blockposition_mutableblockposition)) {
                grow(worldserver, blockposition_mutableblockposition, EnumDirection.UP);
                return;
            }

            if (isValidPointedDripstonePlacement(worldserver, blockposition_mutableblockposition, EnumDirection.UP) && !worldserver.isWaterAt(blockposition_mutableblockposition.below())) {
                grow(worldserver, blockposition_mutableblockposition.below(), EnumDirection.UP);
                return;
            }

            if (!canDripThrough(worldserver, blockposition_mutableblockposition, iblockdata)) {
                return;
            }
        }

    }

    private static void grow(WorldServer worldserver, BlockPosition blockposition, EnumDirection enumdirection) {
        BlockPosition blockposition1 = blockposition.relative(enumdirection);
        IBlockData iblockdata = worldserver.getBlockState(blockposition1);

        if (isUnmergedTipWithDirection(iblockdata, enumdirection.getOpposite())) {
            createMergedTips(iblockdata, worldserver, blockposition1);
        } else if (iblockdata.isAir() || iblockdata.is(Blocks.WATER)) {
            createDripstone(worldserver, blockposition1, enumdirection, DripstoneThickness.TIP, blockposition); // CraftBukkit
        }

    }

    private static void createDripstone(GeneratorAccess generatoraccess, BlockPosition blockposition, EnumDirection enumdirection, DripstoneThickness dripstonethickness, BlockPosition source) { // CraftBukkit
        IBlockData iblockdata = (IBlockData) ((IBlockData) ((IBlockData) Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, enumdirection)).setValue(PointedDripstoneBlock.THICKNESS, dripstonethickness)).setValue(PointedDripstoneBlock.WATERLOGGED, generatoraccess.getFluidState(blockposition).getType() == FluidTypes.WATER);

        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(generatoraccess, source, blockposition, iblockdata, 3); // CraftBukkit
    }

    private static void createMergedTips(IBlockData iblockdata, GeneratorAccess generatoraccess, BlockPosition blockposition) {
        BlockPosition blockposition1;
        BlockPosition blockposition2;

        if (iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION) == EnumDirection.UP) {
            blockposition2 = blockposition;
            blockposition1 = blockposition.above();
        } else {
            blockposition1 = blockposition;
            blockposition2 = blockposition.below();
        }

        createDripstone(generatoraccess, blockposition1, EnumDirection.DOWN, DripstoneThickness.TIP_MERGE, blockposition); // CraftBukkit
        createDripstone(generatoraccess, blockposition2, EnumDirection.UP, DripstoneThickness.TIP_MERGE, blockposition); // CraftBukkit
    }

    public static void spawnDripParticle(World world, BlockPosition blockposition, IBlockData iblockdata) {
        getFluidAboveStalactite(world, blockposition, iblockdata).ifPresent((pointeddripstoneblock_a) -> {
            spawnDripParticle(world, blockposition, iblockdata, pointeddripstoneblock_a.fluid);
        });
    }

    private static void spawnDripParticle(World world, BlockPosition blockposition, IBlockData iblockdata, FluidType fluidtype) {
        Vec3D vec3d = iblockdata.getOffset(blockposition);
        double d0 = 0.0625D;
        double d1 = (double) blockposition.getX() + 0.5D + vec3d.x;
        double d2 = (double) blockposition.getY() + PointedDripstoneBlock.STALACTITE_DRIP_START_PIXEL - 0.0625D;
        double d3 = (double) blockposition.getZ() + 0.5D + vec3d.z;
        FluidType fluidtype1 = getDripFluid(world, fluidtype);
        ParticleParam particleparam = fluidtype1.is(TagsFluid.LAVA) ? Particles.DRIPPING_DRIPSTONE_LAVA : Particles.DRIPPING_DRIPSTONE_WATER;

        world.addParticle(particleparam, d1, d2, d3, 0.0D, 0.0D, 0.0D);
    }

    @Nullable
    private static BlockPosition findTip(IBlockData iblockdata, GeneratorAccess generatoraccess, BlockPosition blockposition, int i, boolean flag) {
        if (isTip(iblockdata, flag)) {
            return blockposition;
        } else {
            EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION);
            BiPredicate<BlockPosition, IBlockData> bipredicate = (blockposition1, iblockdata1) -> {
                return iblockdata1.is(Blocks.POINTED_DRIPSTONE) && iblockdata1.getValue(PointedDripstoneBlock.TIP_DIRECTION) == enumdirection;
            };

            return (BlockPosition) findBlockVertical(generatoraccess, blockposition, enumdirection.getAxisDirection(), bipredicate, (iblockdata1) -> {
                return isTip(iblockdata1, flag);
            }, i).orElse(null); // CraftBukkit - decompile error
        }
    }

    @Nullable
    private static EnumDirection calculateTipDirection(IWorldReader iworldreader, BlockPosition blockposition, EnumDirection enumdirection) {
        EnumDirection enumdirection1;

        if (isValidPointedDripstonePlacement(iworldreader, blockposition, enumdirection)) {
            enumdirection1 = enumdirection;
        } else {
            if (!isValidPointedDripstonePlacement(iworldreader, blockposition, enumdirection.getOpposite())) {
                return null;
            }

            enumdirection1 = enumdirection.getOpposite();
        }

        return enumdirection1;
    }

    private static DripstoneThickness calculateDripstoneThickness(IWorldReader iworldreader, BlockPosition blockposition, EnumDirection enumdirection, boolean flag) {
        EnumDirection enumdirection1 = enumdirection.getOpposite();
        IBlockData iblockdata = iworldreader.getBlockState(blockposition.relative(enumdirection));

        if (isPointedDripstoneWithDirection(iblockdata, enumdirection1)) {
            return !flag && iblockdata.getValue(PointedDripstoneBlock.THICKNESS) != DripstoneThickness.TIP_MERGE ? DripstoneThickness.TIP : DripstoneThickness.TIP_MERGE;
        } else if (!isPointedDripstoneWithDirection(iblockdata, enumdirection)) {
            return DripstoneThickness.TIP;
        } else {
            DripstoneThickness dripstonethickness = (DripstoneThickness) iblockdata.getValue(PointedDripstoneBlock.THICKNESS);

            if (dripstonethickness != DripstoneThickness.TIP && dripstonethickness != DripstoneThickness.TIP_MERGE) {
                IBlockData iblockdata1 = iworldreader.getBlockState(blockposition.relative(enumdirection1));

                return !isPointedDripstoneWithDirection(iblockdata1, enumdirection) ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            } else {
                return DripstoneThickness.FRUSTUM;
            }
        }
    }

    public static boolean canDrip(IBlockData iblockdata) {
        return isStalactite(iblockdata) && iblockdata.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP && !(Boolean) iblockdata.getValue(PointedDripstoneBlock.WATERLOGGED);
    }

    private static boolean canTipGrow(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition) {
        EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION);
        BlockPosition blockposition1 = blockposition.relative(enumdirection);
        IBlockData iblockdata1 = worldserver.getBlockState(blockposition1);

        return !iblockdata1.getFluidState().isEmpty() ? false : (iblockdata1.isAir() ? true : isUnmergedTipWithDirection(iblockdata1, enumdirection.getOpposite()));
    }

    private static Optional<BlockPosition> findRootBlock(World world, BlockPosition blockposition, IBlockData iblockdata, int i) {
        EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION);
        BiPredicate<BlockPosition, IBlockData> bipredicate = (blockposition1, iblockdata1) -> {
            return iblockdata1.is(Blocks.POINTED_DRIPSTONE) && iblockdata1.getValue(PointedDripstoneBlock.TIP_DIRECTION) == enumdirection;
        };

        return findBlockVertical(world, blockposition, enumdirection.getOpposite().getAxisDirection(), bipredicate, (iblockdata1) -> {
            return !iblockdata1.is(Blocks.POINTED_DRIPSTONE);
        }, i);
    }

    private static boolean isValidPointedDripstonePlacement(IWorldReader iworldreader, BlockPosition blockposition, EnumDirection enumdirection) {
        BlockPosition blockposition1 = blockposition.relative(enumdirection.getOpposite());
        IBlockData iblockdata = iworldreader.getBlockState(blockposition1);

        return iblockdata.isFaceSturdy(iworldreader, blockposition1, enumdirection) || isPointedDripstoneWithDirection(iblockdata, enumdirection);
    }

    private static boolean isTip(IBlockData iblockdata, boolean flag) {
        if (!iblockdata.is(Blocks.POINTED_DRIPSTONE)) {
            return false;
        } else {
            DripstoneThickness dripstonethickness = (DripstoneThickness) iblockdata.getValue(PointedDripstoneBlock.THICKNESS);

            return dripstonethickness == DripstoneThickness.TIP || flag && dripstonethickness == DripstoneThickness.TIP_MERGE;
        }
    }

    private static boolean isUnmergedTipWithDirection(IBlockData iblockdata, EnumDirection enumdirection) {
        return isTip(iblockdata, false) && iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION) == enumdirection;
    }

    private static boolean isStalactite(IBlockData iblockdata) {
        return isPointedDripstoneWithDirection(iblockdata, EnumDirection.DOWN);
    }

    private static boolean isStalagmite(IBlockData iblockdata) {
        return isPointedDripstoneWithDirection(iblockdata, EnumDirection.UP);
    }

    private static boolean isStalactiteStartPos(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        return isStalactite(iblockdata) && !iworldreader.getBlockState(blockposition.above()).is(Blocks.POINTED_DRIPSTONE);
    }

    @Override
    protected boolean isPathfindable(IBlockData iblockdata, PathMode pathmode) {
        return false;
    }

    private static boolean isPointedDripstoneWithDirection(IBlockData iblockdata, EnumDirection enumdirection) {
        return iblockdata.is(Blocks.POINTED_DRIPSTONE) && iblockdata.getValue(PointedDripstoneBlock.TIP_DIRECTION) == enumdirection;
    }

    @Nullable
    private static BlockPosition findFillableCauldronBelowStalactiteTip(World world, BlockPosition blockposition, FluidType fluidtype) {
        Predicate<IBlockData> predicate = (iblockdata) -> {
            return iblockdata.getBlock() instanceof AbstractCauldronBlock && ((AbstractCauldronBlock) iblockdata.getBlock()).canReceiveStalactiteDrip(fluidtype);
        };
        BiPredicate<BlockPosition, IBlockData> bipredicate = (blockposition1, iblockdata) -> {
            return canDripThrough(world, blockposition1, iblockdata);
        };

        return (BlockPosition) findBlockVertical(world, blockposition, EnumDirection.DOWN.getAxisDirection(), bipredicate, predicate, 11).orElse(null); // CraftBukkit - decompile error
    }

    @Nullable
    public static BlockPosition findStalactiteTipAboveCauldron(World world, BlockPosition blockposition) {
        BiPredicate<BlockPosition, IBlockData> bipredicate = (blockposition1, iblockdata) -> {
            return canDripThrough(world, blockposition1, iblockdata);
        };

        return (BlockPosition) findBlockVertical(world, blockposition, EnumDirection.UP.getAxisDirection(), bipredicate, PointedDripstoneBlock::canDrip, 11).orElse(null); // CraftBukkit - decompile error
    }

    public static FluidType getCauldronFillFluidType(WorldServer worldserver, BlockPosition blockposition) {
        return (FluidType) getFluidAboveStalactite(worldserver, blockposition, worldserver.getBlockState(blockposition)).map((pointeddripstoneblock_a) -> {
            return pointeddripstoneblock_a.fluid;
        }).filter(PointedDripstoneBlock::canFillCauldron).orElse(FluidTypes.EMPTY);
    }

    private static Optional<PointedDripstoneBlock.a> getFluidAboveStalactite(World world, BlockPosition blockposition, IBlockData iblockdata) {
        return !isStalactite(iblockdata) ? Optional.empty() : findRootBlock(world, blockposition, iblockdata, 11).map((blockposition1) -> {
            BlockPosition blockposition2 = blockposition1.above();
            IBlockData iblockdata1 = world.getBlockState(blockposition2);
            FluidType fluidtype;

            if (iblockdata1.is(Blocks.MUD) && !world.dimensionType().ultraWarm()) {
                fluidtype = FluidTypes.WATER;
            } else {
                fluidtype = world.getFluidState(blockposition2).getType();
            }

            return new PointedDripstoneBlock.a(blockposition2, fluidtype, iblockdata1);
        });
    }

    private static boolean canFillCauldron(FluidType fluidtype) {
        return fluidtype == FluidTypes.LAVA || fluidtype == FluidTypes.WATER;
    }

    private static boolean canGrow(IBlockData iblockdata, IBlockData iblockdata1) {
        return iblockdata.is(Blocks.DRIPSTONE_BLOCK) && iblockdata1.is(Blocks.WATER) && iblockdata1.getFluidState().isSource();
    }

    private static FluidType getDripFluid(World world, FluidType fluidtype) {
        return (FluidType) (fluidtype.isSame(FluidTypes.EMPTY) ? (world.dimensionType().ultraWarm() ? FluidTypes.LAVA : FluidTypes.WATER) : fluidtype);
    }

    private static Optional<BlockPosition> findBlockVertical(GeneratorAccess generatoraccess, BlockPosition blockposition, EnumDirection.EnumAxisDirection enumdirection_enumaxisdirection, BiPredicate<BlockPosition, IBlockData> bipredicate, Predicate<IBlockData> predicate, int i) {
        EnumDirection enumdirection = EnumDirection.get(enumdirection_enumaxisdirection, EnumDirection.EnumAxis.Y);
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = blockposition.mutable();

        for (int j = 1; j < i; ++j) {
            blockposition_mutableblockposition.move(enumdirection);
            IBlockData iblockdata = generatoraccess.getBlockState(blockposition_mutableblockposition);

            if (predicate.test(iblockdata)) {
                return Optional.of(blockposition_mutableblockposition.immutable());
            }

            if (generatoraccess.isOutsideBuildHeight(blockposition_mutableblockposition.getY()) || !bipredicate.test(blockposition_mutableblockposition, iblockdata)) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static boolean canDripThrough(IBlockAccess iblockaccess, BlockPosition blockposition, IBlockData iblockdata) {
        if (iblockdata.isAir()) {
            return true;
        } else if (iblockdata.isSolidRender()) {
            return false;
        } else if (!iblockdata.getFluidState().isEmpty()) {
            return false;
        } else {
            VoxelShape voxelshape = iblockdata.getCollisionShape(iblockaccess, blockposition);

            return !VoxelShapes.joinIsNotEmpty(PointedDripstoneBlock.REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK, voxelshape, OperatorBoolean.AND);
        }
    }

    static record a(BlockPosition pos, FluidType fluid, IBlockData sourceState) {

    }
}
