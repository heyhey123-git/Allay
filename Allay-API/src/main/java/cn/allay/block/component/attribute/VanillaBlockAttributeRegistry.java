package cn.allay.block.component.attribute;

import cn.allay.api.ApiInstanceHolder;
import cn.allay.block.data.VanillaBlockId;
import cn.allay.registry.MappedRegistry;

import java.util.Map;

/**
 * Author: daoge_cmd <br>
 * Date: 2023/4/8 <br>
 * Allay Project <br>
 */
public interface VanillaBlockAttributeRegistry extends MappedRegistry<VanillaBlockId, BlockAttributeComponentImpl, Map<VanillaBlockId, BlockAttributeComponentImpl>> {
    ApiInstanceHolder<VanillaBlockAttributeRegistry> REGISTRY = ApiInstanceHolder.create();

    static VanillaBlockAttributeRegistry getRegistry() {
        return REGISTRY.get();
    }
}
