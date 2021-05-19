package com.carepay.sonar.qualitygate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import com.carepay.sonar.qualitygate.util.ResourceFetcher;
import com.carepay.sonar.qualitygate.util.URLOpener;
import com.github.cliftonlabs.json_simple.JsonObject;
import org.gradle.api.Action;
import org.gradle.api.Task;

public class SonarQualityGateCheckAction implements Action<Task> {
    private static final int NR_OF_RETRIES = 30;
    private static final long WAIT_TIME_MILLIS = 5000L;

    private final ResourceFetcher resourceFetcher;

    public SonarQualityGateCheckAction() {
        this(new ResourceFetcher(new URLOpener.Default()));
    }

    protected SonarQualityGateCheckAction(ResourceFetcher resourceFetcher) {
        this.resourceFetcher = resourceFetcher;
    }

    @Override
    public void execute(Task task) {
        final Map<String, Object> properties = (Map<String, Object>) task.getInputs().getProperties().get("properties");
        if (properties == null) {
            throw new IllegalStateException("Sonarqube task properties missing");
        }
        final String token = String.valueOf(properties.get("sonar.login"));
        task.getLogger().info("checking quality gate");
        final Properties props = getTaskProperties(new File(task.getProject().getBuildDir(), "sonar/report-task.txt"));
        final String analysisId = waitForAnalysisId(props.getProperty("ceTaskUrl"), token);
        task.getLogger().info("analysis id {}", analysisId);
        final String status = getQualityGateStatus(analysisId, token);
        task.getLogger().info("quality gate project status is {}", status);
        if (!"OK".equals(status)) {
            throw new IllegalStateException("Quality gate failed: " + status);
        }
    }

    protected Properties getTaskProperties(final File reportFile) {
        if (!reportFile.exists()) {
            throw new IllegalStateException("Report file not found: " + reportFile);
        }
        final Properties props = new Properties();
        try (final FileInputStream fis = new FileInputStream(reportFile)) {
            props.load(fis);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return props;
    }

    protected String getQualityGateStatus(final String analysisId, final String sonarToken) {
        final URL url = URLOpener.create("https://sonarcloud.io/api/qualitygates/project_status?analysisId=" + analysisId);
        final JsonObject responseJson = resourceFetcher.queryJsonBasicAuth(url, sonarToken, "");
        final JsonObject statusJson = responseJson.getMap(ProjectStatusJsonProperty.projectStatus);
        if (statusJson == null) {
            throw new IllegalStateException("Invalid JSON response: missing 'projectStatus'");
        }
        return statusJson.getString(ProjectStatusJsonProperty.status);
    }

    protected String waitForAnalysisId(final String taskUrlString, final String sonarToken) {
        final URL ceTaskUrl = URLOpener.create(taskUrlString);
        try {
            for (int i = 0; i < NR_OF_RETRIES; i++) {
                final JsonObject responseJson = resourceFetcher.queryJsonBasicAuth(ceTaskUrl, sonarToken, "");
                final JsonObject taskJson = responseJson.getMap(TaskJsonProperty.task);
                if (taskJson == null) {
                    throw new IllegalStateException("Invalid JSON response: missing 'task'");
                }
                if (!"SUCCESS".equals(taskJson.getString(TaskJsonProperty.status))) {
                    Thread.sleep(WAIT_TIME_MILLIS);
                } else {
                    return taskJson.getString(TaskJsonProperty.analysisId);
                }
            }
            throw new IllegalStateException("Unable to get analysis id (after many retries)");
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
