package com.cubrid.cubridmigration.ui.database.provider;

import com.cubrid.cubridmigration.core.dbmetadata.session.SourceMetadataSession;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Helper responsible for composing a working {@link Catalog} from session fragments.
 */
public class CatalogAssembler {

    public Catalog buildCatalog(SourceMetadataSession session, Collection<String> schemaNames) {
        if (session == null || schemaNames == null || schemaNames.isEmpty()) {
            return null;
        }
        Catalog assembled = null;
        Set<String> appendedSchemas = new LinkedHashSet<String>();
        for (String schemaName : schemaNames) {
            Catalog fragment = session.getFragment(schemaName);
            if (fragment == null) {
                continue;
            }
            Catalog fragmentClone = deepClone(fragment);
            if (fragmentClone == null) {
                continue;
            }
            if (assembled == null) {
                assembled = fragmentClone;
            } else {
                for (Schema schema : fragmentClone.getSchemas()) {
                    if (schema == null) {
                        continue;
                    }
                    String normalizedName =
                            schema.getName() == null
                                    ? null
                                    : schema.getName().toUpperCase(Locale.US);
                    if (normalizedName != null && appendedSchemas.contains(normalizedName)) {
                        continue;
                    }
                    assembled.addSchema(schema);
                    if (normalizedName != null) {
                        appendedSchemas.add(normalizedName);
                    }
                }
            }
            for (Schema schema : fragmentClone.getSchemas()) {
                if (schema != null && schema.getName() != null) {
                    appendedSchemas.add(schema.getName().toUpperCase(Locale.US));
                }
            }
        }
        return assembled;
    }

    private Catalog deepClone(Catalog catalog) {
        if (catalog == null) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(catalog);
            oos.flush();
            try (ObjectInputStream ois =
                    new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                return (Catalog) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new RuntimeException("Failed to clone catalog", ex);
        }
    }
}
