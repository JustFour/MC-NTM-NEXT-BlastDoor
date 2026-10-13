package com.blastdoor.client;

import com.blastdoor.BlastDoorMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиентская часть: регистрация рендерера створки. На выделенном сервере не загружается. */
@Mod(value = BlastDoorMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BlastDoorMod.MODID, value = Dist.CLIENT)
public class BlastDoorClient {
    public BlastDoorClient() {}

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlastDoorMod.BLAST_DOOR_BE.get(), BlastDoorRenderer::new);
    }
}
