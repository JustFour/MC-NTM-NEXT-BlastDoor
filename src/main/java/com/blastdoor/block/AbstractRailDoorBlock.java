package com.blastdoor.block;

import com.blastdoor.blockentity.AbstractDoorBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Контроллер гермодвери, стоящей на рельсах. Занимает нижний центр проёма 3x3 и одновременно является
 * прямым рельсом, по которому проезжают вагонетки, когда дверь открыта.
 *
 * <p>Состояния: {@code facing} — ось пути (куда смотрел игрок при установке), {@code open} — проём без
 * коллизии (створка прошла половину хода), {@code mirrored} — дверь открывается в другую сторону
 * (ставится с Shift).</p>
 *
 * <p>Конкретная дверь задаёт только состав структуры: где стоят её части и как их ставить.</p>
 */
public abstract class AbstractRailDoorBlock extends BaseRailBlock implements EntityBlock {
    /** Только прямые формы: дверь не может стоять на склоне. Фактическое направление задаёт {@link #FACING}. */
    public static final EnumProperty<RailShape> SHAPE = EnumProperty.create("shape", RailShape.class,
            s -> s == RailShape.NORTH_SOUTH || s == RailShape.EAST_WEST);
    public static final BooleanProperty WATERLOGGED = BaseRailBlock.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    private static final VoxelShape RAIL_FLAT = Block.box(0, 0, 0, 16, 2, 16);

    protected AbstractRailDoorBlock(Properties properties) {
        super(true, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(MIRRORED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, WATERLOGGED, FACING, OPEN, MIRRORED);
    }

    // ---------------------------------------------------------------- что задаёт конкретная дверь

    /** Позиции всех частей структуры, кроме самого контроллера. */
    protected abstract List<BlockPos> partPositions(BlockPos controller, Direction facing, boolean mirrored);

    /** Поставить части структуры; контроллер к этому моменту уже стоит. */
    protected abstract void placeParts(Level level, BlockPos controller, BlockState controllerState);

    /** Блок на этой позиции — часть двери с данным контроллером. */
    protected abstract boolean isPartOf(BlockPos partPos, BlockState partState, BlockPos controller);

    /** Подсказка игроку, если для двери не хватает места; null — без подсказки. */
    protected @Nullable Component noSpaceMessage() {
        return null;
    }

    // ---------------------------------------------------------------- рельс

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    /** Направление рельса жёстко задано осью двери — соседние рельсы не могут его повернуть. */
    @Override
    public RailShape getRailDirection(BlockState state, BlockGetter level, BlockPos pos, @Nullable AbstractMinecart cart) {
        return axisShape(state.getValue(FACING));
    }

    @Override
    public boolean isFlexibleRail(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canMakeSlopes(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean isValidRailShape(RailShape shape) {
        return shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST;
    }

    private static RailShape axisShape(Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
    }

    // ---------------------------------------------------------------- установка / разрушение

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        boolean mirrored = player != null && player.isSecondaryUseActive();

        // Все позиции частей должны быть свободны.
        for (BlockPos p : partPositions(pos, facing, mirrored)) {
            if (!level.isInsideBuildHeight(p.getY()) || !level.getBlockState(p).canBeReplaced(ctx)) {
                Component hint = noSpaceMessage();
                if (hint != null && player != null && !level.isClientSide()) {
                    player.sendOverlayMessage(hint);
                }
                return null;
            }
        }

        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SHAPE, axisShape(facing))
                .setValue(MIRRORED, mirrored);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        placeParts(level, pos, state);
        if (level.getBlockEntity(pos) instanceof AbstractDoorBlockEntity be) {
            be.updatePower();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (level.getBlockState(pos).is(this)) return; // поменялось только состояние

        // Контроллер исчез — убираем все части структуры.
        for (BlockPos p : partPositions(pos, state.getValue(FACING), state.getValue(MIRRORED))) {
            if (isPartOf(p, level.getBlockState(p), pos)) {
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
                && level.getBlockEntity(pos) instanceof AbstractDoorBlockEntity be) {
            be.updatePower();
        }
    }

    // ---------------------------------------------------------------- формы

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? RAIL_FLAT : DoorGeometry.panelShape();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : DoorGeometry.panelShape();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(OPEN);
    }

    // ---------------------------------------------------------------- блок-сущность

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? (l, p, s, be) -> { if (be instanceof AbstractDoorBlockEntity door) door.clientTick(); }
                : (l, p, s, be) -> { if (be instanceof AbstractDoorBlockEntity door) door.serverTick(); };
    }
}
