package org.srcdeps.gradle.plugin;

import org.gradle.api.GradleException
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.RepositoryHandler;

public class SrcdepsGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {


        project.afterEvaluate {
            org.srcdeps.gradle.plugin.Wiring.init(project);

            project.logger.warn("afterEvaluate --- ")
            project.configurations.findAll { it.state != Configuration.State.UNRESOLVED }.each { configuration ->
                project.logger.warn("RESOLVED --- ")
                new SrcdepsResolver(project, configuration).resolveArtifacts()
            }
            def capturedProject = project
            project.configurations.findAll { it.state == Configuration.State.UNRESOLVED }.each { configuration ->
                configuration.incoming.beforeResolve {
                    project.logger.warn("UNRESOLVED --- ")
                    new SrcdepsResolver(project, configuration).resolveArtifacts()
                }
            }
        }

    }

}
