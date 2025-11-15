package com.cubrid.cubridmigration.core.dbmetadata.session;

import java.util.Objects;

/**
 * Lightweight status holder for a source schema.
 */
public class SourceSchemaSummary {

    public enum LoadState {
        NOT_LOADED,
        LOADING,
        LOADED,
        FAILED
    }

    private final String schemaName;
    private boolean selected;
    private LoadState loadState;
    private boolean grantorSchema;

    public SourceSchemaSummary(String schemaName) {
        this(schemaName, false, LoadState.NOT_LOADED);
    }

    public SourceSchemaSummary(String schemaName, boolean selected, LoadState loadState) {
        this.schemaName = schemaName;
        this.selected = selected;
        this.loadState = loadState;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public LoadState getLoadState() {
        return loadState;
    }

    public void setLoadState(LoadState loadState) {
        this.loadState = loadState;
    }

    public boolean isGrantorSchema() {
        return grantorSchema;
    }

    public void setGrantorSchema(boolean grantorSchema) {
        this.grantorSchema = grantorSchema;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SourceSchemaSummary)) {
            return false;
        }
        SourceSchemaSummary other = (SourceSchemaSummary) obj;
        return Objects.equals(schemaName, other.schemaName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaName);
    }

    @Override
    public String toString() {
        return "SourceSchemaSummary{"
                + "schemaName='"
                + schemaName
                + '\''
                + ", selected="
                + selected
                + ", loadState="
                + loadState
                + ", grantorSchema="
                + grantorSchema
                + '}';
    }
}
