package cn.allay.server.item.enchantment.type;

import cn.allay.api.data.VanillaEnchantmentIds;
import cn.allay.api.item.enchantment.Rarity;
import cn.allay.server.item.enchantment.AbstractEnchantmentType;

/**
 * @author daoge_cmd <br>
 * Allay Project <br>
 */
public class EnchantmentThornsType extends AbstractEnchantmentType {
    public static final EnchantmentThornsType THORNS_TYPE = new EnchantmentThornsType();
  private EnchantmentThornsType() {
    super(VanillaEnchantmentIds.THORNS, 5, 3, Rarity.VERY_RARE);
  }
}
