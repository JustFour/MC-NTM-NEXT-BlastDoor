package com.blastdoor.block;

import com.blastdoor.blockentity.AbstractDoorBlockEntity;
import java.util.List;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import traincraft.track.block.TrackBlock;

/**
 * Контроллер гермодвери на рельсах в сборке под Traincraft. Занимает нижний центр проёма 3x3
 * и является настоящим блоком пути Traincraft: его поезда едут по нему как по короткому прямому
 * отрезку, а сам путь в проёме рисует рендерер Traincraft.
 *
 * <p>Ставится в разрыв между двумя кусками пути Traincraft, вплотную к ним.</p>
 *
 * <p>Состояния: от Traincraft — {@code facing} (ось пути, куда смотрел игрок при установке)
 * и {@code shape} (всегда прямой); свои — {@code open} (проём без коллизии, створка прошла половину
 * хода) и {@code mirrored} (дверь открывается в другую сторону, ставится с Shift).</p>
 *
 * <p>От блока пути унаследованы только распознавание поездами и невидимая блочная модель.
 * Установку, разрушение, проверку опоры и коллизию он делает как дверь, а не как кусок пути:
 * иначе Traincraft «разобрал» бы контроллер по своим правилам и оставил дверь без него.</p>
 */
public abstract class AbstractRailDoorBlock extends TrackBlock {
    public static final EnumProperty<Direction> FACING = TrackBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    private static final VoxelShape RAIL_FLAT = Block.box(0, 0, 0, 16, 2, 16);

    protected AbstractRailDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(OPEN, false)
                .setValue(MIRRORED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN, MIRRORED);
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

    /**
     * То же, что делает обычный блок. Путь Traincraft в этом месте разбирает весь свой кусок
     * и роняет его предмет — контроллеру двери это не нужно.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        spawnDestroyParticles(level, player, pos, state);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        return state;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockState(pos).is(this)) return; // поменялось только состояние

        // Контроллер исчез — убираем все части структуры.
        for (BlockPos p : partPositions(pos, state.getValue(FACING), state.getValue(MIRRORED))) {
            if (isPartOf(p, level.getBlockState(p), pos)) {
                level.removeBlock(p, false);
            }
        }
    }

    // ---------------------------------------------------------------- редстоун

    /** Без проверки опоры, которую делает путь Traincraft: дверь не падает, если под ней убрали блок. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
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

    /** Закрытая дверь — стена для всех, включая поезда; у открытой коллизии нет. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : DoorGeometry.panelShape();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(OPEN);
    }

    // ---------------------------------------------------------------- блок-сущность

    /** Тикает дверь, а не стрелочная логика пути Traincraft: прямому отрезку она не нужна. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? (l, p, s, be) -> { if (be instanceof AbstractDoorBlockEntity door) door.clientTick(); }
                : (l, p, s, be) -> { if (be instanceof AbstractDoorBlockEntity door) door.serverTick(); };
    }
}
