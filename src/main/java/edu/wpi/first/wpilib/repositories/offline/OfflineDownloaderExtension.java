package edu.wpi.first.wpilib.repositories.offline;

import javax.inject.Inject;

import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

public class OfflineDownloaderExtension {
    private final TaskProvider<OfflineDownloadTask> offlineDownloadTask;

    public TaskProvider<OfflineDownloadTask> getDownloadTask() {
        return offlineDownloadTask;
    }

    @Inject
    public OfflineDownloaderExtension(TaskContainer tasks) {
        offlineDownloadTask = tasks.register("offlineDownload", OfflineDownloadTask.class);
    }
}
