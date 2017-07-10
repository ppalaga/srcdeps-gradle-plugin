package org.srcdeps.gradle.plugin;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.srcdeps.core.config.ScmRepository;

public class MavenLocalRepository implements LocalRepository {
    private final Path rootDirectory;
    private static final String FILE_SCHEME = "file";

    public MavenLocalRepository(Path rootDirectory) {
        super();
        this.rootDirectory = rootDirectory;
    }
    public MavenLocalRepository(URI uri) {
        super();
        if (!FILE_SCHEME.equals(uri.getScheme())) {
            throw new RuntimeException(String.format("Expected scheme %s in %s", FILE_SCHEME, uri.toString()));
        }
        this.rootDirectory = Paths.get(uri);
    }

    public boolean contains(String groupId, String artifactId, String version) {
        final Path groupIdPath = ScmRepository.getIdAsPath(groupId);
        final Path artifactPath = rootDirectory.resolve(groupIdPath).resolve(artifactId).resolve(version);
        // TODO: we check only the directory, we should rather check an artifact
        return Files.exists(artifactPath);
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }
}
