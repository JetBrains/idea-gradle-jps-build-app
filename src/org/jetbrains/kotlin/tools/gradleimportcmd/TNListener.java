package org.jetbrains.kotlin.tools.gradleimportcmd;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;

public class TNListener implements ExternalSystemTaskNotificationListener {
    @Override
    public void onQueued(@NotNull ExternalSystemTaskId id, String workingDir) {

    }

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {

    }

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        System.out.println(event.getDescription());
    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        System.out.println(text);
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
        e.printStackTrace();
    }

    @Override
    public void beforeCancel(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onCancel(@NotNull ExternalSystemTaskId id) {

    }
}
