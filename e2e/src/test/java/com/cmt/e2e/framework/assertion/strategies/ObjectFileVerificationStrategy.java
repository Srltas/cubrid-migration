package com.cmt.e2e.framework.assertion.strategies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cmt.e2e.framework.assertion.VerificationFailedException;
import com.cmt.e2e.framework.command.execution.CommandResult;

/**
 * CUBRID load 형식의 object 파일을 검증하는 전략.
 *
 * <p>멀티스레드 마이그레이션으로 인해 레코드 출력 순서가 비결정적이므로,
 * actual과 expected 양쪽을 동일하게 정규화(클래스 헤더 정렬 + 레코드 정렬)한 뒤 비교합니다.
 *
 * <p>object 파일 형식:
 * <pre>
 * %class [PUBLIC].[code] ([s_name] [f_name])
 * 'B' 'Bronze'
 * 'G' 'Gold'
 * %class [PUBLIC].[history] ([event_code] [athlete] ...)
 * 20005 'Hayes Joanna' 2004 '12.37' 'time'
 * </pre>
 */
public class ObjectFileVerificationStrategy implements VerificationStrategy {

    @Override
    public void verify(CommandResult actualResult, Path expectedAnswerPath) throws IOException, AssertionError {
        String normalizedActual = normalize(actualResult.stdout());
        String normalizedExpected = normalize(Files.readString(expectedAnswerPath));

        if (!normalizedActual.equals(normalizedExpected)) {
            throw new VerificationFailedException(
                "Object file content mismatch (after sort-normalization).",
                normalizedActual, normalizedExpected);
        }
    }

    /**
     * Object 파일 내용을 정규화합니다.
     * 1. %class 헤더별로 레코드를 그룹핑
     * 2. 클래스 헤더 기준으로 정렬
     * 3. 각 클래스 내 레코드를 사전순 정렬
     */
    private String normalize(String content) {
        // 1. 파싱: Map<클래스 헤더, 레코드 리스트>
        Map<String, List<String>> classMap = new LinkedHashMap<>();
        String currentClass = null;

        for (String line : content.split("\\R")) {
            if (line.startsWith("%class")) {
                currentClass = line;
                classMap.put(currentClass, new ArrayList<>());
            } else if (currentClass != null && !line.isBlank()) {
                classMap.get(currentClass).add(line);
            }
        }

        // 2. 정규화: 클래스 헤더 정렬 + 각 클래스 내 레코드 정렬
        return classMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                String header = entry.getKey();
                String sortedRecords = entry.getValue().stream()
                    .sorted()
                    .collect(Collectors.joining("\n"));
                return header + "\n" + sortedRecords;
            })
            .collect(Collectors.joining("\n"));
    }
}
