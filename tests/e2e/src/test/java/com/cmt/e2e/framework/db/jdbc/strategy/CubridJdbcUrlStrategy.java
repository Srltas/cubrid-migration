package com.cmt.e2e.framework.db.jdbc.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.cmt.e2e.framework.db.jdbc.JdbcConnectionTry;

/**
 * CUBRID를 위한 JDBC 연결 전략 구현체
 */
public class CubridJdbcUrlStrategy implements JdbcUrlStrategy {

    @Override
    public String getDriverClassName() {
        return "cubrid.jdbc.driver.CUBRIDDriver";
    }

    @Override
    public List<JdbcConnectionTry> buildConnectionAttempts(String host, int port, String dbname, String user, String pass, String charset) {
        String baseUrl = String.format("jdbc:cubrid:%s:%d:%s", host, port, dbname);
        List<JdbcConnectionTry> tries = new ArrayList<>();

        {
            String url = baseUrl + ":::?charset=" + charset;
            var p = new Properties();
            p.setProperty("user", user == null ? "" : user);
            p.setProperty("password", pass == null ? "" : pass);
            tries.add(new JdbcConnectionTry("primary/conf-props", url, p));
        }

        {
            String url = String.format("jdbc:cubrid:%s:%d:%s:%s:%s:?charset=%s",
                host, port, dbname,
                user == null ? "" : user,
                pass == null ? "" : pass,
                charset);
            tries.add(new JdbcConnectionTry("fallback/url-embed-user", url, new Properties()));
        }

        return tries;
    }
}
