package com.cmt.e2e.framework.util;

import java.util.List;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * Logback의 ListAppender에 접근하여 로그를 가져오거나 비우는 유틸리티 클래스
 */
public final class InMemoryLogHolder {
    private static ListAppender<ILoggingEvent> listAppender;
    
    static {
        Logger logger = (Logger) LoggerFactory.getLogger("com.cmt.e2e");
        listAppender = (ListAppender<ILoggingEvent>) logger.getAppender("LIST");
    }

    private InMemoryLogHolder() {}

    /**
     * 메모리에 기록된 모든 로그 메시지를 가져옵니다.
     */
    public static List<String> getLogs() {
        if (listAppender == null) {
            return List.of("Logback ListAppender 'LIST' not found.");
        }
        // ILoggingEvent 객체에서 실제 포맷팅된 메시지만 추출하여 반환합니다.
        return listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());
    }

    /**
     * 메모리의 모든 로그를 지웁니다.
     */
    public static void clear() {
        if (listAppender != null) {
            listAppender.list.clear();
        }
    }
}
