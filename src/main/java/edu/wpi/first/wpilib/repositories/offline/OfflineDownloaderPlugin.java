package edu.wpi.first.wpilib.repositories.offline;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class OfflineDownloaderPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getExtensions().create("wpilibOffline", OfflineDownloaderExtension.class);
        project.getDependencies().getComponents().all(DirectMetadataAccessVariantRule.class);
    }

}
