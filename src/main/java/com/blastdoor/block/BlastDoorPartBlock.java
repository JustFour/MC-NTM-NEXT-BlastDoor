package com.blastdoor.block;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.blockentity.BlastDoorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Невидимая часть гермодвери. Сама ничего не рисует (створку рисует рендерер контроллера),
 * но даёт коллизию закрытой двери и принимает сигнал редстоуна.
 */
public class BlastDoorPartBlock extends Block {
    public static final MapCodec<BlastDoorPartBlock> CODEC = simpleCodec(BlastDoorPartBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty DX = IntegerProperty.create("dx", 0, 2);
    public static final IntegerProperty DY = IntegerProperty.create("dy", 0, 2);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public BlastDoorPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(DX, 0)
                .setValue(DY, 1)
                .setValue(OPEN, false));
    }

    @Override
    public MapCodec<BlastDoorPartBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DX, DY, OPEN);
    }

    public static BlockPos controllerPos(BlockPos partPos, BlockState partState) {
        return DoorGeometry.controllerFromPart(partPos, partState.getValue(FACING),
                partState.getValue(DX), partState.getValue(DY));
    }

    private static @Nullable BlastDoorBlockEntity controller(Level level, BlockPos partPos, BlockState partState) {
        return level.getBlockEntity(controllerPos(partPos, partState)) instanceof BlastDoorBlockEntity be ? be : null;
    }

    // ---------------------------------------------------------------- вид и формы

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : DoorGeometry.panelShape();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : DoorGeometry.panelShape();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(OPEN);
    }

    // ---------------------------------------------------------------- редстоун

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide()) {
            BlastDoorBlockEntity be = controller(level, pos, state);
            if (be != null) be.updatePower();
        }
    }

    // ---------------------------------------------------------------- разрушение

    /** Сломали любую часть — ломается вся дверь; контроллер выпадает предметом (если в выживании и подходящий инструмент). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos ctrl = controllerPos(pos, state);
            BlockState ctrlState = level.getBlockState(ctrl);
            if (ctrlState.is(BlastDoorMod.BLAST_DOOR.get())) {
                boolean drop = !player.isCreative() && player.hasCorrectToolForDrops(ctrlState);
                level.destroyBlock(ctrl, drop, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Часть исчезла не от игрока (команда, мод и т.п.) — разбираем структуру целиком. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (level.getBlockState(pos).is(this)) return;
        BlockPos ctrl = controllerPos(pos, state);
        if (level.getBlockState(ctrl).is(BlastDoorMod.BLAST_DOOR.get())) {
            level.destroyBlock(ctrl, true);
        }
    }
}
