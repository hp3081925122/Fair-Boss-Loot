package com.hp.fairbossloot.drop;

import com.hp.fairbossloot.tracking.DamageTrackerData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 记录生物单次 dropAllDeathLoot 执行期间的各阶段掉落归类上下文。
 */
public final class DropCaptureState {
    private final DamageTrackerData.SettlementResult settlement;
    private final Set<ItemEntity> primaryLootTableDrops = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ItemEntity> customDeathDrops = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ItemEntity> equipmentDrops = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UUID, List<ItemStack>> extraPlayerLootTableDrops = new LinkedHashMap<>();

    public DropCaptureState(DamageTrackerData.SettlementResult settlement) {
        this.settlement = settlement;
    }

    public DamageTrackerData.SettlementResult settlement() {
        return this.settlement;
    }

    public void markPrimaryLootTableDrop(ItemEntity itemEntity) {
        this.primaryLootTableDrops.add(itemEntity);
    }

    public void markCustomDeathDrop(ItemEntity itemEntity) {
        this.customDeathDrops.add(itemEntity);
    }

    public void markEquipmentDrop(ItemEntity itemEntity) {
        this.equipmentDrops.add(itemEntity);
    }

    public void addExtraPlayerLootTableDrops(UUID playerId, List<ItemStack> stacks) {
        this.extraPlayerLootTableDrops.computeIfAbsent(playerId, id -> new ArrayList<>()).addAll(stacks);
    }

    public boolean isPrimaryLootTableDrop(ItemEntity itemEntity) {
        return this.primaryLootTableDrops.contains(itemEntity);
    }

    public boolean isCustomDeathDrop(ItemEntity itemEntity) {
        return this.customDeathDrops.contains(itemEntity);
    }

    public boolean isEquipmentDrop(ItemEntity itemEntity) {
        return this.equipmentDrops.contains(itemEntity);
    }

    public boolean isTrackedBaseDrop(ItemEntity itemEntity) {
        return this.primaryLootTableDrops.contains(itemEntity)
                || this.customDeathDrops.contains(itemEntity)
                || this.equipmentDrops.contains(itemEntity);
    }

    public List<ItemStack> getExtraPlayerLootTableDrops(UUID playerId) {
        return this.extraPlayerLootTableDrops.getOrDefault(playerId, List.of());
    }
}
