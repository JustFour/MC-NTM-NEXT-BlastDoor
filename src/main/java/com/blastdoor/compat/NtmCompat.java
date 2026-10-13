package com.blastdoor.compat;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.BlastDoorBlock;
import com.blastdoor.block.BlastDoorPartBlock;
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
     * Закрытая дверь не пропускает радиацию, полностью открытая — пропускает. Так же ведут себя
     * двери самого NTM: защита привязана к состояниям блока с {@code open=false}.
     */
    public static void registerRadiationShielding() {
        RadiationSystemNT.markRadResistant(BlastDoorMod.BLAST_DOOR.get(),
                state -> !state.getValue(BlastDoorBlock.OPEN));
        RadiationSystemNT.markRadResistant(BlastDoorMod.BLAST_DOOR_PART.get(),
                state -> !state.getValue(BlastDoorPartBlock.OPEN));
    }
}
