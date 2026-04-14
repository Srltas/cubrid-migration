package com.cmt.e2e.framework.assertion.strategies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.cmt.e2e.framework.assertion.VerificationFailedException;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.util.TextUtil;

public class PlainTextVerificationStrategy implements VerificationStrategy {

    @Override
    public void verify(CommandResult actualResult, Path expectedAnswerPath) throws IOException {
        String actualText = TextUtil.trimLines(actualResult.stdout());
        String expectedText = TextUtil.trimLines(Files.readString(expectedAnswerPath));

        if (!actualText.equals(expectedText)) {
            throw new VerificationFailedException("Text content does not match.", actualText, expectedText);
        }
    }
}
