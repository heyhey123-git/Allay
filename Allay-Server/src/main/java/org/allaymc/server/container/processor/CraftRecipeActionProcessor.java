package org.allaymc.server.container.processor;

import lombok.extern.slf4j.Slf4j;
import org.allaymc.api.container.FullContainerType;
import org.allaymc.api.container.impl.CraftingContainer;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.item.interfaces.ItemAirStack;
import org.allaymc.api.registry.Registries;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ConsumeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftRecipeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.allaymc.api.container.FullContainerType.CREATED_OUTPUT;

/**
 * Allay Project 2023/12/1
 *
 * @author daoge_cmd
 */
@Slf4j
public class CraftRecipeActionProcessor implements ContainerActionProcessor<CraftRecipeAction> {
    public static final String RECIPE_DATA_KEY = "recipe";

    @Override
    public ActionResponse handle(CraftRecipeAction action, EntityPlayer player, int currentActionIndex, ItemStackRequestAction[] actions, Map<Object, Object> dataPool) {
        CraftingContainer craftingContainer = player.getOpenedContainer(FullContainerType.CRAFTING_TABLE);
        if (craftingContainer == null) {
            // The player is not opening a crafting table, using crafting grid instead
            craftingContainer = player.getContainer(FullContainerType.CRAFTING_GRID);
        }
        var numberOfRequestedCrafts = action.getNumberOfRequestedCrafts();

        // Validate item type
        var recipe = Registries.RECIPES.get(action.getRecipeNetworkId());
        var input = craftingContainer.createCraftingInput();
        var matched = recipe.match(input);
        if (!matched) {
            log.warn("Mismatched recipe! Network id: {}", recipe.getNetworkId());
            return error();
        }

        // Validate if the player has provided enough ingredients
        var itemStackArray = craftingContainer.getItemStackArray();
        for (int slot = 0; slot < itemStackArray.length; slot++) {
            var ingredient = itemStackArray[slot];
            // Skip empty slot because we have checked item type above
            if (ingredient == ItemAirStack.AIR_STACK) continue;
            if (ingredient.getCount() < numberOfRequestedCrafts) {
                log.warn("Not enough ingredients in slot {}! Expected: {}, Actual: {}", slot, numberOfRequestedCrafts, ingredient.getCount());
                return error();
            }
        }

        // Validate the consume action count which client sent
        // Some checks are also placed in ConsumeActionProcessor (e.g., item consumption count check)
        var consumeActions = findAllConsumeActions(actions, currentActionIndex + 1);
        var consumeActionCountNeeded = craftingContainer.calculateShouldConsumedItemSlotCount();
        if (consumeActions.size() != consumeActionCountNeeded) {
            log.warn("Mismatched consume action count! Expected: {}, Actual: {}", consumeActionCountNeeded, consumeActions.size());
            return error();
        }

        if (recipe.getOutputs().length == 1) {
            // If the recipe outputs a single item, the client will not send a CreateAction, so we directly set the output in CREATED_OUTPUT in CraftRecipeAction
            var output = recipe.getOutputs()[0].copy(false);
            output.setCount(output.getCount() * numberOfRequestedCrafts);
            player.getContainer(CREATED_OUTPUT).setItemStack(0, output);
        } else {
            if (numberOfRequestedCrafts != 1) {
                log.warn("Number of requested crafts for multi-outputs recipe should be one! Actual: {}", numberOfRequestedCrafts);
                return error();
            }
            // If the recipe outputs multiple items, the client will send a CreateAction, so we will set the output in CREATED_OUTPUT in CreateActionProcessor
            // Put recipe to data pool so that CreateActionProcessor can get it
            dataPool.put(RECIPE_DATA_KEY, recipe);
        }

        return null;
    }

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_RECIPE;
    }

    protected List<ConsumeAction> findAllConsumeActions(ItemStackRequestAction[] actions, int startIndex) {
        return Arrays.stream(actions, startIndex, actions.length)
                .filter(action -> action instanceof ConsumeAction)
                .map(action -> (ConsumeAction) action)
                .toList();
    }
}
