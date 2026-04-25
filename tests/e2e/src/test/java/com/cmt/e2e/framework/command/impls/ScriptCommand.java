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

        public Builder sourceConfig(String sourceConfigName) {
            options.addAll(Arrays.asList("-s", sourceConfigName));
            return this;
        }

        public Builder targetConfig(String targetConfigName) {
            options.addAll(Arrays.asList("-t", targetConfigName));
            return this;
        }

        public Builder outputDir(String outputPath) {
            options.addAll(Arrays.asList("-o", outputPath));
            return this;
        }

        /** @deprecated Use {@link #sourceConfig(String)}. */
        @Deprecated
        public Builder source(String source) {
            return sourceConfig(source);
        }

        /** @deprecated Use {@link #targetConfig(String)}. */
        @Deprecated
        public Builder target(String target) {
            return targetConfig(target);
        }

        /** @deprecated Use {@link #outputDir(String)}. */
        @Deprecated
        public Builder output(String outputPath) {
            return outputDir(outputPath);
        }

        public ScriptCommand build() {
            return new ScriptCommand(options);
        }
    }
}
