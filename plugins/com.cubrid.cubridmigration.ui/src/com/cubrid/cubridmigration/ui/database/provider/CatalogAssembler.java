package com.cubrid.cubridmigration.ui.database.provider;

import com.cubrid.cubridmigration.core.dbmetadata.session.SourceMetadataSession;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import java.util.Collection;

/**
 * Helper responsible for composing a working {@link Catalog} from session fragments.
 */
public class CatalogAssembler {

    public Catalog buildCatalog(SourceMetadataSession session, Collection<String> schemaNames) {
        throw new UnsupportedOperationException("buildCatalog has not been implemented yet");
    }
}
