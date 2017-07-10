package org.srcdeps.gradle.plugin;

import java.nio.file.Path;

import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.srcdeps.core.SrcVersion;
import org.srcdeps.core.fs.BuildDirectoriesManager;
import org.srcdeps.core.fs.PathLocker;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

public class Wiring {
    private static Injector injector;

    public static Injector getInjector() {
        return injector;
    }

    public static void init(final Project project) {

        final Path srcdepsYamlPath = project.getRootDir().toPath().resolve("srcdeps.yaml");

        Gradle gradle = project.getGradle();
        if (!(gradle instanceof GradleInternal)) {
            throw new RuntimeException(String.format("Expected %s, but found %s", GradleInternal.class.getName(),
                    gradle.getClass().getName()));
        }

        ServiceRegistry services = ((GradleInternal) gradle).getServices();
        if (!(services instanceof DefaultServiceRegistry)) {
            throw new RuntimeException(String.format("Expected %s, but found %s",
                    DefaultServiceRegistry.class.getName(), services.getClass().getName()));
        }

        ArtifactRepository repo = project.getRepositories()
                .findByName(org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME);
        if (!(repo instanceof MavenArtifactRepository)) {
            throw new RuntimeException(
                    String.format("Expected %s, but found %s", MavenArtifactRepository.class.getName(), repo));
        }
        final LocalRepository localRepository = new MavenLocalRepository(((MavenArtifactRepository) repo).getUrl());

        final Path scrdepsDir = localRepository.getRootDirectory().getParent().resolve("srcdeps");
        final PathLocker<SrcVersion> pathLocker = new PathLocker<>();
        final BuildDirectoriesManager buildDirectoriesManager = new BuildDirectoriesManager(scrdepsDir, pathLocker);

        ClassLoader classloader = Wiring.class.getClassLoader();
        final Module spaceModule = new SpaceModule(new URLClassSpace(classloader));
        Module wrappedWiremodule = new Module() {
            @Override
            public void configure(Binder binder) {

                spaceModule.configure(binder);

                binder.bind(Path.class).annotatedWith(Names.named(ConfigurationService.SRCDEPS_YAML_PATH))
                        .toInstance(srcdepsYamlPath);
                binder.bind(LocalRepository.class).toInstance(localRepository);
                binder.bind(new TypeLiteral<PathLocker<SrcVersion>>() {
                }).toInstance(pathLocker);
                binder.bind(BuildDirectoriesManager.class).toInstance(buildDirectoriesManager);
            }
        };
        final Module wireModule = new WireModule(wrappedWiremodule);
        injector = Guice.createInjector(wireModule);
    }
}
