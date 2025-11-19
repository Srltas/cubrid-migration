package com.cubrid.cubridmigration.core.dbmetadata.session;

import java.util.Objects;

/**
 * Lightweight description of a source catalog that can be cached while
 * deferring the full schema tree.
 */
public class CatalogHeader {

    private final String databaseName;
    private final String databaseVersion;
    private final String timeZoneId;

    public CatalogHeader(String databaseName, String databaseVersion, String timeZoneId) {
        this.databaseName = databaseName;
        this.databaseVersion = databaseVersion;
        this.timeZoneId = timeZoneId;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseVersion() {
        return databaseVersion;
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CatalogHeader)) {
            return false;
        }
        CatalogHeader other = (CatalogHeader) obj;
        return Objects.equals(databaseName, other.databaseName)
                && Objects.equals(databaseVersion, other.databaseVersion)
                && Objects.equals(timeZoneId, other.timeZoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseName, databaseVersion, timeZoneId);
    }

    @Override
    public String toString() {
        return "CatalogHeader{"
                + "databaseName='"
                + databaseName
                + '\''
                + ", databaseVersion='"
                + databaseVersion
                + '\''
                + ", timeZoneId='"
                + timeZoneId
                + '\''
                + '}';
    }
}
