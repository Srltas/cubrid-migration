package com.cubrid.cubridmigration.ui.database.provider;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbmetadata.IBuildSchemaFilter;
import com.cubrid.cubridmigration.core.dbmetadata.JDBCDBSchemaFetcherFacade;
import com.cubrid.cubridmigration.core.dbmetadata.session.CatalogCacheManager;
import com.cubrid.cubridmigration.core.dbmetadata.session.CatalogHeader;
import com.cubrid.cubridmigration.core.dbmetadata.session.SourceMetadataSession;
import com.cubrid.cubridmigration.core.dbmetadata.session.SourceSchemaSummary;
import com.cubrid.cubridmigration.core.dbmetadata.session.SourceSchemaSummary.LoadState;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.ui.database.SchemaFetcherWithProgress;
import com.cubrid.cubridmigration.ui.message.Messages;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * JDBC-based implementation of {@link SourceMetadataProvider}. The concrete loading logic will be
 * provided in later phases once the wizard consumes the new lazy-loading infrastructure.
 */
public class JdbcSourceMetadataProvider implements SourceMetadataProvider {

    @Override
    public CatalogHeader loadHeader(ConnParameters connParameters) {
        if (connParameters == null) {
            return null;
        }
        return new CatalogHeader(connParameters.getDbName(), null, connParameters.getTimeZone());
    }

    @Override
    public List<SourceSchemaSummary> listSchemas(SourceMetadataSession session) {
        if (session == null || session.getConnParameters() == null) {
            return Collections.emptyList();
        }
        JDBCDBSchemaFetcherFacade facade = new JDBCDBSchemaFetcherFacade();
        List<String> schemaNames = facade.listSchemaNames(session.getConnParameters());
        if (schemaNames == null || schemaNames.isEmpty()) {
            return Collections.emptyList();
        }
        String conUser = session.getConnParameters().getConUser();
        List<SourceSchemaSummary> summaries = new ArrayList<SourceSchemaSummary>(schemaNames.size());
        for (String name : schemaNames) {
            SourceSchemaSummary summary = new SourceSchemaSummary(name);
            if (StringUtils.isNotBlank(conUser)) {
                summary.setGrantorSchema(!conUser.equalsIgnoreCase(name));
            }
            session.putSummary(summary);
            summaries.add(summary);
        }
        return summaries;
    }

    @Override
    public Catalog loadSchemaDetails(
            SourceMetadataSession session, Collection<String> schemaNames, IProgressMonitor monitor) {
        if (session == null || session.getConnParameters() == null) {
            return null;
        }
        if (schemaNames == null || schemaNames.isEmpty()) {
            return null;
        }

        SelectedSchemaFilter filter = new SelectedSchemaFilter(schemaNames);
        if (filter.isEmpty()) {
            return null;
        }

        SchemaFetcherWithProgress fetcher = SchemaFetcherWithProgress.getInstance(session.getConnParameters());
        Catalog catalog = fetcher.fetch(filter);
        if (catalog == null) {
            Exception error = fetcher.getError();
            String message = fetcher.getErrorMessage();
            if (StringUtils.isBlank(message)) {
                message = Messages.errMsgLoadSchemaFailed;
            }
            throw new RuntimeException(message, error);
        }

        List<Schema> schemas = catalog.getSchemas();
        if (schemas == null || schemas.isEmpty()) {
            return catalog;
        }

        CatalogCacheManager cacheManager = CatalogCacheManager.getInstance();
        ConnParameters connParameters = session.getConnParameters();

        for (Schema schema : new ArrayList<Schema>(schemas)) {
            if (schema == null || !filter.accepts(schema.getName())) {
                continue;
            }
            Catalog fragment = catalog.createCatalog();
            fragment.getSchemas().clear();
            fragment.addSchema(cloneSchema(schema));
            session.putFragment(schema.getName(), fragment);
            if (connParameters != null) {
                cacheManager.storeFragment(connParameters, schema.getName(), fragment);
            }
            SourceSchemaSummary summary = session.getSummary(schema.getName());
            if (summary != null) {
                summary.setLoadState(LoadState.LOADED);
                summary.setGrantorSchema(schema.isGrantorSchema());
            }
        }

        if (connParameters != null) {
            cacheManager.storeSummaries(connParameters, session.getSummaries());
        }

        return catalog;
    }

    private Schema cloneSchema(Schema schema) {
        if (schema == null) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(schema);
            oos.flush();
            try (ObjectInputStream ois =
                    new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                return (Schema) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new RuntimeException("Failed to clone schema " + schema.getName(), ex);
        }
    }

    private static final class SelectedSchemaFilter implements IBuildSchemaFilter {

        private final Set<String> allowedSchemas = new LinkedHashSet<String>();

        SelectedSchemaFilter(Collection<String> schemaNames) {
            if (schemaNames != null) {
                for (String name : schemaNames) {
                    if (StringUtils.isNotBlank(name)) {
                        allowedSchemas.add(name.toUpperCase(Locale.US));
                    }
                }
            }
        }

        boolean isEmpty() {
            return allowedSchemas.isEmpty();
        }

        boolean accepts(String schemaName) {
            if (schemaName == null) {
                return false;
            }
            return allowedSchemas.contains(schemaName.toUpperCase(Locale.US));
        }

        @Override
        public boolean filter(String schema, String objName) {
            if (schema == null) {
                return false;
            }
            return !allowedSchemas.contains(schema.toUpperCase(Locale.US));
        }
    }
}
