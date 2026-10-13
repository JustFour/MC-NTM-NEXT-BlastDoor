package com.blastdoor.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Основа блок-сущностей всех дверей. От варианта сборки зависит только она: в обычной сборке это
 * простая блок-сущность, в сборке под Traincraft ({@code -Ptc}) — блок-сущность его пути, чтобы
 * контроллер двери на рельсах был для поездов настоящим рельсом.
 */
public abstract class DoorBlockEntityBase extends BlockEntity {
    protected DoorBlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
