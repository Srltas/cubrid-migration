package com.cmt.e2e.framework.command;

import java.util.Map;

/**
 * 대화형 프롬프트에 자동 응답이 필요한 커맨드를 나타냅니다.
 * CommandRunner.runInteractive()에서 프롬프트 패턴(정규식)을 감지하면
 * 대응하는 응답 문자열을 stdin에 전달합니다.
 */
public interface InteractiveCommand extends Command {

    /**
     * 프롬프트 패턴(정규식) → 응답 문자열 맵을 반환합니다.
     */
    Map<String, String> getResponders();
}
