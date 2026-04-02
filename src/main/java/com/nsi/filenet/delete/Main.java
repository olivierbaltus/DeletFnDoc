package com.nsi.filenet.delete;

import com.filenet.api.collection.IndependentObjectSet;
import com.filenet.api.constants.RefreshMode;
import com.filenet.api.core.Connection;
import com.filenet.api.core.Document;
import com.filenet.api.core.Domain;
import com.filenet.api.core.Factory;
import com.filenet.api.core.IndependentObject;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.VersionSeries;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.UserContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.security.auth.Subject;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
        name = "filenet-wsi-delete",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Deletes FileNet documents selected by a CE SQL query over WSI"
)
public class Main implements Callable<Integer> {

    @Option(names = "--uri", required = true, description = "WSI URI, e.g. https://host:port/wsi/FNCEWS40MTOM")
    private String uri;

    @Option(names = "--username", required = true, description = "FileNet / directory username")
    private String username;

    @Option(names = "--password", required = true, interactive = true, arity = "0..1", description = "Password")
    private char[] password;

    @Option(names = "--object-store", required = true, description = "Object Store name")
    private String objectStoreName;

    @Option(names = "--query", required = true, description = "CE SQL query returning Document objects, e.g. SELECT * FROM Document WHERE ...")
    private String query;

    @Option(names = "--dry-run", description = "Lists matching documents without deleting them")
    private boolean dryRun;

    @Option(names = "--delete-all-versions", description = "Deletes the whole VersionSeries instead of only the matched version")
    private boolean deleteAllVersions;

    @Option(names = "--max", defaultValue = "0", description = "Maximum number of matched rows to process. 0 = no limit")
    private int max;

    @Option(names = "--page-size", defaultValue = "200", description = "Search page size")
    private int pageSize;

    @Option(names = "--continue-on-error", description = "Continue processing other documents if one deletion fails")
    private boolean continueOnError;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        validateArguments();

        UserContext userContext = UserContext.get();
        Subject subject = null;

        try {
            Connection connection = Factory.Connection.getConnection(uri);
            subject = UserContext.createSubject(connection, username, new String(password), null);
            userContext.pushSubject(subject);

            Domain domain = Factory.Domain.fetchInstance(connection, null, null);
            ObjectStore os = Factory.ObjectStore.fetchInstance(domain, objectStoreName, null);

            System.out.println("Connected to ObjectStore='" + os.get_Name() + "' using WSI URI='" + uri + "'.");
            System.out.println("Mode=" + (dryRun ? "DRY-RUN" : "DELETE") + ", deleteAllVersions=" + deleteAllVersions + ", max=" + max + ".");
            System.out.println("Query: " + query);

            SearchSQL sql = new SearchSQL(query);
            SearchScope scope = new SearchScope(os);
            PropertyFilter filter = null;
            IndependentObjectSet objects = scope.fetchObjects(sql, pageSize, filter, Boolean.TRUE);

            int matched = 0;
            int deleted = 0;
            int failed = 0;
            Set<String> deletedVersionSeries = new HashSet<>();

            for (Object entry : objects) {
                if (!(entry instanceof IndependentObject)) {
                    continue;
                }

                IndependentObject io = (IndependentObject) entry;
                if (!(io instanceof Document)) {
                    System.err.println("Skipping non-Document result of type: " + io.getClass().getName());
                    continue;
                }

                Document doc = (Document) io;
                matched++;

                String id = safeId(doc);
                String title = safeTitle(doc);
                String versionSeriesId = safeVersionSeriesId(doc);

                System.out.printf("[%d] Matched Id=%s, VersionSeries=%s, Title=%s%n", matched, id, versionSeriesId, title);

                if (max > 0 && matched > max) {
                    System.out.println("Reached max limit. Stopping.");
                    break;
                }

                if (dryRun) {
                    continue;
                }

                try {
                    if (deleteAllVersions) {
                        if (versionSeriesId == null || versionSeriesId.isBlank()) {
                            throw new IllegalStateException("VersionSeries is null for document Id=" + id);
                        }
                        if (deletedVersionSeries.contains(versionSeriesId)) {
                            System.out.println("  -> already deleted/skipped VersionSeries=" + versionSeriesId);
                            continue;
                        }

                        VersionSeries vs = doc.get_VersionSeries();
                        vs.delete();
                        vs.save(RefreshMode.NO_REFRESH);
                        deletedVersionSeries.add(versionSeriesId);
                        deleted++;
                        System.out.println("  -> deleted VersionSeries=" + versionSeriesId);
                    } else {
                        Document toDelete = Factory.Document.getInstance(os, null, doc.get_Id());
                        toDelete.delete();
                        toDelete.save(RefreshMode.NO_REFRESH);
                        deleted++;
                        System.out.println("  -> deleted document version Id=" + id);
                    }
                } catch (Exception e) {
                    failed++;
                    System.err.println("  -> ERROR deleting Id=" + id + ": " + e.getMessage());
                    if (!continueOnError) {
                        throw e;
                    }
                }
            }

            System.out.println();
            System.out.println("Summary");
            System.out.println("-------");
            System.out.println("Matched : " + matched);
            System.out.println("Deleted : " + deleted);
            System.out.println("Failed  : " + failed);
            System.out.println("Dry-run : " + dryRun);

            return failed > 0 ? 2 : 0;
        } catch (EngineRuntimeException e) {
            System.err.println("FileNet error: " + e.getExceptionCode() + " - " + e.getMessage());
            return 10;
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            return 11;
        } finally {
            if (subject != null) {
                userContext.popSubject();
            }
            if (password != null) {
                for (int i = 0; i < password.length; i++) {
                    password[i] = '\0';
                }
            }
        }
    }

    private void validateArguments() {
        if (!query.toUpperCase().contains("FROM")) {
            throw new IllegalArgumentException("The query does not look like a valid CE SQL statement.");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("--page-size must be > 0");
        }
        if (max < 0) {
            throw new IllegalArgumentException("--max must be >= 0");
        }
    }

    private String safeId(Document doc) {
        try {
            return doc.get_Id().toString();
        } catch (Exception e) {
            return "<unknown>";
        }
    }

    private String safeTitle(Document doc) {
        try {
            return doc.getProperties().isPropertyPresent("DocumentTitle")
                    ? doc.getProperties().getStringValue("DocumentTitle")
                    : "<not-fetched>";
        } catch (Exception e) {
            return "<error-reading-title>";
        }
    }

    private String safeVersionSeriesId(Document doc) {
        try {
            VersionSeries vs = doc.get_VersionSeries();
            return vs != null && vs.get_Id() != null ? vs.get_Id().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
