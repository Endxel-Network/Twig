package org.bukkit.craftbukkit.inventory.components.consumable.effects;

import java.util.Map;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import org.bukkit.inventory.meta.components.consumable.effects.ConsumableClearEffects;

public class CraftConsumableClearEffects extends CraftConsumableEffect<ClearAllStatusEffectsConsumeEffect> implements ConsumableClearEffects {

    public CraftConsumableClearEffects(ClearAllStatusEffectsConsumeEffect consumeEffect) {
        super(consumeEffect);
    }

    public CraftConsumableClearEffects(CraftConsumableClearEffects consumeEffect) {
        super(consumeEffect);
    }

    @Override
    public Map<String, Object> serialize() {
        return Map.of();
    }
}
