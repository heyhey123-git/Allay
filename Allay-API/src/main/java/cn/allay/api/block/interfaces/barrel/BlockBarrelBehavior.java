package cn.allay.api.block.interfaces.barrel;

import cn.allay.api.block.BlockBehavior;
import cn.allay.api.block.component.base.BlockBaseComponentImpl;
import cn.allay.api.block.component.blockentity.BlockEntityHolderComponent;
import cn.allay.api.block.data.BlockFace;
import cn.allay.api.block.type.BlockState;
import cn.allay.api.block.type.BlockType;
import cn.allay.api.block.type.BlockTypeBuilder;
import cn.allay.api.blockentity.interfaces.barrel.BlockEntityBarrel;
import cn.allay.api.data.VanillaBlockId;
import cn.allay.api.entity.interfaces.player.EntityPlayer;
import cn.allay.api.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;
import org.joml.Vector3ic;

import static cn.allay.api.data.VanillaBlockPropertyTypes.FACING_DIRECTION;
import static cn.allay.api.data.VanillaBlockPropertyTypes.OPEN_BIT;
import static java.lang.Math.abs;

/**
 * @author daoge_cmd | Cool_Loong <br>
 * Allay Project <br>
 */
public interface BlockBarrelBehavior extends
        BlockBehavior, BlockEntityHolderComponent<BlockEntityBarrel> {
    BlockType<BlockBarrelBehavior> BARREL_TYPE = BlockTypeBuilder
            .builder(BlockBarrelBehavior.class)
            .vanillaBlock(VanillaBlockId.BARREL)
            .setProperties(FACING_DIRECTION, OPEN_BIT)
            .setBlockBaseComponentSupplier(
                    blockType -> new BlockBaseComponentImpl(blockType) {
                        @Override
                        public boolean place(@Nullable EntityPlayer player, @NotNull World world, @NotNull BlockState blockState, @NotNull Vector3ic targetBlockPos, @NotNull Vector3ic placeBlockPos, Vector3fc clickPos, @NotNull BlockFace blockFace) {
                            if (player != null) {
                                if (abs(player.getLocation().x() - placeBlockPos.x()) < 2 && abs(player.getLocation().z() - placeBlockPos.z()) < 2) {
                                    var y = player.getLocation().y() + player.getEyeHeight();
                                    if (y - placeBlockPos.y() > 2) {
                                        blockState = blockState.setProperty(FACING_DIRECTION, BlockFace.UP.ordinal());
                                    } else if (placeBlockPos.y() - y > 0) {
                                        blockState = blockState.setProperty(FACING_DIRECTION, BlockFace.DOWN.ordinal());
                                    } else {
                                        blockState = blockState.setProperty(FACING_DIRECTION, player.getHorizontalFace().opposite().ordinal());
                                    }
                                } else {
                                    blockState = blockState.setProperty(FACING_DIRECTION, player.getHorizontalFace().opposite().ordinal());
                                }
                            }
                            world.setBlockState(placeBlockPos.x(), placeBlockPos.y(), placeBlockPos.z(), blockState);
                            return true;
                        }
                    }
            )
            .bindBlockEntity(BlockEntityBarrel.BARREL_TYPE)
            .build();
}
