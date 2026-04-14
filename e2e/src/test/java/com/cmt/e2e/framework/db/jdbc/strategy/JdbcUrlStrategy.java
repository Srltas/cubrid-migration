package com.cmt.e2e.framework.db.jdbc.strategy;

import java.util.List;

import com.cmt.e2e.framework.db.jdbc.JdbcConnectionTry;

/**
 * DB별 JDBC 연결 방법을 정의하는 전략 인터페이스
 */
public interface JdbcUrlStrategy {
    /**
     * DB의 JDBC 드라이버 클래스 전체 이름을 반환합니다.
     */
    String getDriverClassName();

    /**
     * 해당 DB에 대해 시도해 볼 모든 연결 방법의 리스트를 생성합니다.
     */
    List<JdbcConnectionTry> buildConnectionAttempts(String host, int port, String dbname, String user, String pass, String charset);
}
