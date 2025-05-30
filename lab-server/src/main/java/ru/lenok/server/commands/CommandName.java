package ru.lenok.server.commands;


import ru.lenok.common.commands.ArgType;
import ru.lenok.common.commands.CommandBehavior;

import static ru.lenok.common.commands.CommandBehavior.*;

public enum CommandName {

    insert(STRING_ARG_HAS_ELEM),
    exit(CLIENT),
    show(SIMPLE),
    save(SIMPLE),
    remove_key(STRING_ARG_NO_ELEM),
    update_id(LONG_ARG_HAS_ELEM),
    print_ascending(SIMPLE),
    remove_greater(NO_ARG_HAS_ELEM),
    replace_if_greater(STRING_ARG_HAS_ELEM),
    filter_contains_description(STRING_ARG_NO_ELEM),
    filter_starts_with_name(STRING_ARG_NO_ELEM),
    help(SIMPLE),
    info(SIMPLE),
    clear(SIMPLE),
    history(SIMPLE),
    execute_script(STRING_ARG_NO_ELEM_CLIENT),
    make_offer(LONG_LONG_ARGS_NO_ELEM),
    show_products(SIMPLE),
    show_incoming_offers(SIMPLE),
    show_outgoing_offers(SIMPLE),
    accept_offer(LONG_ARG_NO_ELEM),
    register_product(STRING_ARG_NO_ELEM);
    private final CommandBehavior behavior;

    CommandName(CommandBehavior behavior) {
        this.behavior = behavior;
    }

    public CommandBehavior getBehavior() {
        return behavior;
    }

    public boolean hasElement() {
        return behavior.hasElement();
    }

    public ArgType getArgType() {
        return behavior.getArgType1();
    }

    public boolean hasArg() {
        return behavior.getArgType1() != null;
    }

    public boolean isClient() {
        return behavior.isClient();
    }
}
