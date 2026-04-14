package com.cmt.e2e.framework.command.impls;

import java.util.ArrayList;
import java.util.List;

import com.cmt.e2e.framework.command.Command;

public abstract class CmtCommand implements Command {
    protected static final String MIGRATION_SHELL = "./migration.sh";
    protected final String subCommand;
    protected  final List<String> options;

    public CmtCommand(String subCommand, List<String> options) {
        this.subCommand = subCommand;
        this.options = options;
    }

    public List<String> build() {
        List<String> commandList = new ArrayList<>();
        commandList.add(MIGRATION_SHELL);
        if (subCommand != null && !subCommand.isBlank()) {
            commandList.add(subCommand);
        }
        commandList.addAll(options);
        return commandList;
    }
}
