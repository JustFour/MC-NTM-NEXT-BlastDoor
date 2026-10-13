package com.blastdoor.client;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.blockentity.AbstractDoorBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиентская часть: рендереры створок и звук их работы. На выделенном сервере не загружается. */
@Mod(value = BlastDoorMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BlastDoorMod.MODID, value = Dist.CLIENT)
public class BlastDoorClient {
    public BlastDoorClient() {
        AbstractDoorBlockEntity.motionSoundFactory = DoorRumbleSound::start;
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlastDoorMod.BLAST_DOOR_BE.get(), BlastDoorRenderer::new);
        event.registerBlockEntityRenderer(BlastDoorMod.STATION_GATE_BE.get(), StationGateRenderer::new);
        event.registerBlockEntityRenderer(BlastDoorMod.SIDE_BLAST_DOOR_BE.get(), SideDoorRenderer::new);
    }
}
