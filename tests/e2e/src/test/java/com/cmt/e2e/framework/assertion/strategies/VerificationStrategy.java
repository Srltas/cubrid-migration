package com.cmt.e2e.framework.assertion.strategies;

import java.io.IOException;
import java.nio.file.Path;

import com.cmt.e2e.framework.command.execution.CommandResult;

@FunctionalInterface
public interface VerificationStrategy {
    void verify(CommandResult actualResult, Path expectedAnswerPath) throws IOException, AssertionError;
}
