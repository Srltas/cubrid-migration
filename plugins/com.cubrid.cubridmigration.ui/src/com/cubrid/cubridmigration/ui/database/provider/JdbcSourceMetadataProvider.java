package com.cubrid.cubridmigration.ui.database.provider;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbmetadata.session.CatalogHeader;
import com.cubrid.cubridmigration.core.dbmetadata.session.SourceMetadataSession;
import com.cubrid.cubridmigration.core.dbmetadata.session.SourceSchemaSummary;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import java.util.Collection;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * JDBC-based implementation of {@link SourceMetadataProvider}. The concrete loading logic will be
 * provided in later phases once the wizard consumes the new lazy-loading infrastructure.
 */
public class JdbcSourceMetadataProvider implements SourceMetadataProvider {

    @Override
    public CatalogHeader loadHeader(ConnParameters connParameters) {
        throw new UnsupportedOperationException("loadHeader has not been implemented yet");
    }

    @Override
    public List<SourceSchemaSummary> listSchemas(SourceMetadataSession session) {
        throw new UnsupportedOperationException("listSchemas has not been implemented yet");
    }

    @Override
    public Catalog loadSchemaDetails(
            SourceMetadataSession session, Collection<String> schemaNames, IProgressMonitor monitor) {
        throw new UnsupportedOperationException("loadSchemaDetails has not been implemented yet");
    }
}
