package com.cmt.e2e.framework.command.impls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReportCommand extends CmtCommand {

    private final String mhFile;

    public ReportCommand(List<String> options, String mhFile) {
        super("report", options);
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

        public Builder allOutput() {
            options.add("-ao");
            return this;
        }

        public Builder mhFile(String mhFileName) {
            this.mhFile = mhFileName;
            return this;
        }

        public ReportCommand build() {
            return new ReportCommand(Collections.unmodifiableList(options), mhFile);
        }
    }
}
