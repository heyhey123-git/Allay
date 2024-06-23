package org.allaymc.server.command.defaults;

import org.allaymc.api.command.SimpleCommand;
import org.allaymc.api.command.tree.CommandTree;
import org.allaymc.api.entity.effect.EffectType;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.i18n.TrKeys;

import java.util.Collection;

/**
 * Allay Project 23/06/2024
 *
 * @author IWareQ
 */
public class EffectCommand extends SimpleCommand {
    public EffectCommand() {
        super("effect", TrKeys.M_COMMANDS_EFFECT_DESCRIPTION);
    }

    @Override
    public void prepareCommandTree(CommandTree tree) {
        tree.getRoot()
                .playerTarget("player")
                .effectNode("effect")
                .intNum("seconds", 30).optional()
                .intNum("amplifier").optional()
                .bool("hideParticles").optional()
                .exec(context -> {
                    Collection<EntityPlayer> players = context.getResult(0);
                    EffectType effectType = context.getResult(1);
                    int seconds = (int) context.getResult(2) * 20; // because effect duration in ticks
                    int amplifier = context.getResult(3);
                    boolean hideParticles = context.getResult(4);

                    players.forEach(player -> {
                        player.addEffect(effectType.createInstance(amplifier, seconds, !hideParticles));
                        context.addOutput(
                                TrKeys.M_COMMANDS_EFFECT_SUCCESS,
                                effectType.getIdentifier().path(), // TODO: I18N
                                amplifier,
                                player.getDisplayName(),
                                seconds
                        );
                    });

                    return context.success();
                })
                .root()
                .playerTarget("player")
                .key("clear")
                .exec(context -> {
                    Collection<EntityPlayer> players = context.getResult(0);
                    players.forEach(player -> {
                        player.removeAllEffects();
                        context.addOutput(TrKeys.M_COMMANDS_EFFECT_SUCCESS_REMOVED_ALL, player.getDisplayName());
                    });
                    return context.success();
                });
    }
}
