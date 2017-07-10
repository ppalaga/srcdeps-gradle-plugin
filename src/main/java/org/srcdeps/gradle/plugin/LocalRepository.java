package org.srcdeps.gradle.plugin;

import java.nio.file.Path;

public interface LocalRepository {

    boolean contains(String groupId, String artifactId, String version);

    Path getRootDirectory();

}
