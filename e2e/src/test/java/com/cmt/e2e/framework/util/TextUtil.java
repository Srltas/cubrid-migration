package com.cmt.e2e.framework.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class TextUtil {
    private TextUtil() {}

    /**
     * 각 줄의 앞뒤 공백을 제거하고 줄바꿈을 \n으로 정규화합니다.
     */
    public static String trimLines(String input) {
        if (input == null) return "";
        return Arrays.stream(input.split("\\R"))
            .map(String::trim)
            .collect(Collectors.joining("\n"));
    }
}
