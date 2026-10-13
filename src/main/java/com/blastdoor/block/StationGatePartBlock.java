package com.blastdoor.block;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.blockentity.StationGateBlockEntity;
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
 * Невидимая часть станционного гермозатвора. Сама ничего не рисует (плиту рисует рендерер контроллера),
 * но даёт коллизию и принимает сигнал редстоуна.
 *
 * <p>Частей два вида, и {@code open} у них значит разное. У блоков пола ({@code floor=true}) это
 * «плита легла до конца»: тогда они твёрдые, иначе на их месте приямок. У блоков проёма это
 * «проём проходим»: коллизия пропадает, когда плита прошла половину хода.
 * {@code row} — ряд вглубь для пола и высота для проёма.</p>
 */
public class StationGatePartBlock extends Block {
    public static final MapCodec<StationGatePartBlock> CODEC = simpleCodec(StationGatePartBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty DX = IntegerProperty.create("dx", 0, StationGateGeometry.SIZE - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, StationGateGeometry.SIZE - 1);
    public static final BooleanProperty FLOOR = BooleanProperty.create("floor");
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public StationGatePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(DX, 0)
                .setValue(ROW, 0)
                .setValue(FLOOR, false)
                .setValue(OPEN, false));
    }

    @Override
    public MapCodec<StationGatePartBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DX, ROW, FLOOR, OPEN);
    }

    public static BlockPos controllerPos(BlockPos partPos, BlockState partState) {
        Direction facing = partState.getValue(FACING);
        int dx = partState.getValue(DX);
        int row = partState.getValue(ROW);
        return partState.getValue(FLOOR)
                ? StationGateGeometry.controllerFromFloor(partPos, facing, dx, row)
                : StationGateGeometry.controllerFromPlane(partPos, facing, dx, row);
    }

    private static @Nullable StationGateBlockEntity controller(Level level, BlockPos partPos, BlockState partState) {
        return level.getBlockEntity(controllerPos(partPos, partState)) instanceof StationGateBlockEntity be ? be : null;
    }

    /** Блок сейчас твёрдый: пол под лежащей плитой или проём, пока он не стал проходимым. */
    public static boolean isSolid(BlockState state) {
        return state.getValue(FLOOR) == state.getValue(OPEN);
    }

    // ---------------------------------------------------------------- вид и формы

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    private static VoxelShape shape(BlockState state) {
        if (isSolid(state)) return Shapes.block();
        return state.getValue(FLOOR) ? StationGateGeometry.PIT_FLOOR : Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return !state.getValue(FLOOR) && state.getValue(OPEN);
    }

    // ---------------------------------------------------------------- редстоун

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide()) {
            StationGateBlockEntity be = controller(level, pos, state);
            if (be != null) be.updatePower();
        }
    }

    // ---------------------------------------------------------------- разрушение

    /** Сломали любую часть — ломается весь затвор; контроллер выпадает предметом (если в выживании и подходящий инструмент). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos ctrl = controllerPos(pos, state);
            BlockState ctrlState = level.getBlockState(ctrl);
            if (ctrlState.is(BlastDoorMod.STATION_GATE.get())) {
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
        if (level.getBlockState(ctrl).is(BlastDoorMod.STATION_GATE.get())) {
            level.destroyBlock(ctrl, true);
        }
    }
}
