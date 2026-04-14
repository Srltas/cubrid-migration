package com.cmt.e2e.framework.command.impls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScriptCommand extends CmtCommand {

    private ScriptCommand(List<String> options) {
        super("script", options);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<String> options = new ArrayList<>();

        public Builder source(String source) {
            options.addAll(Arrays.asList("-s", source));
            return this;
        }

        public Builder target(String target) {
            options.addAll(Arrays.asList("-t", target));
            return this;
        }

        public Builder output(String outputPath) {
            options.addAll(Arrays.asList("-o", outputPath));
            return this;
        }

        public ScriptCommand build() {
            return new ScriptCommand(options);
        }
    }
}
