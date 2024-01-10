package edu.wpi.first.wpilib.repositories;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class WPILibRepositoriesPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getExtensions().create("wpilibRepositories", WPILibRepositoriesPluginExtension.class, project);
    }
}
