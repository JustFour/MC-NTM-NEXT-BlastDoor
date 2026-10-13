package com.blastdoor.block;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.blockentity.StationGateBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Контроллер станционного гермозатвора. Это блок пола в ближнем к игроку ряду углубления
 * (второй справа): на нём висит блок-сущность с анимацией и логикой. Сам ничего не рисует.
 *
 * <p>Состояния: {@code facing} — направление сквозь проём, {@code open} — плита легла до конца
 * и стала полом (у блоков проёма то же свойство значит другое, см. {@link StationGatePartBlock}).</p>
 */
public class StationGateBlock extends Block implements EntityBlock {
    public static final MapCodec<StationGateBlock> CODEC = simpleCodec(StationGateBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public StationGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    public MapCodec<StationGateBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    // ---------------------------------------------------------------- установка / разрушение

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();

        // Углубление 4x4 в полу и проём 4x4 над его ближним рядом должны быть свободны.
        for (BlockPos p : StationGateGeometry.allPositions(pos, facing)) {
            if (p.equals(pos)) continue;
            if (!level.isInsideBuildHeight(p.getY()) || !level.getBlockState(p).canBeReplaced(ctx)) {
                Player player = ctx.getPlayer();
                if (player != null && !level.isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.blastdoor.station_gate.no_space"));
                }
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        Direction facing = state.getValue(FACING);
        BlockState part = BlastDoorMod.STATION_GATE_PART.get().defaultBlockState()
                .setValue(StationGatePartBlock.FACING, facing)
                .setValue(StationGatePartBlock.OPEN, false);
        for (int dx = 0; dx < StationGateGeometry.SIZE; dx++) {
            for (int n = 0; n < StationGateGeometry.SIZE; n++) {
                BlockState cell = part.setValue(StationGatePartBlock.DX, dx).setValue(StationGatePartBlock.ROW, n);
                if (!StationGateGeometry.isController(dx, n)) {
                    level.setBlock(StationGateGeometry.floorPos(pos, facing, dx, n),
                            cell.setValue(StationGatePartBlock.FLOOR, true), Block.UPDATE_ALL);
                }
                level.setBlock(StationGateGeometry.planePos(pos, facing, dx, n),
                        cell.setValue(StationGatePartBlock.FLOOR, false), Block.UPDATE_ALL);
            }
        }
        if (level.getBlockEntity(pos) instanceof StationGateBlockEntity be) {
            be.updatePower();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (level.getBlockState(pos).is(this)) return; // поменялось только состояние

        // Контроллер исчез — убираем все части структуры.
        for (BlockPos p : StationGateGeometry.allPositions(pos, state.getValue(FACING))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlastDoorMod.STATION_GATE_PART.get()) && StationGatePartBlock.controllerPos(p, s).equals(pos)) {
                level.removeBlock(p, false);
            }
        }
    }

    // ---------------------------------------------------------------- редстоун

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockState(pos).is(this)
                && level.getBlockEntity(pos) instanceof StationGateBlockEntity be) {
            be.updatePower();
        }
    }

    // ---------------------------------------------------------------- вид и формы

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Контроллер — блок пола: целый блок, пока плита лежит, и дно приямка, когда она поднята. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.block() : StationGateGeometry.PIT_FLOOR;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.block() : StationGateGeometry.PIT_FLOOR;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---------------------------------------------------------------- блок-сущность

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationGateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BlastDoorMod.STATION_GATE_BE.get()) return null;
        return level.isClientSide()
                ? (l, p, s, be) -> ((StationGateBlockEntity) be).clientTick()
                : (l, p, s, be) -> ((StationGateBlockEntity) be).serverTick();
    }
}
