package com.cmt.e2e.framework.command;

import java.util.List;

public interface Command {
    /**
     * 실행 가능한 문자열 리스트로 명령어를 반환합니다.
     * 예: ["./migration.sh", "script", "-s", "cubrid_source", ...]
     */
    List<String> build();
}
