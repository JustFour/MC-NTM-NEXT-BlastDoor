package com.blastdoor.block;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.blockentity.SideDoorBlockEntity;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Контроллер распашной гермодвери 3x3: рельс в нижнем центре проёма, 8 частей проёма вокруг него
 * и 9 частей кармана сбоку, куда створка встаёт в открытом положении. {@code mirrored} — карман справа.
 */
public class SideDoorBlock extends AbstractRailDoorBlock {
    public static final MapCodec<SideDoorBlock> CODEC = simpleCodec(SideDoorBlock::new);

    public SideDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SideDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected List<BlockPos> partPositions(BlockPos controller, Direction facing, boolean mirrored) {
        List<BlockPos> positions = SideDoorGeometry.allPositions(controller, facing, mirrored);
        positions.remove(controller);
        return positions;
    }

    @Override
    protected void placeParts(Level level, BlockPos controller, BlockState controllerState) {
        Direction facing = controllerState.getValue(FACING);
        boolean mirrored = controllerState.getValue(MIRRORED);
        BlockState part = BlastDoorMod.SIDE_BLAST_DOOR_PART.get().defaultBlockState()
                .setValue(SideDoorPartBlock.FACING, facing)
                .setValue(SideDoorPartBlock.MIRRORED, mirrored)
                .setValue(SideDoorPartBlock.OPEN, false);
        for (int a = 0; a < SideDoorGeometry.SIZE; a++) {
            for (int dy = 0; dy < SideDoorGeometry.SIZE; dy++) {
                BlockState cell = part.setValue(SideDoorPartBlock.POS, a).setValue(SideDoorPartBlock.DY, dy);
                if (!DoorGeometry.isController(a, dy)) {
                    level.setBlock(DoorGeometry.partPos(controller, facing, a, dy),
                            cell.setValue(SideDoorPartBlock.POCKET, false), Block.UPDATE_ALL);
                }
                level.setBlock(SideDoorGeometry.pocketPos(controller, facing, mirrored, a, dy),
                        cell.setValue(SideDoorPartBlock.POCKET, true), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected boolean isPartOf(BlockPos partPos, BlockState partState, BlockPos controller) {
        return partState.is(BlastDoorMod.SIDE_BLAST_DOOR_PART.get())
                && SideDoorPartBlock.controllerPos(partPos, partState).equals(controller);
    }

    @Override
    protected Component noSpaceMessage() {
        return Component.translatable("message.blastdoor.side_blast_door.no_space");
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SideDoorBlockEntity(pos, state);
    }
}
