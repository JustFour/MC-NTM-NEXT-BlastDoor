package com.blastdoor;

import com.blastdoor.block.BlastDoorBlock;
import com.blastdoor.block.BlastDoorPartBlock;
import com.blastdoor.block.SideDoorBlock;
import com.blastdoor.block.SideDoorPartBlock;
import com.blastdoor.block.StationGateBlock;
import com.blastdoor.block.StationGatePartBlock;
import com.blastdoor.blockentity.BlastDoorBlockEntity;
import com.blastdoor.blockentity.SideDoorBlockEntity;
import com.blastdoor.blockentity.StationGateBlockEntity;
import com.blastdoor.compat.NtmCompat;
import com.blastdoor.item.BlastDoorItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Гермодвери для метро и бункеров. У каждой двери один блок-контроллер с блок-сущностью
 * (анимация и логика) и невидимые блоки-части, которые дают коллизию и передают сигнал редстоуна.
 *
 * <ul>
 *   <li>{@code blastdoor:blast_door} + {@code blast_door_part} — откатная гермодверь 3x3 с рельсом
 *       в проёме; контроллер — сам рельс в нижнем центре.</li>
 *   <li>{@code blastdoor:station_gate} + {@code station_gate_part} — станционный гермозатвор 4x4:
 *       плита, которая в открытом виде лежит в полу, а в закрытом стоит в проёме.</li>
 *   <li>{@code blastdoor:side_blast_door} + {@code side_blast_door_part} — распашная гермодверь 3x3
 *       с рельсом в проёме: створка на петлях уходит в карман сбоку от пути.</li>
 * </ul>
 */
@Mod(BlastDoorMod.MODID)
public class BlastDoorMod {
    public static final String MODID = "blastdoor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);

    /** Контроллер: рельс + логика + рендер створки. Вдвое твёрже обсидиана, взрывостойкость как у дюкретовых кирпичей NTM. */
    public static final DeferredBlock<BlastDoorBlock> BLAST_DOOR = BLOCKS.registerBlock("blast_door",
            BlastDoorBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED)
                    .strength(100.0F, 450.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .isViewBlocking((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false));

    /** Невидимая часть структуры. Без лута: при разрушении выпадает только контроллер. */
    public static final DeferredBlock<BlastDoorPartBlock> BLAST_DOOR_PART = BLOCKS.registerBlock("blast_door_part",
            BlastDoorPartBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED)
                    .strength(100.0F, 450.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .noLootTable()
                    .isViewBlocking((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false));

    public static final DeferredItem<BlastDoorItem> BLAST_DOOR_ITEM = ITEMS.registerItem("blast_door",
            p -> new BlastDoorItem(BLAST_DOOR.get(), p),
            p -> p.useBlockDescriptionPrefix());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlastDoorBlockEntity>> BLAST_DOOR_BE =
            BLOCK_ENTITY_TYPES.register("blast_door",
                    () -> new BlockEntityType<>(BlastDoorBlockEntity::new, BLAST_DOOR.get()));

    /** Контроллер станционного гермозатвора 4x4: блок пола с логикой и рендером плиты. Характеристики как у гермодвери. */
    public static final DeferredBlock<StationGateBlock> STATION_GATE = BLOCKS.registerBlock("station_gate",
            StationGateBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY)
                    .strength(100.0F, 450.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .isViewBlocking((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false));

    /** Невидимая часть гермозатвора (пол или проём). Без лута: при разрушении выпадает только контроллер. */
    public static final DeferredBlock<StationGatePartBlock> STATION_GATE_PART = BLOCKS.registerBlock("station_gate_part",
            StationGatePartBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY)
                    .strength(100.0F, 450.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .noLootTable()
                    .isViewBlocking((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false));

    public static final DeferredItem<BlastDoorItem> STATION_GATE_ITEM = ITEMS.registerItem("station_gate",
            p -> new BlastDoorItem(STATION_GATE.get(), p),
            p -> p.useBlockDescriptionPrefix());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationGateBlockEntity>> STATION_GATE_BE =
            BLOCK_ENTITY_TYPES.register("station_gate",
                    () -> new BlockEntityType<>(StationGateBlockEntity::new, STATION_GATE.get()));

    /** Контроллер распашной гермодвери 3x3: рельс + логика + рендер створки. Характеристики как у остальных дверей. */
    public static final DeferredBlock<SideDoorBlock> SIDE_BLAST_DOOR = BLOCKS.registerBlock("side_blast_door",
            SideDoorBlock::new,
            p -> p.mapColor(MapColor.COLOR_BROWN)
                    .strength(100.0F, 450.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .isViewBlocking((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false));

    /** Невидимая часть распашной двери (проём или карман). Без лута: при разрушении выпадает только контроллер. */
    public static final DeferredBlock<SideDoorPartBlock> SIDE_BLAST_DOOR_PART = BLOCKS.registerBlock("side_blast_door_part",
            SideDoorPartBlock::new,
            p -> p.mapColor(MapColor.COLOR_BROWN)
                    .strength(100.0F, 450.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .noLootTable()
                    .isViewBlocking((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false));

    public static final DeferredItem<BlastDoorItem> SIDE_BLAST_DOOR_ITEM = ITEMS.registerItem("side_blast_door",
            p -> new BlastDoorItem(SIDE_BLAST_DOOR.get(), p),
            p -> p.useBlockDescriptionPrefix());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SideDoorBlockEntity>> SIDE_BLAST_DOOR_BE =
            BLOCK_ENTITY_TYPES.register("side_blast_door",
                    () -> new BlockEntityType<>(SideDoorBlockEntity::new, SIDE_BLAST_DOOR.get()));

    public BlastDoorMod(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(NtmCompat.MODID)) {
            return;
        }
        // Реестр защиты у NTM не потокобезопасен, а событие приходит из рабочих потоков.
        event.enqueueWork(() -> {
            try {
                NtmCompat.registerRadiationShielding();
                LOGGER.info("NTM NEXT найден: закрытые гермодвери защищают от радиации");
            } catch (LinkageError e) {
                LOGGER.warn("NTM NEXT найден, но его API радиации изменился: защита от радиации отключена", e);
            }
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(BLAST_DOOR_ITEM);
            event.accept(SIDE_BLAST_DOOR_ITEM);
            event.accept(STATION_GATE_ITEM);
        }
    }
}
