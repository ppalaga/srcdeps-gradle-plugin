package org.srcdeps.gradle.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.BuildException;
import org.srcdeps.core.BuildRequest;
import org.srcdeps.core.BuildService;
import org.srcdeps.core.SrcVersion;
import org.srcdeps.core.config.BuilderIo;
import org.srcdeps.core.config.Configuration;
import org.srcdeps.core.config.ScmRepository;
import org.srcdeps.core.fs.BuildDirectoriesManager;
import org.srcdeps.core.fs.PathLock;
import org.srcdeps.core.shell.IoRedirects;

@Named @Singleton
public class SrcdepsService {
    private static final Logger log = LoggerFactory.getLogger(SrcdepsService.class);

    private static List<String> enhanceBuildArguments(List<String> buildArguments, Path configurationLocation,
            String localRepo) {
        List<String> result = new ArrayList<>();
        for (String arg : buildArguments) {
            if (arg.startsWith("-Dmaven.repo.local=")) {
                /* We won't touch maven.repo.local set in the user's config */
                log.debug("Srcdeps forwards {} to the nested build as set in {}", arg, configurationLocation);
                return buildArguments;
            }
            result.add(arg);
        }

        String arg = "-Dmaven.repo.local=" + localRepo;
        log.debug("Srcdeps forwards {} from the outer Maven build to the nested build", arg);
        result.add(arg);

        return Collections.unmodifiableList(result);
    }

    private final BuildDirectoriesManager buildDirectoriesManager;
    private final BuildService buildService;
    private final ConfigurationService configurationService;
    private final LocalRepository localRepository;

    @Inject
    public SrcdepsService(ConfigurationService configurationService, BuildDirectoriesManager buildDirectoriesManager,
            BuildService buildService, LocalRepository localRepository) {
        super();
        this.configurationService = configurationService;
        this.buildDirectoriesManager = buildDirectoriesManager;
        this.buildService = buildService;
        this.localRepository = localRepository;
    }

    public void buildIfNecessary(String groupId, String artifactId, String version) {
        final Configuration configuration = configurationService.getConfiguration();
        if (!configuration.isSkip() && !localRepository.contains(groupId, artifactId, version)) {
            ScmRepository scmRepo = configurationService.getRepositoryFinder().findRepository(groupId, artifactId, version);
            SrcVersion srcVersion = SrcVersion.parse(version);
            try (PathLock projectBuildDir = buildDirectoriesManager.openBuildDirectory(scmRepo.getIdAsPath(),
                    srcVersion)) {

                /* query the delegate again, because things may have changed since we requested the lock */
                if (localRepository.contains(groupId, artifactId, version)) {
                    return;
                } else {
                    /* no change in the local repo, let's build */
                    BuilderIo builderIo = scmRepo.getBuilderIo();
                    IoRedirects ioRedirects = IoRedirects.builder() //
                            .stdin(IoRedirects.parseUri(builderIo.getStdin())) //
                            .stdout(IoRedirects.parseUri(builderIo.getStdout())) //
                            .stderr(IoRedirects.parseUri(builderIo.getStderr())) //
                            .build();

                    List<String> buildArgs = enhanceBuildArguments(scmRepo.getBuildArguments(),
                            configurationService.getConfigurationLocation(),
                            localRepository.getRootDirectory().toString());

                    BuildRequest buildRequest = BuildRequest.builder() //
                            .projectRootDirectory(projectBuildDir.getPath()) //
                            .scmUrls(scmRepo.getUrls()) //
                            .srcVersion(srcVersion) //
                            .buildArguments(buildArgs) //
                            .timeoutMs(scmRepo.getBuildTimeout().toMilliseconds()) //
                            .skipTests(scmRepo.isSkipTests()) //
                            .forwardProperties(configuration.getForwardProperties()) //
                            .addDefaultBuildArguments(scmRepo.isAddDefaultBuildArguments()) //
                            .verbosity(scmRepo.getVerbosity()) //
                            .ioRedirects(ioRedirects) //
                            .versionsMavenPluginVersion(scmRepo.getMaven().getVersionsMavenPluginVersion())
                            .build();
                    buildService.build(buildRequest);

                    /* check once again if the delegate sees the newly built artifact */
                    if (!localRepository.contains(groupId, artifactId, version)) {
                        log.error(
                                "Srcdeps build succeeded but the artifact {}:{}:{} is still not available in the local repository",
                                groupId, artifactId, version);
                    }
                }

            } catch (BuildException | IOException e) {
                log.error("Srcdeps could not build {}:{}:{}" + groupId, artifactId, version, e);
            }

        }
    }
}
