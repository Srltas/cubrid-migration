package com.cmt.e2e.framework.db.precond;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;

import com.cmt.e2e.framework.db.config.DbConfig;
import com.cmt.e2e.framework.db.jdbc.JdbcPreflight;
import com.cmt.e2e.framework.db.jdbc.strategy.JdbcUrlStrategy;

/**
 * 테스트 실행 전 환경 사전 조건을 검증하는 유틸리티 클래스
 */
public final class TestPreconditions {
    private TestPreconditions() {}

    public static void assertTcpConnection(DbConfig config) throws IOException {
        assertTcpConnection(config.getSourceHost(), config.getSourcePort());
    }

    public static void assertJdbcConnection(DbConfig config, JdbcUrlStrategy jdbcUrlStrategy) throws IOException {
        assertJdbcConnection(
            config.getSourceDriverJarPath(),
            jdbcUrlStrategy,
            config.getSourceHost(),
            config.getSourcePort(),
            config.getSourceDbName(),
            config.getSourceUser(),
            config.getSourcePassword(),
            config.getSourceCharset()
        );
    }

    /**
     * 대상 호스트와 포트로 TCP 소켓 연결을 시도하여 네트워크 연결성을 확인합니다.
     */
    public static void assertTcpConnection(String host, int port) {
        try (var s = new Socket(host, port)) {
            // 연결 성공
        } catch (Exception e) {
            throw new AssertionError(
                "[NET] TCP preflight check FAILED to "
                    + host + ":" + port, e);
        }
    }

    /**
     * CUBRID DB에 대한 JDBC 사전 점검을 수행합니다.
     */
    public static void assertJdbcConnection(Path dirverJarPath, JdbcUrlStrategy jdbcUrlStrategy,
                                            String host, int port, String dbName, String user, String password, String charset) {
        JdbcPreflight.run(dirverJarPath, jdbcUrlStrategy, host, port, dbName, user, password, charset);
    }
}
