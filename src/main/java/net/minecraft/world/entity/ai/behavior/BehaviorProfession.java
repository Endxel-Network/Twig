package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.EntityVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
// CraftBukkit end

public class BehaviorProfession {

    public BehaviorProfession() {}

    public static BehaviorControl<EntityVillager> create() {
        return BehaviorBuilder.create((behaviorbuilder_b) -> {
            return behaviorbuilder_b.group(behaviorbuilder_b.absent(MemoryModuleType.JOB_SITE)).apply(behaviorbuilder_b, (memoryaccessor) -> {
                return (worldserver, entityvillager, i) -> {
                    VillagerData villagerdata = entityvillager.getVillagerData();
                    boolean flag = !villagerdata.profession().is(VillagerProfession.NONE) && !villagerdata.profession().is(VillagerProfession.NITWIT);

                    if (flag && entityvillager.getVillagerXp() == 0 && villagerdata.level() <= 1) {
                        // CraftBukkit start
                        VillagerCareerChangeEvent event = CraftEventFactory.callVillagerCareerChangeEvent(entityvillager, CraftVillager.CraftProfession.minecraftHolderToBukkit(worldserver.registryAccess().getOrThrow(VillagerProfession.NONE)), VillagerCareerChangeEvent.ChangeReason.LOSING_JOB);
                        if (event.isCancelled()) {
                            return false;
                        }

                        entityvillager.setVillagerData(entityvillager.getVillagerData().withProfession(CraftVillager.CraftProfession.bukkitToMinecraftHolder(event.getProfession())));
                        // CraftBukkit end
                        entityvillager.refreshBrain(worldserver);
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}
