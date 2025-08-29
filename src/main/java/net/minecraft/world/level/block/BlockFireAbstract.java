package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.particles.Particles;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.portal.BlockPortalShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;

// CraftBukkit start
import net.minecraft.world.item.context.ItemActionContext;
// CraftBukkit end

public abstract class BlockFireAbstract extends Block {

    private static final int SECONDS_ON_FIRE = 8;
    private static final int MIN_FIRE_TICKS_TO_ADD = 1;
    private static final int MAX_FIRE_TICKS_TO_ADD = 3;
    private final float fireDamage;
    protected static final VoxelShape SHAPE = Block.column(16.0D, 0.0D, 1.0D);

    public BlockFireAbstract(BlockBase.Info blockbase_info, float f) {
        super(blockbase_info);
        this.fireDamage = f;
    }

    @Override
    protected abstract MapCodec<? extends BlockFireAbstract> codec();

    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        return getState(blockactioncontext.getLevel(), blockactioncontext.getClickedPos());
    }

    public static IBlockData getState(IBlockAccess iblockaccess, BlockPosition blockposition) {
        BlockPosition blockposition1 = blockposition.below();
        IBlockData iblockdata = iblockaccess.getBlockState(blockposition1);

        return BlockSoulFire.canSurviveOnBlock(iblockdata) ? Blocks.SOUL_FIRE.defaultBlockState() : ((BlockFire) Blocks.FIRE).getStateForPlacement(iblockaccess, blockposition);
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockFireAbstract.SHAPE;
    }

    @Override
    public void animateTick(IBlockData iblockdata, World world, BlockPosition blockposition, RandomSource randomsource) {
        if (randomsource.nextInt(24) == 0) {
            world.playLocalSound((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, SoundEffects.FIRE_AMBIENT, SoundCategory.BLOCKS, 1.0F + randomsource.nextFloat(), randomsource.nextFloat() * 0.7F + 0.3F, false);
        }

        BlockPosition blockposition1 = blockposition.below();
        IBlockData iblockdata1 = world.getBlockState(blockposition1);

        if (!this.canBurn(iblockdata1) && !iblockdata1.isFaceSturdy(world, blockposition1, EnumDirection.UP)) {
            if (this.canBurn(world.getBlockState(blockposition.west()))) {
                for (int i = 0; i < 2; ++i) {
                    double d0 = (double) blockposition.getX() + randomsource.nextDouble() * (double) 0.1F;
                    double d1 = (double) blockposition.getY() + randomsource.nextDouble();
                    double d2 = (double) blockposition.getZ() + randomsource.nextDouble();

                    world.addParticle(Particles.LARGE_SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(world.getBlockState(blockposition.east()))) {
                for (int j = 0; j < 2; ++j) {
                    double d3 = (double) (blockposition.getX() + 1) - randomsource.nextDouble() * (double) 0.1F;
                    double d4 = (double) blockposition.getY() + randomsource.nextDouble();
                    double d5 = (double) blockposition.getZ() + randomsource.nextDouble();

                    world.addParticle(Particles.LARGE_SMOKE, d3, d4, d5, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(world.getBlockState(blockposition.north()))) {
                for (int k = 0; k < 2; ++k) {
                    double d6 = (double) blockposition.getX() + randomsource.nextDouble();
                    double d7 = (double) blockposition.getY() + randomsource.nextDouble();
                    double d8 = (double) blockposition.getZ() + randomsource.nextDouble() * (double) 0.1F;

                    world.addParticle(Particles.LARGE_SMOKE, d6, d7, d8, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(world.getBlockState(blockposition.south()))) {
                for (int l = 0; l < 2; ++l) {
                    double d9 = (double) blockposition.getX() + randomsource.nextDouble();
                    double d10 = (double) blockposition.getY() + randomsource.nextDouble();
                    double d11 = (double) (blockposition.getZ() + 1) - randomsource.nextDouble() * (double) 0.1F;

                    world.addParticle(Particles.LARGE_SMOKE, d9, d10, d11, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(world.getBlockState(blockposition.above()))) {
                for (int i1 = 0; i1 < 2; ++i1) {
                    double d12 = (double) blockposition.getX() + randomsource.nextDouble();
                    double d13 = (double) (blockposition.getY() + 1) - randomsource.nextDouble() * (double) 0.1F;
                    double d14 = (double) blockposition.getZ() + randomsource.nextDouble();

                    world.addParticle(Particles.LARGE_SMOKE, d12, d13, d14, 0.0D, 0.0D, 0.0D);
                }
            }
        } else {
            for (int j1 = 0; j1 < 3; ++j1) {
                double d15 = (double) blockposition.getX() + randomsource.nextDouble();
                double d16 = (double) blockposition.getY() + randomsource.nextDouble() * 0.5D + 0.5D;
                double d17 = (double) blockposition.getZ() + randomsource.nextDouble();

                world.addParticle(Particles.LARGE_SMOKE, d15, d16, d17, 0.0D, 0.0D, 0.0D);
            }
        }

    }

    protected abstract boolean canBurn(IBlockData iblockdata);

    @Override
    protected void entityInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity, InsideBlockEffectApplier insideblockeffectapplier) {
        insideblockeffectapplier.apply(InsideBlockEffectType.FIRE_IGNITE);
        insideblockeffectapplier.runAfter(InsideBlockEffectType.FIRE_IGNITE, (entity1) -> {
            entity1.hurt(entity1.level().damageSources().inFire(), this.fireDamage);
        });
    }

    public static void fireIgnite(Entity entity) {
        if (!entity.fireImmune()) {
            if (entity.getRemainingFireTicks() < 0) {
                entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
            } else if (entity instanceof EntityPlayer) {
                int i = entity.level().getRandom().nextInt(1, 3);

                entity.setRemainingFireTicks(entity.getRemainingFireTicks() + i);
            }

            if (entity.getRemainingFireTicks() >= 0) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityCombustEvent event = new org.bukkit.event.entity.EntityCombustByBlockEvent(entity.getBukkitEntity().getLocation().getBlock(), entity.getBukkitEntity(), 8.0F); // PAIL - TODO
                entity.level().getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.igniteForSeconds(event.getDuration(), false);
                }
                // CraftBukkit end
            }
        }

    }

    @Override
    protected void onPlace(IBlockData iblockdata, World world, BlockPosition blockposition, IBlockData iblockdata1, boolean flag, ItemActionContext context) { // CraftBukkit - context
        if (!iblockdata1.is(iblockdata.getBlock())) {
            if (inPortalDimension(world)) {
                Optional<BlockPortalShape> optional = BlockPortalShape.findEmptyPortalShape(world, blockposition, EnumDirection.EnumAxis.X);

                if (optional.isPresent()) {
                    ((BlockPortalShape) optional.get()).createPortalBlocks(world, (context == null) ? null : context.getPlayer()); // CraftBukkit - player
                    return;
                }
            }

            if (!iblockdata.canSurvive(world, blockposition)) {
                fireExtinguished(world, blockposition); // CraftBukkit - fuel block broke
            }

        }
    }

    private static boolean inPortalDimension(World world) {
        return world.getTypeKey() == net.minecraft.world.level.dimension.WorldDimension.OVERWORLD || world.getTypeKey() == net.minecraft.world.level.dimension.WorldDimension.NETHER; // CraftBukkit - getTypeKey()
    }

    @Override
    protected void spawnDestroyParticles(World world, EntityHuman entityhuman, BlockPosition blockposition, IBlockData iblockdata) {}

    @Override
    public IBlockData playerWillDestroy(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman) {
        if (!world.isClientSide()) {
            world.levelEvent((Entity) null, 1009, blockposition, 0);
        }

        return super.playerWillDestroy(world, blockposition, iblockdata, entityhuman);
    }

    public static boolean canBePlacedAt(World world, BlockPosition blockposition, EnumDirection enumdirection) {
        IBlockData iblockdata = world.getBlockState(blockposition);

        return !iblockdata.isAir() ? false : getState(world, blockposition).canSurvive(world, blockposition) || isPortal(world, blockposition, enumdirection);
    }

    private static boolean isPortal(World world, BlockPosition blockposition, EnumDirection enumdirection) {
        if (!inPortalDimension(world)) {
            return false;
        } else {
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = blockposition.mutable();
            boolean flag = false;

            for (EnumDirection enumdirection1 : EnumDirection.values()) {
                if (world.getBlockState(blockposition_mutableblockposition.set(blockposition).move(enumdirection1)).is(Blocks.OBSIDIAN)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                EnumDirection.EnumAxis enumdirection_enumaxis = enumdirection.getAxis().isHorizontal() ? enumdirection.getCounterClockWise().getAxis() : EnumDirection.EnumDirectionLimit.HORIZONTAL.getRandomAxis(world.random);

                return BlockPortalShape.findEmptyPortalShape(world, blockposition, enumdirection_enumaxis).isPresent();
            }
        }
    }

    // CraftBukkit start
    protected void fireExtinguished(net.minecraft.world.level.GeneratorAccess world, BlockPosition position) {
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, position, Blocks.AIR.defaultBlockState()).isCancelled()) {
            world.removeBlock(position, false);
        }
    }
    // CraftBukkit end
}
