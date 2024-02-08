package org.allaymc.api.perm;

import org.allaymc.api.command.Command;
import org.allaymc.api.perm.tree.PermTree;

import java.util.HashMap;
import java.util.Map;

import static org.allaymc.api.perm.PermKeys.*;

/**
 * Allay Project 2023/12/30
 *
 * @author daoge_cmd
 */
public final class DefaultPermissions {

    public static final PermTree VISITOR = PermTree.create("Visitor");

    public static final PermTree MEMBER =
            PermTree.create("Member")
                    .extendFrom(VISITOR)
                    .addPerm(BUILD).addPerm(MINE)
                    .addPerm(DOORS_AND_SWITCHES).addPerm(OPEN_CONTAINERS)
                    .addPerm(ATTACK_PLAYERS).addPerm(ATTACK_MOBS)
                    .addPerm(SUMMON_LIGHTNING).addPerm(PVM).addPerm(MVP)
                    // .addPerm(Command.COMMAND_PERM_PREFIX + "help") // TODO
                    .addPerm(Command.COMMAND_PERM_PREFIX + "me");

    public static final PermTree OPERATOR = PermTree.create("Operator").extendFrom(MEMBER);

    private static final Map<String, PermTree> NAME_LOOK_UP = new HashMap<>();

    static {
        NAME_LOOK_UP.put(VISITOR.getName(), VISITOR);
        NAME_LOOK_UP.put(MEMBER.getName(), MEMBER);
        NAME_LOOK_UP.put(OPERATOR.getName(), OPERATOR);
    }

    public static PermTree byName(String name) {
        return NAME_LOOK_UP.get(name);
    }
}
