package com.cmt.e2e.framework.assertion.strategies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.cmt.e2e.framework.assertion.VerificationFailedException;
import com.cmt.e2e.framework.command.execution.CommandResult;
import com.cmt.e2e.framework.util.XmlUtil;

public class XmlVerificationStrategy implements VerificationStrategy {

    @Override
    public void verify(CommandResult actualResult, Path expectedAnswerPath) throws IOException {
        String expectedXml = Files.readString(expectedAnswerPath);
        String actualXml = findActualXml(actualResult);

        try {
            XmlUtil.assertSimilarStandard(actualXml, expectedXml);
        } catch (AssertionError e) {
            throw new VerificationFailedException(e.getMessage(), actualXml, expectedXml, e);
        }
    }

    private String findActualXml(CommandResult actualResult) {
        return actualResult.stdout();
    }
}
