package org.srcdeps.gradle.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DisconnectedDescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DownloadedIvyModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter;
import org.gradle.api.logging.Logger;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.ivy.IvyDescriptorArtifact;
import org.gradle.ivy.IvyModule;
import org.srcdeps.core.SrcVersion;

public class SrcdepsResolver {

    private class DependencyAction implements Action<DependencyResolveDetails> {

        @Override
        public void execute(DependencyResolveDetails dep) {
            ModuleVersionSelector requested = dep.getRequested();
            final String version = requested.getVersion();
            if (SrcVersion.isSrcVersion(version)
                    // && is not available yet
            ) {
                final SrcdepsService srcdepsService = Wiring.getInjector().getInstance(SrcdepsService.class);
                srcdepsService.buildIfNecessary(requested.getGroup(), requested.getName(), version);
            }
        }

    }

    private final Project project;
    private final Logger log;
    private final Configuration configuration;
    private final Action<DependencyResolveDetails> dependencyAction = new DependencyAction();

    public SrcdepsResolver(Project project, Configuration configuration) {
        super();
        this.project = project;
        this.log = project.getLogger();
        this.configuration = configuration;
    }

    public void resolveArtifacts() {

        log.warn("---- resolveArtifacts()");
        if (configuration.isCanBeResolved()) {
            log.warn("---- after configuration.isCanBeResolved()");
            configuration.resolutionStrategy(new Action<ResolutionStrategy>() {
                @Override
                public void execute(ResolutionStrategy strategy) {
                    strategy.eachDependency(dependencyAction);
                }
            });
        } else {
            log.warn("Configuration {} cannot be resolved", configuration);
        }
//
//
//        Set<ComponentArtifactsResult> ivyDescriptors = getIvyDescriptorsForConfiguration(configuration.copy());
//        for (ComponentArtifactsResult component : ivyDescriptors) {
//            File ivyFile = getIvyArtifact(component);
//            if (ivyFile != null) {
//                ModuleDependency targetDependency = findRelatedDependency(component);
//
//                log.error("Dependency [" + targetDependency + "] has ivy file [" + ivyFile + "], parsing");
//
//                List<Artifact> ivyDefinedArtifacts = readArtifactsSet(ivyFile, project);
//                Set<DependencyArtifact> depArtifacts = targetDependency.getArtifacts();
//                Set<Artifact> toAdd = new HashSet<>();
//                Iterator<DependencyArtifact> i = depArtifacts.iterator();
//                while (i.hasNext()) {
//                    DependencyArtifact da = i.next();
//                    String daName = "" + da.getName() + "." + da.getType() + "".toString();
//                    List<Artifact> candidates = new ArrayList<>();
//                    log.error("processing dependency artifact [" + daName + "]");
//
//                    Artifact exactEqual = null;
//                    for (Artifact it : ivyDefinedArtifacts) {
//                        if (daName.equals(it.getArtifactName().toString())) {
//                            exactEqual = it;
//                            break;
//                        } else {
//                            if (it.getArtifactName().toString().matches(daName)) {
//                                candidates.add(it);
//                            }
//                        }
//                    }
//
//                    log.error("got exact equal [" + exactEqual + "] and candidates [" + candidates + "]");
//
//                    if (exactEqual == null && candidates.size() > 0) {
//                        i.remove();
//                        toAdd.addAll(candidates);
//                    }
//                }
//
//                for (Artifact artifact : toAdd) {
//                    log.error("injecting new artifact [" + artifact.toString() + "]");
//                    final IvyArtifactName artifactName = artifact.getArtifactName();
//                    targetDependency.addArtifact(new DefaultDependencyArtifact(artifactName.getName(), null,
//                            artifactName.getExtension(), null, null));
//                }
//
//            }
//        }

    }

    private List<Artifact> readArtifactsSet(File ivyFile, Project project) {
        log.error("Parsing ivy file [" + ivyFile + "]");
        DefaultImmutableModuleIdentifierFactory factory = new DefaultImmutableModuleIdentifierFactory();
        return new DownloadedIvyModuleDescriptorParser(new IvyModuleDescriptorConverter(factory), factory)
                .parseMetaData(new DisconnectedDescriptorParseContext(), ivyFile).getDescriptor().getArtifacts();
    }

    private ModuleDependency findRelatedDependency(ComponentArtifactsResult component) {
        for (Dependency dep : this.configuration.getDependencies()) {
            final String componentId = dep.getGroup() + ":" + dep.getName() + ":" + dep.getVersion();
            if (dep instanceof ModuleDependency && component.getId().getDisplayName().equals(componentId)) {
                return (ModuleDependency) dep;
            }
        }
        return null;
    }

    private boolean hasIvyArtifact(ComponentArtifactsResult component) {
        File ivyFile = getIvyArtifact(component);
        if (ivyFile == null) {
            log.error("No ivy descriptor for component [" + component.getId().getDisplayName() + "].");
            return false;
        }
        return true;
    }

    private File getIvyArtifact(ComponentArtifactsResult component) {
        Set<ArtifactResult> artifacts = component.getArtifacts(IvyDescriptorArtifact.class);
        for (ArtifactResult artifactResult : artifacts) {
            if (artifactResult instanceof ResolvedArtifactResult) {
                return ((ResolvedArtifactResult) artifactResult).getFile();
            }
        }
        return null;
    }

    private Set<ComponentArtifactsResult> getIvyDescriptorsForConfiguration(Configuration configuration) {

        Set<? extends DependencyResult> deps = configuration.getIncoming().getResolutionResult().getAllDependencies();

        List<ComponentIdentifier> componentIds = new ArrayList<>();
        for (DependencyResult dependencyResult : deps) {
            log.error("---- dependencyResult: "+ dependencyResult);
            if (dependencyResult instanceof ResolvedDependencyResult) {
                ComponentIdentifier id = ((ResolvedDependencyResult) dependencyResult).getSelected().getId();
                componentIds.add(id);
            }
        }

        if (componentIds.isEmpty()) {
            log.error("no components found");
            return Collections.emptySet();
        } else {
            log.error("component ids " + componentIds + "");
        }
        return getIvyDescriptorsForComponents(componentIds);
    }

    private Set<ComponentArtifactsResult> getIvyDescriptorsForComponents(List<ComponentIdentifier> componentIds) {
        return project.getDependencies().createArtifactResolutionQuery().forComponents(componentIds)
                .withArtifacts(IvyModule.class, IvyDescriptorArtifact.class).execute().getResolvedComponents();
    }

}
