package edu.wpi.first.wpilib.repositories.offline;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Input caching is difficult")
public abstract class OfflineDownloadTask extends DefaultTask {
    @OutputDirectory
    public abstract DirectoryProperty getOfflineDirectory();

    @Input
    public abstract ListProperty<Configuration> getExplicitConfigurations();

    @Optional
    @Input
    public abstract Property<Boolean> getIncludeBuildscriptDependencies();

    private MavenXpp3Reader pomReader = new MavenXpp3Reader();
    private Map<ComponentIdentifier, Set<File>> components = new HashMap<>();
    private Set<File> poms = new HashSet<>();
    private Set<File> collectedPoms = new HashSet<>();

    private void addToFileMap(ComponentIdentifier componentId, File file) {
        if (!components.containsKey(componentId)) {
            components.put(componentId, new HashSet<>());
        }
        components.get(componentId).add(file);
    }

    @TaskAction
    public void execute() throws IOException {
        Collection<Configuration> configurations = new ArrayList<>();
        Collection<Configuration> explicitConfigurations = getExplicitConfigurations().getOrNull();
        if (explicitConfigurations == null || explicitConfigurations.size() == 0) {
            explicitConfigurations = getProject().getConfigurations();
        }
        configurations.addAll(explicitConfigurations);

        if (getIncludeBuildscriptDependencies().getOrElse(true)) {
            configurations.addAll(getProject().getBuildscript().getConfigurations());
        }

        getFiles(configurations);

        File rootDirectory = getOfflineDirectory().get().getAsFile();
        for (Map.Entry<ComponentIdentifier, Set<File>> files : components.entrySet()) {
            File directory = getModuleDirectory((ModuleComponentIdentifier) files.getKey(), rootDirectory);
            directory.mkdirs();
            for (File file : files.getValue()) {
                File output = new File(directory, file.getName());
                getLogger().info(String.format("Copying %s to %s", file, output));
                Files.copy(file.toPath(), output.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void getFiles(Collection<Configuration> configurations) {
        for (Configuration configuration : configurations) {
            if (!configuration.isCanBeResolved()) {
                getLogger().debug(String.format("Configuration %s skipped due to not being resolvable", configuration));
                continue;
            }
            resolveConfiguration(configuration);
        }
    }

    private void resolveConfiguration(Configuration configuration) {
        getLogger().info(String.format("Resolving configuration %s", configuration));
        for (Dependency dependency : configuration.getAllDependencies()) {
            if (!(dependency instanceof ExternalModuleDependency)) {
                getLogger().debug(String.format("Dependency %s skipped due to not being an ExternalModuleDependency", dependency));
                continue;
            }

            getLogger().info(String.format("Resolving dependency %s", dependency));

            List<Dependency> newDependencies = new ArrayList<>();

            Configuration detached = getProject().getConfigurations().detachedConfiguration(dependency);
            // Resolve all primary artifacts
            for (ResolvedArtifact artifact : detached.getResolvedConfiguration().getResolvedArtifacts()) {
                getLogger().info(String.format("Resolved artifact %s", artifact));
                artifact.getModuleVersion().getId();
                if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier) {
                    newDependencies.add(getProject().getDependencies()
                            .create(artifact.getId().getComponentIdentifier().toString()));
                }
                if (artifact.getFile().getName().toLowerCase().endsWith(".pom")) {
                    poms.add(artifact.getFile());
                }
                addToFileMap(artifact.getId().getComponentIdentifier(), artifact.getFile());
            }

            detached = getProject().getConfigurations()
                    .detachedConfiguration(newDependencies.toArray(new Dependency[newDependencies.size()]));

            // Resolve all extra files
            ArtifactView view = detached.getIncoming().artifactView(viewConfig -> {
                viewConfig.withVariantReselection();
                viewConfig.setLenient(true);
                viewConfig.attributes(attributes -> {
                    attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                            getProject().getObjects().named(DocsType.class, "all-files"));
                });
            });

            for (ResolvedArtifactResult result : view.getArtifacts()) {
                getLogger().info(String.format("Resolved artifact %s", result));
                if (result.getFile().getName().toLowerCase().endsWith(".pom")) {
                    poms.add(result.getFile());
                }
                addToFileMap(result.getId().getComponentIdentifier(), result.getFile());
            }

            // Resolve platform files
            view = detached.getIncoming().artifactView(viewConfig -> {
                viewConfig.withVariantReselection();
                viewConfig.setLenient(true);
                viewConfig.attributes(attributes -> {
                    attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                            getProject().getObjects().named(DocsType.class, "platform-metadata"));
                });
            });

            for (ResolvedArtifactResult result : view.getArtifacts()) {
                getLogger().info(String.format("Resolved artifact %s", result));
                if (result.getFile().getName().toLowerCase().endsWith(".pom")) {
                    poms.add(result.getFile());
                }
                addToFileMap(result.getId().getComponentIdentifier(), result.getFile());
            }
        }

        // Load dependent poms while making sure to not go infinite
        while (!poms.isEmpty()) {
            poms.removeAll(collectedPoms);
            for (File pom : new ArrayList<>(poms)) {
                collectedPoms.add(pom);
                collectPoms(pom);
            }
        }
    }

    private void collectAlreadyDetached(Dependency dependency, boolean skipMainArtifacts) {
        getLogger().info(String.format("Resolving extra dependency %s", dependency));
        Configuration detached = getProject().getConfigurations().detachedConfiguration(dependency);
        // // Resolve configuration
        if (!skipMainArtifacts) {
            for (ResolvedArtifact artifact : detached.getResolvedConfiguration().getResolvedArtifacts()) {
                getLogger().info(String.format("Resolved extra artifact %s", artifact));
                if (artifact.getFile().getName().toLowerCase().endsWith(".pom")) {
                    poms.add(artifact.getFile());
                }
                addToFileMap(artifact.getId().getComponentIdentifier(), artifact.getFile());
            }
        }

        // Resolve poms and metadata
        ArtifactView view = detached.getIncoming().artifactView(viewConfig -> {
            viewConfig.withVariantReselection();
            viewConfig.setLenient(true);
            viewConfig.attributes(attributes -> {
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                        getProject().getObjects().named(DocsType.class, "platform-metadata"));
            });
        });

        boolean found = false;
        for (ResolvedArtifactResult result : view.getArtifacts()) {
            getLogger().info(String.format("Resolved extra artifact %s", result));
            found = true;
            if (result.getFile().getName().toLowerCase().endsWith(".pom")) {
                poms.add(result.getFile());
            }
            addToFileMap(result.getId().getComponentIdentifier(), result.getFile());
        }

        if (!found) {
            getLogger().info("No extra artifacts resolved. Attempting configuration hack");
            detached = getProject().getConfigurations().detachedConfiguration(dependency);
            detached.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                        getProject().getObjects().named(DocsType.class, "platform-metadata"));

            view = detached.getIncoming().artifactView(viewConfig -> {
                        viewConfig.withVariantReselection();
                        viewConfig.setLenient(true);
                        viewConfig.attributes(attributes -> {
                            attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                                    getProject().getObjects().named(DocsType.class, "platform-metadata"));
                        });
                    });

                    for (ResolvedArtifactResult result : view.getArtifacts()) {
                        getLogger().info(String.format("Resolved extra artifact %s", result));
                        if (result.getFile().getName().toLowerCase().endsWith(".pom")) {
                            poms.add(result.getFile());
                        }
                        addToFileMap(result.getId().getComponentIdentifier(), result.getFile());
                    }
        }
    }

    private void collectPoms(File pom) {
        getLogger().info(String.format("Resolving extra pom %s", pom));
        try (FileReader reader = new FileReader(pom)) {
            Model pomModel = pomReader.read(reader);
            Parent parent = pomModel.getParent();
            if (parent != null) {
                // Resolve the parent
                Dependency dep = getProject().getDependencies()
                        .create(parent.getGroupId() + ":" + parent.getArtifactId() + ":" + parent.getVersion());
                collectAlreadyDetached(dep, false);
            }

            DependencyManagement management = pomModel.getDependencyManagement();
            if (management != null) {
                for (org.apache.maven.model.Dependency dependency : management.getDependencies()) {

                    if (!"pom".equals(dependency.getType()) || !"import".equals(dependency.getScope())) {
                        continue;
                    }
                    String id = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
                            + dependency.getVersion();
                    Dependency dep = getProject().getDependencies()
                            .create(id);
                    collectAlreadyDetached(dep, true);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected File getModuleDirectory(ModuleComponentIdentifier componentId, File rootDirectory) {
        return new File(rootDirectory, componentId.getGroup().replace(".", "/") + "/" + componentId.getModule() + "/"
                + componentId.getVersion());
    }
}
