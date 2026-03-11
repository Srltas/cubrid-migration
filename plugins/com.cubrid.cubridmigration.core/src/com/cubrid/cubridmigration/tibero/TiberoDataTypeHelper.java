/*
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.tibero;

import com.cubrid.cubridmigration.core.datatype.DBDataTypeHelper;
import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class TiberoDataTypeHelper extends DBDataTypeHelper {

    private static final TiberoDataTypeHelper HELPER = new TiberoDataTypeHelper();

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("TIMESTAMP\\(\\d*\\)");
    private static final Pattern TIME_PATTERN = Pattern.compile("TIME\\(\\d*\\)");
    private static final Pattern TIMESTAMP_TZ_PATTERN =
            Pattern.compile("TIMESTAMP\\(\\d*\\) WITH TIME ZONE");
    private static final Pattern TIMESTAMP_LTZ_PATTERN =
            Pattern.compile("TIMESTAMP\\(\\d*\\) WITH LOCAL TIME ZONE");
    private static final Pattern INTERVAL_DS_PATTERN =
            Pattern.compile("INTERVAL DAY\\(\\d*\\) TO SECOND\\(\\d*\\)");
    private static final Pattern INTERVAL_YM_PATTERN =
            Pattern.compile("INTERVAL YEAR\\(\\d*\\) TO MONTH");

    /**
     * Singleton
     *
     * @param version of tibero database
     * @return DataTypeHelper
     */
    public static TiberoDataTypeHelper getInstance(String version) {
        return HELPER;
    }

    /**
     * return get Tibero data type key
     *
     * @param dataType String
     * @return String
     */
    public static String getTiberoDataTypeKey(String dataType) {
        if (dataType == null) {
            return "";
        }

        String normalizedType =
                WHITESPACE_PATTERN
                        .matcher(dataType.trim().toUpperCase(Locale.ENGLISH))
                        .replaceAll(" ");
        String key = normalizedType;

        if ("JSON".equals(normalizedType)) {
            key = "JSON";
        } else if ("XMLTYPE".equals(normalizedType) || normalizedType.endsWith(" XMLTYPE")) {
            key = "XMLTYPE";
        } else if (TIMESTAMP_PATTERN.matcher(normalizedType).matches()) {
            key = "TIMESTAMP";
        } else if (TIME_PATTERN.matcher(normalizedType).matches()) {
            key = "TIME";
        } else if (TIMESTAMP_TZ_PATTERN.matcher(normalizedType).matches()) {
            key = "TIMESTAMP WITH TIME ZONE";
        } else if (TIMESTAMP_LTZ_PATTERN.matcher(normalizedType).matches()) {
            key = "TIMESTAMP WITH LOCAL TIME ZONE";
        } else if (INTERVAL_DS_PATTERN.matcher(normalizedType).matches()) {
            key = "INTERVAL DAY TO SECOND";
        } else if (INTERVAL_YM_PATTERN.matcher(normalizedType).matches()) {
            key = "INTERVAL YEAR TO MONTH";
        }
        return key;
    }

    private TiberoDataTypeHelper() {
        // Do nothing here.
    }

    /**
     * Retrieves the Database type.
     *
     * @return DatabaseType
     */
    public DatabaseType getDBType() {
        return DatabaseType.TIBERO;
    }

    /**
     * return data type id
     *
     * @param catalog Catalog
     * @param dataType String
     * @param precision Integer
     * @param scale Integer
     * @return Integer
     */
    public Integer getJdbcDataTypeID(
            Catalog catalog, String dataType, Integer precision, Integer scale) {
        if ("NUMBER".equals(dataType)) {
            return TiberoJdbcTypeMapper.getNumberType(precision, scale);
        }

        if (TiberoJdbcTypeMapper.isUnsupportedJdbcType(dataType)) {
            return null;
        }

        Integer fixedType = TiberoJdbcTypeMapper.getFixedJdbcTypeId(dataType);
        if (fixedType != null) {
            return fixedType;
        }

        String key = getTiberoDataTypeKey(dataType);
        Map<String, List<DataType>> supportedDataType = catalog.getSupportedDataType();
        List<DataType> dataTypeList = supportedDataType.get(key);
        if (dataTypeList == null) {
            throw new IllegalArgumentException("Not supported Tibero data type(" + dataType + ")");
        }

        if (dataTypeList.size() == 1) {
            return dataTypeList.get(0).getJdbcDataTypeID();
        }

        throw new IllegalArgumentException(
                "Not supported Tibero data type("
                        + dataType
                        + ": p="
                        + precision
                        + ", s="
                        + scale
                        + ")");
    }

    /**
     * generate data type via JDBC meta data information of CUBRID
     *
     * @param column Column
     * @return String
     */
    public String getShownDataType(Column column) {
        return TiberoTypeFormatter.format(column, this);
    }

    /**
     * Retrieves if The data type of column is binary type such as blob/bit ...
     *
     * @param dataType Column
     * @return true or false
     */
    public boolean isBinary(String dataType) {
        return "blob".equalsIgnoreCase(dataType);
    }

    /**
     * Tibero server does not support collection type.
     *
     * @param dataType Integer
     * @return false
     */
    public boolean isCollection(String dataType) {
        return false;
    }
}
