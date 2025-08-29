package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidType;

// CraftBukkit start
import org.bukkit.event.entity.CreatureSpawnEvent;
// CraftBukkit end

public class MobBucketItem extends ItemBucket {

    private final EntityTypes<? extends EntityInsentient> type;
    private final SoundEffect emptySound;

    public MobBucketItem(EntityTypes<? extends EntityInsentient> entitytypes, FluidType fluidtype, SoundEffect soundeffect, Item.Info item_info) {
        super(fluidtype, item_info);
        this.type = entitytypes;
        this.emptySound = soundeffect;
    }

    @Override
    public void checkExtraContent(@Nullable EntityLiving entityliving, World world, ItemStack itemstack, BlockPosition blockposition) {
        if (world instanceof WorldServer) {
            this.spawn((WorldServer) world, itemstack, blockposition);
            world.gameEvent(entityliving, (Holder) GameEvent.ENTITY_PLACE, blockposition);
        }

    }

    @Override
    protected void playEmptySound(@Nullable EntityLiving entityliving, GeneratorAccess generatoraccess, BlockPosition blockposition) {
        generatoraccess.playSound(entityliving, blockposition, this.emptySound, SoundCategory.NEUTRAL, 1.0F, 1.0F);
    }

    private void spawn(WorldServer worldserver, ItemStack itemstack, BlockPosition blockposition) {
        EntityInsentient entityinsentient = this.type.create(worldserver, EntityTypes.createDefaultStackConfig(worldserver, itemstack, (EntityLiving) null), blockposition, EntitySpawnReason.BUCKET, true, false);

        if (entityinsentient instanceof Bucketable bucketable) {
            CustomData customdata = (CustomData) itemstack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);

            bucketable.loadFromBucketTag(customdata.copyTag());
            bucketable.setFromBucket(true);
        }

        if (entityinsentient != null) {
            worldserver.addFreshEntityWithPassengers(entityinsentient, CreatureSpawnEvent.SpawnReason.BUCKET); // CraftBukkit
            entityinsentient.playAmbientSound();
        }

    }
}
