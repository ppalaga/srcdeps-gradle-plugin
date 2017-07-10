package org.srcdeps.gradle.plugin;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.config.yaml.YamlConfigurationIo;
import org.srcdeps.core.config.Configuration;
import org.srcdeps.core.config.ConfigurationException;
import org.srcdeps.core.config.ScmRepositoryFinder;
import org.srcdeps.core.config.tree.walk.DefaultsAndInheritanceVisitor;
import org.srcdeps.core.config.tree.walk.OverrideVisitor;

@Named
@Singleton
public class ConfigurationService {
    public static final String SRCDEPS_YAML_PATH = "SRCDEPS_YAML_PATH";
    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    @Inject
    public ConfigurationService(@Named(SRCDEPS_YAML_PATH) Path srcdepsYamlPath) {
        super();
        this.configurationLocation = srcdepsYamlPath;

        final Configuration.Builder configBuilder;
        if (Files.exists(srcdepsYamlPath)) {
            log.debug("SrcdepsLocalRepositoryManager using configuration {}", srcdepsYamlPath);
            final String encoding = System.getProperty(Configuration.getSrcdepsEncodingProperty(), "utf-8");
            final Charset cs = Charset.forName(encoding);
            try (Reader r = Files.newBufferedReader(srcdepsYamlPath, cs)) {
                configBuilder = new YamlConfigurationIo().read(r);
            } catch (IOException | ConfigurationException e) {
                throw new RuntimeException(e);
            }
        } else {
            log.warn("Could not locate srcdeps configuration at {}, defaulting to an empty configuration",
                    srcdepsYamlPath);
            configBuilder = Configuration.builder();
        }

        this.configuration = configBuilder //
                .accept(new OverrideVisitor(System.getProperties())) //
                .accept(new DefaultsAndInheritanceVisitor()) //
                .build();

        this.repositoryFinder = new ScmRepositoryFinder(this.configuration);
    }

    private final Configuration configuration;
    private final ScmRepositoryFinder repositoryFinder;
    private final Path configurationLocation;

    public Configuration getConfiguration() {
        return configuration;
    }

    public ScmRepositoryFinder getRepositoryFinder() {
        return repositoryFinder;
    }

    public Path getConfigurationLocation() {
        return configurationLocation;
    }

}
