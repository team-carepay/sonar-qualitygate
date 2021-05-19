package com.carepay.sonar.qualitygate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;

import com.carepay.sonar.qualitygate.util.ResourceFetcher;
import com.carepay.sonar.qualitygate.util.URLOpener;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin, which waits for SonarQube analysis to finish, optionally breaking the build if
 * the quality gate is not OK.
 */
public class SonarQualityGatePlugin implements Plugin<Project> {
    private static final int NR_OF_RETRIES = 30;
    private static final long WAIT_TIME_MILLIS = 5000L;

    private final ResourceFetcher resourceFetcher;

    @Inject
    public SonarQualityGatePlugin() {
        this(new ResourceFetcher(new URLOpener.Default()));
    }

    protected SonarQualityGatePlugin(final ResourceFetcher resourceFetcher) {
        this.resourceFetcher = resourceFetcher;
    }

    @SuppressWarnings("unchecked")
    public void apply(Project project) {
        project.getTasksByName("sonarqube", true).forEach(task -> {
            task.doLast(s -> {
                final Map<String, Object> properties = (Map<String, Object>) s.getInputs().getProperties().get("properties");
                if (properties == null) {
                    throw new IllegalStateException("Sonarqube task properties missing");
                }
                final String token = String.valueOf(properties.get("sonar.login"));
                s.getLogger().info("checking quality gate");
                final Properties props = getTaskProperties(new File(project.getBuildDir(), "sonar/report-task.txt"));
                final String analysisId = waitForAnalysisId(props.getProperty("ceTaskUrl"), token);
                s.getLogger().info("analysis id {}", analysisId);
                final String status = getQualityGateStatus(analysisId, token);
                s.getLogger().info("quality gate project status is {}", status);
                if (!"OK".equals(status)) {
                    throw new IllegalStateException("Quality gate failed: " + status);
                }
            });
        });
    }

    protected Properties getTaskProperties(File reportFile) {
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

    @SuppressWarnings("unchecked")
    protected String getQualityGateStatus(final String analysisId, final String sonarToken) {
        final URL url = URLOpener.create("https://sonarcloud.io/api/qualitygates/project_status?analysisId=" + analysisId);
        final Map<String, Object> responseJson = resourceFetcher.queryJsonBasicAuth(url, sonarToken, "");
        final Map<String, String> statusJson = (Map<String, String>) responseJson.get("projectStatus");
        if (statusJson == null) {
            throw new IllegalStateException("Invalid JSON response: missing 'projectStatus'");
        }
        return statusJson.get("status");
    }

    @SuppressWarnings("unchecked")
    protected String waitForAnalysisId(final String taskUrlString, final String sonarToken) {
        final URL ceTaskUrl = URLOpener.create(taskUrlString);
        try {
            for (int i = 0; i < NR_OF_RETRIES; i++) {
                final Map<String, Object> responseJson = resourceFetcher.queryJsonBasicAuth(ceTaskUrl, sonarToken, "");
                final Map<String, String> taskJson = (Map<String, String>) responseJson.get("task");
                if (taskJson == null) {
                    throw new IllegalStateException("Invalid JSON response: missing 'task'");
                }
                if (!"SUCCESS".equals(taskJson.get("status"))) {
                    Thread.sleep(WAIT_TIME_MILLIS);
                } else {
                    return taskJson.get("analysisId");
                }
            }
            throw new IllegalStateException("Unable to get analysis id (after many retries)");
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}