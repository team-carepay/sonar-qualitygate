package com.carepay.sonar.qualitygate;

import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Properties;

import com.carepay.sonar.qualitygate.util.ResourceFetcher;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskInputs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarQualityGateCheckActionTest {

    private ResourceFetcher resourceFetcher;
    private SonarQualityGateCheckAction action;
    private Task task;

    @BeforeEach
    void setUp() {
        resourceFetcher = mock(ResourceFetcher.class);
        action = new SonarQualityGateCheckAction(resourceFetcher);
        Project project = mock(Project.class);
        task = mock(Task.class);
        TaskInputs inputs = mock(TaskInputs.class);
        when(task.getProject()).thenReturn(project);
        when(task.getInputs()).thenReturn(inputs);
        when(task.getLogger()).thenReturn(mock(Logger.class));
        when(project.getBuildDir()).thenReturn(new File("src/test/resources"));
        Properties properties = new Properties();
        properties.setProperty("sonar.login", "1234");
        when(task.getInputs()).thenReturn(inputs);
        when(inputs.getProperties()).thenReturn(Collections.singletonMap("properties", properties));
    }

    @Test
    void execute() throws JsonException, MalformedURLException {
        JsonObject taskPendingJson = (JsonObject) Jsoner.deserialize(new InputStreamReader(getClass().getResourceAsStream("/task-pending.json")));
        JsonObject taskSuccessJson = (JsonObject) Jsoner.deserialize(new InputStreamReader(getClass().getResourceAsStream("/task-success.json")));
        when(resourceFetcher.queryJsonBasicAuth(eq(new URL("https://sonarcloud.io/api/ce/task?id=AXmHBwTu2SYq9eoiyJWZ")),eq("1234"), eq(""))).thenReturn(taskPendingJson, taskSuccessJson);
        JsonObject statusJson = (JsonObject) Jsoner.deserialize(new InputStreamReader(getClass().getResourceAsStream("/project-status-ok.json")));
        when(resourceFetcher.queryJsonBasicAuth(eq(new URL("https://sonarcloud.io/api/qualitygates/project_status?analysisId=AXmEqYqP01j0lbdsnsf7")),eq("1234"), eq(""))).thenReturn(statusJson);
        action.execute(task);
    }

    @Test
    void executeFailed() throws JsonException, MalformedURLException {
        JsonObject taskPendingJson = (JsonObject) Jsoner.deserialize(new InputStreamReader(getClass().getResourceAsStream("/task-success.json")));
        JsonObject taskSuccessJson = (JsonObject) Jsoner.deserialize(new InputStreamReader(getClass().getResourceAsStream("/task-success.json")));
        when(resourceFetcher.queryJsonBasicAuth(eq(new URL("https://sonarcloud.io/api/ce/task?id=AXmHBwTu2SYq9eoiyJWZ")),eq("1234"), eq(""))).thenReturn(taskPendingJson, taskSuccessJson);
        JsonObject statusJson = (JsonObject) Jsoner.deserialize(new InputStreamReader(getClass().getResourceAsStream("/project-status-failed.json")));
        when(resourceFetcher.queryJsonBasicAuth(eq(new URL("https://sonarcloud.io/api/qualitygates/project_status?analysisId=AXmEqYqP01j0lbdsnsf7")),eq("1234"), eq(""))).thenReturn(statusJson);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> action.execute(task));
        assertThat(e.getMessage()).isEqualTo("Quality gate failed: FAILED");
    }
}