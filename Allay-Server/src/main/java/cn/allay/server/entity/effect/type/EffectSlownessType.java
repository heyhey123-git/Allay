package cn.allay.server.entity.effect.type;

import cn.allay.api.data.VanillaEffectIds;
import cn.allay.server.entity.effect.AbstractEffectType;

import java.awt.*;

/**
 * Allay Project 2023/10/27
 *
 * @author daoge_cmd
 */
public class EffectSlownessType extends AbstractEffectType {
    public static final EffectSlownessType SLOWNESS_TYPE = new EffectSlownessType();
    private EffectSlownessType() {
        super(2, VanillaEffectIds.SLOWNESS, new Color(90, 108, 129), true);
    }
}
