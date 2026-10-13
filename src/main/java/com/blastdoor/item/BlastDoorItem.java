package com.blastdoor.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Предмет гермодвери: показывает в подсказке взрывостойкость блока, как блоки NTM. */
public class BlastDoorItem extends BlockItem {
    public BlastDoorItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        // Число берётся из самого блока, поэтому подсказка не разойдётся с настройкой в BlastDoorMod.
        builder.accept(Component.translatable("tooltip.blastdoor.blast_resistance", getBlock().getExplosionResistance())
                .withStyle(ChatFormatting.GOLD));
    }
}
