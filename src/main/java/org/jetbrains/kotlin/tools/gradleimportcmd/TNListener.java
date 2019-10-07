package org.jetbrains.kotlin.tools.gradleimportcmd;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.tools.testutils.OutputPrinterUtilsKt;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class TNListener implements ExternalSystemTaskNotificationListener {

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {

    }

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id) {
        OutputPrinterUtilsKt.printMessage("Start external system task " + id, null, null);
    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        OutputPrinterUtilsKt.printMessage(event.getDescription(), null, null);
    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        OutputPrinterUtilsKt.printMessage(text, null, null);
        GradleModelBuilderOverheadContainer.reportOutput(text);
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {
        OutputPrinterUtilsKt.printMessage("End external system task " + id, null, null);
    }

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        OutputPrinterUtilsKt.printMessage("Successfully finished external system task" + id, null, null);
    }

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
        OutputPrinterUtilsKt.printException(e);
    }

    @Override
    public void beforeCancel(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onCancel(@NotNull ExternalSystemTaskId id) {
        System.out.println("Cancelled " + id);
    }

}

