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
 * Abstraction over metadata retrieval so the wizard can load schema details on demand.
 */
public interface SourceMetadataProvider {

    CatalogHeader loadHeader(ConnParameters connParameters);

    List<SourceSchemaSummary> listSchemas(SourceMetadataSession session);

    Catalog loadSchemaDetails(
            SourceMetadataSession session, Collection<String> schemaNames, IProgressMonitor monitor);
}
