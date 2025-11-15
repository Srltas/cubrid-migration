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
 */
package com.cubrid.cubridmigration.ui.source;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Session object storing the source metadata retrieved during a wizard session.
 */
public class SourceMetadataSession {

    private final ConnParameters connParameters;
    private final Map<String, SchemaSummary> summariesByName = new LinkedHashMap<String, SchemaSummary>();
    private final Map<String, Schema> schemaCache = new LinkedHashMap<String, Schema>();

    public SourceMetadataSession(ConnParameters connParameters) {
        this.connParameters = connParameters;
    }

    public ConnParameters getConnParameters() {
        return connParameters;
    }

    public List<SchemaSummary> getSchemaSummaries() {
        return Collections.unmodifiableList(new ArrayList<SchemaSummary>(summariesByName.values()));
    }

    public void setSchemaSummaries(Collection<SchemaSummary> summaries) {
        summariesByName.clear();
        if (summaries == null) {
            return;
        }
        for (SchemaSummary summary : summaries) {
            if (summary == null || summary.getName() == null) {
                continue;
            }
            SchemaSummary stored =
                    new SchemaSummary(summary.getName(), isSchemaCached(summary.getName()));
            summariesByName.put(normalize(summary.getName()), stored);
        }
    }

    public Schema getCachedSchema(String schemaName) {
        return schemaCache.get(normalize(schemaName));
    }

    public boolean isSchemaCached(String schemaName) {
        return schemaCache.containsKey(normalize(schemaName));
    }

    public void clearSchemaCache() {
        schemaCache.clear();
        for (SchemaSummary summary : summariesByName.values()) {
            summary.setDetailsLoaded(false);
        }
    }

    public void mergeSchemas(Collection<Schema> schemas) {
        if (schemas == null) {
            return;
        }
        for (Schema schema : schemas) {
            if (schema == null || schema.getName() == null) {
                continue;
            }
            String key = normalize(schema.getName());
            schemaCache.put(key, schema);

            SchemaSummary summary = summariesByName.get(key);
            if (summary == null) {
                summary = new SchemaSummary(schema.getName(), true);
                summariesByName.put(key, summary);
            } else {
                summary.setDetailsLoaded(true);
            }
        }
    }

    private String normalize(String schemaName) {
        if (schemaName == null) {
            return "";
        }
        return schemaName.toUpperCase(Locale.US);
    }
}
