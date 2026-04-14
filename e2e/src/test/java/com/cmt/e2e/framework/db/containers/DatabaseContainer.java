package com.cmt.e2e.framework.db.containers;

import com.cmt.e2e.framework.db.driver.Drivers.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startable;

public interface DatabaseContainer extends Startable {

    /**
     * 컨테이너가 실행 중인 호스트 이름을 반환
     */
    String getHost();

    /**
     * 외부에 노출된 DB 포트 번호를 반환
     */
    Integer getDatabasePort();

    /**
     * 이 컨테이너의 DB 종류를 반환
     */
    DB getDbType();

    /**
     * 내부적으로 사용하는 Testcontainers의 GenericContainer 인스턴스를 반환
     */
    GenericContainer<?> getContainer();

    /**
     * 이 DB 타입에 맞는 JDBC URL을 생성합니다.
     * @param dbName 데이터베이스 이름
     * @param user 접속 사용자
     * @return JDBC URL 문자열
     */
    String getJdbcUrl(String dbName, String user);
}
