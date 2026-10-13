package com.blastdoor.block;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.blockentity.AbstractDoorBlockEntity;
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
 * Невидимая часть распашной гермодвери. Сама ничего не рисует (створку рисует рендерер контроллера),
 * но даёт коллизию и принимает сигнал редстоуна.
 *
 * <p>Частей два вида, и {@code open} у них значит разное. У блоков проёма это «проём проходим»:
 * коллизия пропадает, когда створка прошла половину хода. У блоков кармана ({@code pocket=true}) это
 * «створка встала в карман до конца»: тогда они твёрдые, иначе пустые.
 * {@code pos} — место поперёк пути для проёма и ряд вглубь для кармана.</p>
 */
public class SideDoorPartBlock extends Block {
    public static final MapCodec<SideDoorPartBlock> CODEC = simpleCodec(SideDoorPartBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty MIRRORED = AbstractRailDoorBlock.MIRRORED;
    public static final BooleanProperty POCKET = BooleanProperty.create("pocket");
    public static final IntegerProperty POS = IntegerProperty.create("pos", 0, SideDoorGeometry.SIZE - 1);
    public static final IntegerProperty DY = IntegerProperty.create("dy", 0, SideDoorGeometry.SIZE - 1);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public SideDoorPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MIRRORED, false)
                .setValue(POCKET, false)
                .setValue(POS, 0)
                .setValue(DY, 1)
                .setValue(OPEN, false));
    }

    @Override
    public MapCodec<SideDoorPartBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MIRRORED, POCKET, POS, DY, OPEN);
    }

    public static BlockPos controllerPos(BlockPos partPos, BlockState partState) {
        Direction facing = partState.getValue(FACING);
        int pos = partState.getValue(POS);
        int dy = partState.getValue(DY);
        return partState.getValue(POCKET)
                ? SideDoorGeometry.controllerFromPocket(partPos, facing, partState.getValue(MIRRORED), pos, dy)
                : DoorGeometry.controllerFromPart(partPos, facing, pos, dy);
    }

    /** Блок сейчас твёрдый: карман со вставшей в него створкой или проём, пока он не стал проходимым. */
    public static boolean isSolid(BlockState state) {
        return state.getValue(POCKET) == state.getValue(OPEN);
    }

    // ---------------------------------------------------------------- вид и формы

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isSolid(state) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isSolid(state) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return !isSolid(state);
    }

    // ---------------------------------------------------------------- редстоун

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide()
                && level.getBlockEntity(controllerPos(pos, state)) instanceof AbstractDoorBlockEntity be) {
            be.updatePower();
        }
    }

    // ---------------------------------------------------------------- разрушение

    /** Сломали любую часть — ломается вся дверь; контроллер выпадает предметом (если в выживании и подходящий инструмент). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos ctrl = controllerPos(pos, state);
            BlockState ctrlState = level.getBlockState(ctrl);
            if (ctrlState.is(BlastDoorMod.SIDE_BLAST_DOOR.get())) {
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
        if (level.getBlockState(ctrl).is(BlastDoorMod.SIDE_BLAST_DOOR.get())) {
            level.destroyBlock(ctrl, true);
        }
    }
}
