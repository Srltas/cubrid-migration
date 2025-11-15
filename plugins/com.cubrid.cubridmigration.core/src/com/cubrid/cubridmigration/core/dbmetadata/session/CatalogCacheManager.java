package com.cubrid.cubridmigration.core.dbmetadata.session;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches catalog metadata fragments so that wizard sessions can resume without reloading metadata
 * from the source database.
 */
public final class CatalogCacheManager {

    private static final CatalogCacheManager INSTANCE = new CatalogCacheManager();

    private final Map<String, CatalogHeader> headerCache = new ConcurrentHashMap<String, CatalogHeader>();
    private final Map<String, Map<String, SourceSchemaSummary>> summaryCache =
            new ConcurrentHashMap<String, Map<String, SourceSchemaSummary>>();
    private final Map<String, Map<String, Catalog>> fragmentCache =
            new ConcurrentHashMap<String, Map<String, Catalog>>();
    private final Map<String, Catalog> legacyCatalogCache =
            new ConcurrentHashMap<String, Catalog>();

    private CatalogCacheManager() {}

    public static CatalogCacheManager getInstance() {
        return INSTANCE;
    }

    public void storeHeader(ConnParameters parameters, CatalogHeader header) {
        String key = buildKey(parameters);
        if (key == null || header == null) {
            return;
        }
        headerCache.put(key, header);
    }

    public CatalogHeader getHeader(ConnParameters parameters) {
        String key = buildKey(parameters);
        if (key == null) {
            return null;
        }
        return headerCache.get(key);
    }

    public void storeSummaries(ConnParameters parameters, Collection<SourceSchemaSummary> summaries) {
        String key = buildKey(parameters);
        if (key == null || summaries == null) {
            return;
        }
        Map<String, SourceSchemaSummary> copy =
                new LinkedHashMap<String, SourceSchemaSummary>(summaries.size());
        for (SourceSchemaSummary summary : summaries) {
            if (summary == null || summary.getSchemaName() == null) {
                continue;
            }
            copy.put(summary.getSchemaName().toUpperCase(Locale.US), cloneSummary(summary));
        }
        if (copy.isEmpty()) {
            summaryCache.remove(key);
        } else {
            summaryCache.put(key, copy);
        }
    }

    public Collection<SourceSchemaSummary> getSummaries(ConnParameters parameters) {
        String key = buildKey(parameters);
        if (key == null) {
            return Collections.emptyList();
        }
        Map<String, SourceSchemaSummary> summaries = summaryCache.get(key);
        if (summaries == null || summaries.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, SourceSchemaSummary> copy =
                new LinkedHashMap<String, SourceSchemaSummary>(summaries.size());
        for (Map.Entry<String, SourceSchemaSummary> entry : summaries.entrySet()) {
            SourceSchemaSummary summary = entry.getValue();
            if (summary != null) {
                copy.put(entry.getKey(), cloneSummary(summary));
            }
        }
        return copy.values();
    }

    public void storeFragment(ConnParameters parameters, String schemaName, Catalog fragment) {
        String key = buildKey(parameters);
        if (key == null || schemaName == null || fragment == null) {
            return;
        }
        Map<String, Catalog> fragments = fragmentCache.get(key);
        if (fragments == null) {
            fragments = new LinkedHashMap<String, Catalog>();
            fragmentCache.put(key, fragments);
        }
        fragments.put(schemaName.toUpperCase(Locale.US), deepClone(fragment));
    }

    public Map<String, Catalog> getFragments(ConnParameters parameters) {
        String key = buildKey(parameters);
        if (key == null) {
            return Collections.emptyMap();
        }
        Map<String, Catalog> fragments = fragmentCache.get(key);
        if (fragments == null || fragments.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Catalog> copy = new LinkedHashMap<String, Catalog>(fragments.size());
        for (Map.Entry<String, Catalog> entry : fragments.entrySet()) {
            Catalog fragment = entry.getValue();
            if (fragment != null) {
                copy.put(entry.getKey(), deepClone(fragment));
            }
        }
        return copy;
    }

    public void storeLegacyCatalog(ConnParameters parameters, Catalog catalog) {
        String key = buildKey(parameters);
        if (key == null) {
            return;
        }
        if (catalog == null) {
            legacyCatalogCache.remove(key);
            return;
        }
        legacyCatalogCache.put(key, deepClone(catalog));
    }

    public Catalog getLegacyCatalog(ConnParameters parameters) {
        String key = buildKey(parameters);
        if (key == null) {
            return null;
        }
        Catalog catalog = legacyCatalogCache.get(key);
        if (catalog == null) {
            return null;
        }
        return deepClone(catalog);
    }

    public void invalidate(ConnParameters parameters) {
        String key = buildKey(parameters);
        if (key == null) {
            return;
        }
        headerCache.remove(key);
        summaryCache.remove(key);
        fragmentCache.remove(key);
        legacyCatalogCache.remove(key);
    }

    private SourceSchemaSummary cloneSummary(SourceSchemaSummary source) {
        SourceSchemaSummary summary = new SourceSchemaSummary(source.getSchemaName());
        summary.setGrantorSchema(source.isGrantorSchema());
        summary.setLoadState(source.getLoadState());
        summary.setSelected(source.isSelected());
        return summary;
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

    private String buildKey(ConnParameters parameters) {
        if (parameters == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(parameters.getDatabaseType() == null ? 0 : parameters.getDatabaseType().getID());
        builder.append('|').append(nullSafe(parameters.getHost()));
        builder.append('|').append(parameters.getPort());
        builder.append('|').append(nullSafe(parameters.getDbName()).toUpperCase(Locale.US));
        builder.append('|').append(nullSafe(parameters.getConUser()).toUpperCase(Locale.US));
        builder.append('|').append(nullSafe(parameters.getConName()).toUpperCase(Locale.US));
        return builder.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
