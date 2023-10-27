package cn.allay.server.entity.effect.type;

import cn.allay.api.data.VanillaEffectIds;
import cn.allay.server.entity.effect.AbstractEffectType;

import java.awt.*;

/**
 * Allay Project 2023/10/27
 *
 * @author daoge_cmd
 */
public class EffectHasteType extends AbstractEffectType {
    public static final EffectHasteType HASTE_TYPE = new EffectHasteType();
    private EffectHasteType() {
        super(3, VanillaEffectIds.HASTE, new Color(217, 192, 67));
    }
}
