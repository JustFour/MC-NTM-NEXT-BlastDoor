package com.blastdoor.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import traincraft.track.block.TrackBlock;
import traincraft.track.block.TrackBlockEntity;

/**
 * Основа блок-сущностей всех дверей в сборке под Traincraft.
 *
 * <p>Поезда Traincraft считают блок путём, только если это его {@code TrackBlock}, а в блоке лежит
 * его {@code TrackBlockEntity}. Блок-сущность в клетке одна, поэтому логика двери и «рельс» должны
 * быть одним объектом: дверь наследует блок-сущность пути. По умолчанию та описывает короткий прямой
 * отрезок по центру блока — ровно то, что нужно в проёме.</p>
 *
 * <p>Тип у {@code TrackBlockEntity} зашит в конструкторе, поэтому наружу отдаётся тип двери: под ним
 * она сохраняется, загружается, тикает и рисуется нашим кодом.</p>
 */
public abstract class DoorBlockEntityBase extends TrackBlockEntity {
    /** Пусто, пока работает конструктор родителя. */
    private final @Nullable BlockEntityType<?> doorType;

    protected DoorBlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(pos, state);
        this.doorType = type;
        // Направление пути Traincraft хранит и в состоянии блока, и в блок-сущности: держим их равными.
        if (state.hasProperty(TrackBlock.FACING)) {
            setFacing(TrackBlock.metaFromFacing(state.getValue(TrackBlock.FACING)));
        }
    }

    @Override
    public BlockEntityType<?> getType() {
        return doorType != null ? doorType : super.getType();
    }

    /** Конструктор родителя проверяет состояние блока раньше, чем тип двери записан в поле. */
    @Override
    public boolean isValidBlockState(BlockState state) {
        return doorType == null || doorType.isValid(state);
    }
}
