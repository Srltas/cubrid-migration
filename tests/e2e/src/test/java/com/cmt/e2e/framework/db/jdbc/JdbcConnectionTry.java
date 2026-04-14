package com.cmt.e2e.framework.db.jdbc;

import java.util.Properties;

/**
 * 하나의 JDBC 연결 시도를 표현하는 불변 데이터 객체
 *
 * @param label 연결 시도를 식별하기 위한 이름 (ex: "A/conf-props")
 * @param url   JDBC 연결 URL
 * @param props 연결 속성 (user, password 등)
 */
public record JdbcConnectionTry(String label, String url, Properties props) {
}
