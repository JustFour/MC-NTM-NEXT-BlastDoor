package com.blastdoor.compat;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.BlastDoorBlock;
import com.blastdoor.block.BlastDoorPartBlock;
import com.blastdoor.block.SideDoorBlock;
import com.blastdoor.block.SideDoorPartBlock;
import com.blastdoor.block.StationGateBlock;
import com.blastdoor.block.StationGatePartBlock;
import com.hbm.handler.radiation.RadiationSystemNT;

/**
 * Совместимость с NTM NEXT (mod id {@code hbm}).
 *
 * <p>Класс ссылается на классы NTM, поэтому обращаться к нему можно только когда мод установлен
 * (см. {@link BlastDoorMod}).
 */
public final class NtmCompat {
    public static final String MODID = "hbm";

    private NtmCompat() {
    }

    /**
     * Радиацию не пропускают те блоки двери, которые сейчас твёрдые. Так же ведут себя
     * двери самого NTM: защита привязана к состояниям блока.
     */
    public static void registerRadiationShielding() {
        // Откатная дверь: проём закрыт, пока створка не прошла половину хода.
        RadiationSystemNT.markRadResistant(BlastDoorMod.BLAST_DOOR.get(),
                state -> !state.getValue(BlastDoorBlock.OPEN));
        RadiationSystemNT.markRadResistant(BlastDoorMod.BLAST_DOOR_PART.get(),
                state -> !state.getValue(BlastDoorPartBlock.OPEN));

        // Гермозатвор: блоки проёма — пока он не проходим, блоки пола (и контроллер) — пока плита лежит.
        RadiationSystemNT.markRadResistant(BlastDoorMod.STATION_GATE.get(),
                state -> state.getValue(StationGateBlock.OPEN));
        RadiationSystemNT.markRadResistant(BlastDoorMod.STATION_GATE_PART.get(),
                StationGatePartBlock::isSolid);

        // Распашная дверь: блоки проёма — пока он не проходим, блоки кармана — пока створка стоит в нём.
        RadiationSystemNT.markRadResistant(BlastDoorMod.SIDE_BLAST_DOOR.get(),
                state -> !state.getValue(SideDoorBlock.OPEN));
        RadiationSystemNT.markRadResistant(BlastDoorMod.SIDE_BLAST_DOOR_PART.get(),
                SideDoorPartBlock::isSolid);
    }
}
