package com.blastdoor.block;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.blockentity.BlastDoorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Контроллер гермодвери. Стоит в нижнем центре проёма и одновременно является прямым рельсом,
 * по которому проезжают вагонетки, когда дверь открыта.
 *
 * <p>Состояния: {@code facing} — ось пути, {@code open} — проём свободен (дверь полностью откатилась),
 * {@code mirrored} — створка откатывается в другую сторону (ставится с Shift).</p>
 */
public class BlastDoorBlock extends BaseRailBlock implements EntityBlock {
    public static final MapCodec<BlastDoorBlock> CODEC = simpleCodec(BlastDoorBlock::new);

    /** Только прямые формы: дверь не может стоять на склоне. Фактическое направление задаёт {@link #FACING}. */
    public static final EnumProperty<RailShape> SHAPE = EnumProperty.create("shape", RailShape.class,
            s -> s == RailShape.NORTH_SOUTH || s == RailShape.EAST_WEST);
    public static final BooleanProperty WATERLOGGED = BaseRailBlock.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    private static final VoxelShape RAIL_FLAT = Block.box(0, 0, 0, 16, 2, 16);

    public BlastDoorBlock(Properties properties) {
        super(true, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(MIRRORED, false));
    }

    @Override
    public MapCodec<BlastDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, WATERLOGGED, FACING, OPEN, MIRRORED);
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

        // Все 8 позиций частей должны быть свободны.
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                if (DoorGeometry.isController(dx, dy)) continue;
                BlockPos p = DoorGeometry.partPos(pos, facing, dx, dy);
                if (!level.isInsideBuildHeight(p.getY()) || !level.getBlockState(p).canBeReplaced(ctx)) {
                    return null;
                }
            }
        }

        Player player = ctx.getPlayer();
        boolean mirrored = player != null && player.isSecondaryUseActive();
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SHAPE, axisShape(facing))
                .setValue(MIRRORED, mirrored);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        Direction facing = state.getValue(FACING);
        BlockState part = BlastDoorMod.BLAST_DOOR_PART.get().defaultBlockState()
                .setValue(BlastDoorPartBlock.FACING, facing)
                .setValue(BlastDoorPartBlock.OPEN, false);
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                if (DoorGeometry.isController(dx, dy)) continue;
                level.setBlock(DoorGeometry.partPos(pos, facing, dx, dy),
                        part.setValue(BlastDoorPartBlock.DX, dx).setValue(BlastDoorPartBlock.DY, dy),
                        Block.UPDATE_ALL);
            }
        }
        if (level.getBlockEntity(pos) instanceof BlastDoorBlockEntity be) {
            be.updatePower();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (level.getBlockState(pos).is(this)) return; // поменялось только состояние

        // Контроллер исчез — убираем все части структуры.
        Direction facing = state.getValue(FACING);
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                if (DoorGeometry.isController(dx, dy)) continue;
                BlockPos p = DoorGeometry.partPos(pos, facing, dx, dy);
                BlockState s = level.getBlockState(p);
                if (s.is(BlastDoorMod.BLAST_DOOR_PART.get()) && BlastDoorPartBlock.controllerPos(p, s).equals(pos)) {
                    level.removeBlock(p, false);
                }
            }
        }
    }

    // ---------------------------------------------------------------- редстоун

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockState(pos).is(this)
                && level.getBlockEntity(pos) instanceof BlastDoorBlockEntity be) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlastDoorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BlastDoorMod.BLAST_DOOR_BE.get()) return null;
        return level.isClientSide()
                ? (l, p, s, be) -> ((BlastDoorBlockEntity) be).clientTick()
                : (l, p, s, be) -> ((BlastDoorBlockEntity) be).serverTick();
    }
}
