package net.xuwu.jei_trade.client;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.xuwu.jei_trade.TradeRecipe;
import net.xuwu.jei_trade.TradeRecipeGroup;

import java.util.List;

final class TradeRecipeCategory extends AbstractRecipeCategory<TradeRecipeGroup> {
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 28;
    private static final int CATEGORY_WIDTH = 180;
    private static final int RANGE_MAX_WIDTH = 16;
    private static final float RANGE_MAX_SCALE = 0.65F;
    private final IDrawableStatic arrow;

    TradeRecipeCategory(IGuiHelper guiHelper, IRecipeType<TradeRecipeGroup> recipeType) {
        super(recipeType,
                Component.translatable("jei_trade.category.villager_trade"),
                guiHelper.createDrawableItemStack(net.minecraft.world.item.Items.EMERALD.getDefaultInstance()),
                CATEGORY_WIDTH, HEADER_HEIGHT + TradeRecipeGroup.ROWS_PER_PAGE * ROW_HEIGHT);
        arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TradeRecipeGroup group, IFocusGroup focuses) {
        int workstationX = 4;
        for (var workstation : group.workstationStacks()) {
            if (workstationX > 44) break;
            builder.addInputSlot(workstationX, 4)
                    .setStandardSlotBackground()
                    .add(workstation);
            workstationX += 20;
        }
        ItemStack spawnEgg = group.sourceSpawnEggStack();
        if (!spawnEgg.isEmpty()) {
            builder.addInputSlot(workstationX, 4)
                    .setStandardSlotBackground()
                    .add(spawnEgg);
        }

        for (int index = 0; index < group.trades().size(); index++) {
            TradeRecipe trade = group.trades().get(index);
            int y = HEADER_HEIGHT + index * ROW_HEIGHT;
            IRecipeSlotBuilder buyA = builder.addInputSlot(4, y).setStandardSlotBackground();
            buyA.addItemStacks(trade.buyAVariantsForDisplay());
            addSlotTooltip(buyA, trade, trade.buyAVariants(), trade.buyACountRange(), false);
            if (!trade.buyB().isEmpty()) {
                IRecipeSlotBuilder buyB = builder.addInputSlot(27, y).setStandardSlotBackground();
                buyB.addItemStacks(trade.buyBVariantsForDisplay());
                addSlotTooltip(buyB, trade, trade.buyBVariants(), trade.buyBCountRange(), false);
            }
            IRecipeSlotBuilder result = builder.addOutputSlot(91, y).setStandardSlotBackground();
            result.addItemStacks(trade.resultVariantsForDisplay());
            addSlotTooltip(result, trade, trade.resultVariants(), trade.resultCountRange(), true);
        }
    }

    private static void addSlotTooltip(IRecipeSlotBuilder slot, TradeRecipe trade,
                                       List<ItemStack> variants, String countRange,
                                       boolean includeDetails) {
        if (!countRange.isEmpty()) {
            slot.setOverlay(new IDrawable() {
                @Override public int getWidth() { return 18; }
                @Override public int getHeight() { return 18; }
                @Override public void draw(GuiGraphicsExtractor graphics, int x, int y) {
                    graphics.nextStratum();
                    drawRange(graphics, Minecraft.getInstance().font, countRange, x, y);
                }
            }, 0, 0);
        }
        slot.addRichTooltipCallback((view, tooltip) -> {
            int nbtVariants = trade.nbtVariantCount(variants);
            if (nbtVariants > 1) {
                boolean sameItem = variants.stream().allMatch(stack -> stack.is(variants.get(0).getItem()));
                tooltip.add(Component.translatable(sameItem ? "jei_trade.nbt_variants" : "jei_trade.variants", nbtVariants)
                        .withStyle(ChatFormatting.GRAY));
            }
            if (!countRange.isEmpty()) {
                tooltip.add(Component.translatable("jei_trade.count_range", countRange)
                        .withStyle(ChatFormatting.GRAY));
            }
            if (includeDetails) trade.details().forEach(tooltip::add);
        });
    }

    @Override
    public void draw(TradeRecipeGroup group, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int workstationCount = Math.min(group.workstationCount(), 3);
        int headerSlotCount = workstationCount + (group.hasSourceSpawnEgg() ? 1 : 0);
        int titleX = headerSlotCount == 0 ? 4 : 4 + headerSlotCount * 20 + 3;
        boolean wanderingTrader = group.entityType() != null
                && "minecraft".equals(group.entityType().getNamespace())
                && "wandering_trader".equals(group.entityType().getPath());
        String source = wanderingTrader
                ? Component.translatable("jei_trade.source.wandering_trader").getString()
                : group.profession() == null
                ? merchantName(group.entityType()).getString()
                : Component.translatable("jei_trade.source.profession", professionName(group.profession())).getString();
        source = minecraft.font.plainSubstrByWidth(source, CATEGORY_WIDTH - titleX - 4);
        graphics.text(minecraft.font, source, titleX, 7, 0xFF404040, false);
        if (group.pageCount() > 1) {
            String page = Component.translatable("jei_trade.page", group.page(), group.pageCount()).getString();
            graphics.text(minecraft.font, page, CATEGORY_WIDTH - minecraft.font.width(page) - 4,
                    18, 0xFF606060, false);
        }

        for (int index = 0; index < group.trades().size(); index++) {
            TradeRecipe trade = group.trades().get(index);
            int y = HEADER_HEIGHT + index * ROW_HEIGHT;
            arrow.draw(graphics, 57, y);
            String level = Component.translatable("jei_trade.level", trade.level()).getString();
            graphics.text(minecraft.font, level, 120, y + 2, 0xFF404040, false);
            graphics.text(minecraft.font,
                    Component.translatable("jei_trade.max_uses", trade.maxUses()), 120, y + 12,
                    0xFF606060, false);
        }
    }

    private static void drawRange(GuiGraphicsExtractor graphics, Font font, String range, int x, int y) {
        if (!range.isEmpty()) {
            int width = font.width(range);
            float scale = Math.min(RANGE_MAX_SCALE, RANGE_MAX_WIDTH / (float) Math.max(1, width));
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 18.0F, y + 18.0F);
            graphics.pose().scale(scale, scale);
            graphics.text(font, range, -width, -font.lineHeight, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();
        }
    }

    private static Component professionName(Identifier profession) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            var value = minecraft.level.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION)
                    .getValue(profession);
            if (value != null) return value.name();
        }
        for (String key : professionTranslationKeys(profession)) {
            if (ClientLanguage.getInstance().has(key)) return Component.translatable(key);
        }

        return Component.literal(profession.toString());
    }

    private static List<String> professionTranslationKeys(Identifier profession) {
        String namespace = profession.getNamespace();
        String path = profession.getPath();
        return List.of(
                "entity." + namespace + ".villager." + path,
                "entity." + namespace + "." + path,
                "villager.profession." + namespace + "." + path,
                "villager.profession." + path,
                "profession." + namespace + "." + path,
                "profession." + path,
                "entity.minecraft.villager." + path
        );
    }

    private static Component merchantName(Identifier entityType) {
        if (entityType != null) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(entityType);
            if (type != null && ClientLanguage.getInstance().has(type.getDescriptionId())) {
                return Component.translatable(type.getDescriptionId());
            }
        }
        return Component.translatable("jei_trade.source.merchant");
    }

}
