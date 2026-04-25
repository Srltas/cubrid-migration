package com.cmt.e2e.framework.command;

import java.util.List;

public interface Command {
    /**
     * Returns the command as an executable string list.
     * Example: ["./migration.sh", "script", "-s", "cubrid_source", ...]
     */
    List<String> build();
}
