package edu.wpi.first.wpilib.repositories;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.PublishingExtension;

import edu.wpi.first.wpilib.repositories.offline.OfflineDownloaderExtension;
import edu.wpi.first.wpilib.repositories.offline.OfflineDownloaderPlugin;

public class WPILibRepositoriesPluginExtension {

    private final Property<String> mavenLocalDevelopmentUrl;
    private final Property<String> mavenLocalReleaseUrl;
    private final Property<String> mavenRemoteDevelopmentUrl;
    private final Property<String> mavenRemoteReleaseUrl;

    @Inject
    public WPILibRepositoriesPluginExtension(Project project) {
        String remoteBase = "https://frcmaven.wpi.edu/artifactory/";
        String localBase = System.getProperty("user.home") + "/releases/maven/";
        String devExtension = "development";
        String releaseExtension = "release";
        mavenLocalDevelopmentUrl = project.getObjects().property(String.class);
        mavenLocalDevelopmentUrl.set(localBase + devExtension);
        mavenLocalReleaseUrl = project.getObjects().property(String.class);
        mavenLocalReleaseUrl.set(localBase + releaseExtension);
        mavenRemoteDevelopmentUrl = project.getObjects().property(String.class);
        mavenRemoteDevelopmentUrl.set(remoteBase + devExtension);
        mavenRemoteReleaseUrl = project.getObjects().property(String.class);
        mavenRemoteReleaseUrl.set(remoteBase + releaseExtension);
    }

    private void addInternalLocalPublishing(Project project, Property<String> urlProperty, String name) {
        project.getPluginManager().withPlugin("maven-publish", plugin -> {
            PublishingExtension ext = project.getExtensions().getByType(PublishingExtension.class);
            ext.getRepositories().maven(repo -> {
                repo.setName(name);
                repo.setUrl(urlProperty);
            });
        });
    }

    public Property<String> getMavenLocalDevelopmentUrl() {
        return mavenLocalDevelopmentUrl;
    }

    public Property<String> getMavenLocalReleaseUrl() {
        return mavenLocalReleaseUrl;
    }

    public Property<String> getMavenRemoteDevelopmentUrl() {
        return mavenRemoteDevelopmentUrl;
    }

    public Property<String> getMavenRemoteReleaseUrl() {
        return mavenRemoteReleaseUrl;
    }

    public void addLocalDevelopmentPublishing(Project project) {
        addInternalLocalPublishing(project, mavenLocalDevelopmentUrl, "Local-Development-Publishing");
    }

    public void addLocalReleasePublishing(Project project) {
        addInternalLocalPublishing(project, mavenLocalReleaseUrl, "Local-Release-Publishing");
    }

    private void addRepositoryInternal(Project project, Action<MavenArtifactRepository> configureAction, Property<String> url, String name) {
        project.getRepositories().maven(repo -> {
            repo.setName(name);
            repo.setUrl(url);
            if (configureAction != null) {
                configureAction.execute(repo);
            }
        });
    }

    public void addLocalReleaseRepository(Project project) {
        addLocalReleaseRepository(project, null);
    }

    public void addLocalReleaseRepository(Project project, Action<MavenArtifactRepository> configureAction) {
        addRepositoryInternal(project, configureAction, mavenLocalReleaseUrl, "Local-Release");
    }

    public void addLocalDevelopmentRepository(Project project) {
        addLocalDevelopmentRepository(project, null);
    }

    public void addLocalDevelopmentRepository(Project project, Action<MavenArtifactRepository> configureAction) {
        addRepositoryInternal(project, configureAction, mavenLocalDevelopmentUrl, "Local-Development");
    }

    public void addRemoteReleaseRepository(Project project) {
        addRemoteReleaseRepository(project, null);
    }

    public void addRemoteReleaseRepository(Project project, Action<MavenArtifactRepository> configureAction) {
        addRepositoryInternal(project, configureAction, mavenRemoteReleaseUrl, "Remote-Release");
    }

    public void addRemoteDevelopmentRepository(Project project) {
        addRemoteDevelopmentRepository(project, null);
    }

    public void addRemoteDevelopmentRepository(Project project, Action<MavenArtifactRepository> configureAction) {
        addRepositoryInternal(project, configureAction, mavenRemoteDevelopmentUrl, "Remote-Development");
    }

    public void addAllDevelopmentRepositories(Project project) {
        addLocalDevelopmentPublishing(project);
        addLocalDevelopmentRepository(project);
        addRemoteDevelopmentRepository(project);
    }

    public void addAllReleaseRepositories(Project project) {
        addLocalReleasePublishing(project);
        addLocalReleaseRepository(project);
        addRemoteReleaseRepository(project);
    }

    public OfflineDownloaderExtension enableOfflineDownloader(Project project) {
        project.getPluginManager().apply(OfflineDownloaderPlugin.class);
        return project.getExtensions().getByType(OfflineDownloaderExtension.class);
    }
}
