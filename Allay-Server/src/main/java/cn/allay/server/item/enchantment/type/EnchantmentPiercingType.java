package cn.allay.server.item.enchantment.type;

import cn.allay.api.data.VanillaEnchantmentIds;
import cn.allay.api.item.enchantment.Rarity;
import cn.allay.server.item.enchantment.AbstractEnchantmentType;

/**
 * @author daoge_cmd <br>
 * Allay Project <br>
 */
public class EnchantmentPiercingType extends AbstractEnchantmentType {
    public static final EnchantmentPiercingType PIERCING_TYPE = new EnchantmentPiercingType();
  private EnchantmentPiercingType() {
    super(VanillaEnchantmentIds.PIERCING, 34, 4, Rarity.COMMON);
  }
}
