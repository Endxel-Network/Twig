package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateInteger;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.phys.shapes.VoxelShapes;

// CraftBukkit start
import java.util.Collections;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
// CraftBukkit end

public class BlockSweetBerryBush extends VegetationBlock implements IBlockFragilePlantElement {

    public static final MapCodec<BlockSweetBerryBush> CODEC = simpleCodec(BlockSweetBerryBush::new);
    private static final float HURT_SPEED_THRESHOLD = 0.003F;
    public static final int MAX_AGE = 3;
    public static final BlockStateInteger AGE = BlockProperties.AGE_3;
    private static final VoxelShape SHAPE_SAPLING = Block.column(10.0D, 0.0D, 8.0D);
    private static final VoxelShape SHAPE_GROWING = Block.column(14.0D, 0.0D, 16.0D);

    @Override
    public MapCodec<BlockSweetBerryBush> codec() {
        return BlockSweetBerryBush.CODEC;
    }

    public BlockSweetBerryBush(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) (this.stateDefinition.any()).setValue(BlockSweetBerryBush.AGE, 0));
    }

    @Override
    protected ItemStack getCloneItemStack(IWorldReader iworldreader, BlockPosition blockposition, IBlockData iblockdata, boolean flag) {
        return new ItemStack(Items.SWEET_BERRIES);
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        VoxelShape voxelshape;

        switch ((Integer) iblockdata.getValue(BlockSweetBerryBush.AGE)) {
            case 0:
                voxelshape = BlockSweetBerryBush.SHAPE_SAPLING;
                break;
            case 3:
                voxelshape = VoxelShapes.block();
                break;
            default:
                voxelshape = BlockSweetBerryBush.SHAPE_GROWING;
        }

        return voxelshape;
    }

    @Override
    protected boolean isRandomlyTicking(IBlockData iblockdata) {
        return (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE) < 3;
    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        int i = (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE);

        if (i < 3 && randomsource.nextFloat() < (worldserver.spigotConfig.sweetBerryModifier / (100.0f * 5)) && worldserver.getRawBrightness(blockposition.above(), 0) >= 9) { // Spigot - SPIGOT-7159: Better modifier resolution
            IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(BlockSweetBerryBush.AGE, i + 1);

            if (!CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, iblockdata1, 2)) return; // CraftBukkit
            worldserver.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.a.of(iblockdata1));
        }

    }

    @Override
    protected void entityInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity, InsideBlockEffectApplier insideblockeffectapplier) {
        if (entity instanceof EntityLiving && entity.getType() != EntityTypes.FOX && entity.getType() != EntityTypes.BEE) {
            entity.makeStuckInBlock(iblockdata, new Vec3D((double) 0.8F, 0.75D, (double) 0.8F));
            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if ((Integer) iblockdata.getValue(BlockSweetBerryBush.AGE) != 0) {
                    Vec3D vec3d = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());

                    if (vec3d.horizontalDistanceSqr() > 0.0D) {
                        double d0 = Math.abs(vec3d.x());
                        double d1 = Math.abs(vec3d.z());

                        if (d0 >= (double) 0.003F || d1 >= (double) 0.003F) {
                            entity.hurtServer(worldserver, world.damageSources().sweetBerryBush().directBlock(world, blockposition), 1.0F); // CraftBukkit
                        }
                    }

                    return;
                }
            }

        }
    }

    @Override
    protected EnumInteractionResult useItemOn(ItemStack itemstack, IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, EnumHand enumhand, MovingObjectPositionBlock movingobjectpositionblock) {
        int i = (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE);
        boolean flag = i == 3;

        return (EnumInteractionResult) (!flag && itemstack.is(Items.BONE_MEAL) ? EnumInteractionResult.PASS : super.useItemOn(itemstack, iblockdata, world, blockposition, entityhuman, enumhand, movingobjectpositionblock));
    }

    @Override
    protected EnumInteractionResult useWithoutItem(IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, MovingObjectPositionBlock movingobjectpositionblock) {
        int i = (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE);
        boolean flag = i == 3;

        if (i > 1) {
            int j = 1 + world.random.nextInt(2);

            // CraftBukkit start - useWithoutItem is always MAIN_HAND
            PlayerHarvestBlockEvent event = CraftEventFactory.callPlayerHarvestBlockEvent(world, blockposition, entityhuman, EnumHand.MAIN_HAND, Collections.singletonList(new ItemStack(Items.SWEET_BERRIES, j + (flag ? 1 : 0))));
            if (event.isCancelled()) {
                return EnumInteractionResult.SUCCESS; // We need to return a success either way, because making it PASS or FAIL will result in a bug where cancelling while harvesting w/ block in hand places block
            }
            for (org.bukkit.inventory.ItemStack itemStack : event.getItemsHarvested()) {
                popResource(world, blockposition, CraftItemStack.asNMSCopy(itemStack));
            }
            // CraftBukkit end
            world.playSound((Entity) null, blockposition, SoundEffects.SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1.0F, 0.8F + world.random.nextFloat() * 0.4F);
            IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(BlockSweetBerryBush.AGE, 1);

            world.setBlock(blockposition, iblockdata1, 2);
            world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.a.of(entityhuman, iblockdata1));
            return EnumInteractionResult.SUCCESS;
        } else {
            return super.useWithoutItem(iblockdata, world, blockposition, entityhuman, movingobjectpositionblock);
        }
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockSweetBerryBush.AGE);
    }

    @Override
    public boolean isValidBonemealTarget(IWorldReader iworldreader, BlockPosition blockposition, IBlockData iblockdata) {
        return (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE) < 3;
    }

    @Override
    public boolean isBonemealSuccess(World world, RandomSource randomsource, BlockPosition blockposition, IBlockData iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(WorldServer worldserver, RandomSource randomsource, BlockPosition blockposition, IBlockData iblockdata) {
        int i = Math.min(3, (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE) + 1);

        worldserver.setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockSweetBerryBush.AGE, i), 2);
    }
}
