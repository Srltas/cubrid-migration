package com.cubrid.cubridmigration.core.dbmetadata.session;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds metadata lookup context so that wizard pages can collaborate without
 * eagerly materialising every schema.
 */
public class SourceMetadataSession {

    private ConnParameters connParameters;
    private CatalogHeader header;
    private final Map<String, SourceSchemaSummary> summaries = new LinkedHashMap<>();
    private final Map<String, Catalog> fragments = new LinkedHashMap<>();
    private Catalog legacyCatalog;

    public void reset(ConnParameters parameters) {
        this.connParameters = parameters;
        this.header = null;
        this.legacyCatalog = null;
        summaries.clear();
        fragments.clear();
    }

    public ConnParameters getConnParameters() {
        return connParameters;
    }

    public CatalogHeader getHeader() {
        return header;
    }

    public void setHeader(CatalogHeader header) {
        this.header = header;
    }

    public void putSummary(SourceSchemaSummary summary) {
        if (summary == null) {
            return;
        }
        SourceSchemaSummary existing = summaries.get(summary.getSchemaName());
        if (existing != null) {
            summary.setSelected(existing.isSelected());
            if (existing.getLoadState() == SourceSchemaSummary.LoadState.LOADED) {
                summary.setLoadState(existing.getLoadState());
            }
            if (existing.isGrantorSchema()) {
                summary.setGrantorSchema(true);
            }
        }
        summaries.put(summary.getSchemaName(), summary);
    }

    public SourceSchemaSummary getSummary(String schemaName) {
        return summaries.get(schemaName);
    }

    public Collection<SourceSchemaSummary> getSummaries() {
        return Collections.unmodifiableCollection(summaries.values());
    }

    public void putFragment(String schemaName, Catalog catalog) {
        if (schemaName == null || catalog == null) {
            return;
        }
        fragments.put(schemaName, catalog);
    }

    public Catalog getFragment(String schemaName) {
        return fragments.get(schemaName);
    }

    public Map<String, Catalog> getFragments() {
        return Collections.unmodifiableMap(fragments);
    }

    public boolean isActiveFor(ConnParameters parameters) {
        return Objects.equals(connParameters, parameters);
    }

    public Catalog getLegacyCatalog() {
        return legacyCatalog;
    }

    public void setLegacyCatalog(Catalog legacyCatalog) {
        this.legacyCatalog = legacyCatalog;
    }
}
