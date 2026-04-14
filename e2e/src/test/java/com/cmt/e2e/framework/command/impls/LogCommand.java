package com.cmt.e2e.framework.command.impls;

import java.util.*;

import com.cmt.e2e.framework.command.InteractiveCommand;

public class LogCommand extends CmtCommand implements InteractiveCommand {

    private static final Map<String, String> DEFAULT_RESPONDERS =
        Map.of("<Press \\[enter\\] to continue...>", "\n");
    private final String mhFile;

    private LogCommand(List<String> options, String mhFile) {
        super("log", options);
        this.mhFile = mhFile;
    }

    @Override
    public List<String> build() {
        List<String> commandList = super.build();
        if (mhFile != null && !mhFile.isBlank()) {
            commandList.add(mhFile);
        }
        return commandList;
    }

    @Override
    public Map<String, String> getResponders() {
        return DEFAULT_RESPONDERS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<String> options = new ArrayList<>();
        private String mhFile;

        public Builder latest() {
            options.add("-l");
            return this;
        }

        public Builder pageSize(int size) {
            options.addAll(Arrays.asList("-ps", String.valueOf(size)));
            return this;
        }

        public Builder mhFile(String mhFileName) {
            this.mhFile = mhFileName;
            return this;
        }

        public LogCommand build() {

            return new LogCommand(Collections.unmodifiableList(options), mhFile);
        }
    }
}
