/*
 * Copyright (C) 2008 Search Solution Corporation.
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
package com.cubrid.cubridmigration.cubrid.trans.converter;

import com.cubrid.cubridmigration.core.common.DBUtils;
import com.cubrid.cubridmigration.core.datatype.DataTypeInstance;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.trans.AbstractDataConverter;
import com.cubrid.cubridmigration.cubrid.CUBRIDTimeUtil;
import java.text.ParseException;
import java.util.Calendar;

/**
 * DateConverter Description
 *
 * @author Kevin.Wang
 * @version 1.0 - 2011-11-29 created by Kevin.Wang
 */
public class DateConverter extends AbstractDataConverter {

    /**
     * @param obj Object
     * @param dti DataTypeInstance
     * @param config MigrationConfiguration
     * @return value Object
     */
    public Object convert(Object obj, DataTypeInstance dti, MigrationConfiguration config) {
        if (obj instanceof java.sql.Date) {
            return obj;
        }

        if (obj instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) obj).getTime());
        }

        if (obj instanceof Calendar) {
            return ((Calendar) obj).getTime();
        }

        Object value = null;
        Exception ex = null;
        try {
            value =
                    new java.sql.Date(
                            CUBRIDTimeUtil.parseDate2Long(
                                    obj.toString(), config.getSourceDatabaseTimeZone()));
        } catch (Exception e) {
            ex = e;
        }
        if (ex != null) {
            try {
                value = new java.sql.Date(DBUtils.getDateFormat().parse(obj.toString()).getTime());

                if (value.toString().charAt(0) == 0) {
                    throw new RuntimeException(
                            "java.sql.Date could not build date correctly (not in range 999 - 9999 ?): "
                                    + obj,
                            ex);
                }
            } catch (ParseException e1) {
                throw new RuntimeException(
                        "ERROR: could not convert:" + obj + " to CUBRID type Date", e1);
            }
        }

        return value;
    }
}
