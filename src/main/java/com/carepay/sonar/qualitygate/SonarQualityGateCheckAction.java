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

/**
 * Action in Gradle which runs after the SonarQube task has finished.
 * Waits for the analysis to be completed, then verifies the quality-gate result.
 */
public class SonarQualityGateCheckAction implements Action<Task> {
    private static final int NR_OF_RETRIES = 30;
    private static final long WAIT_TIME_MILLIS = 5000L;
    private static final String CE_TASK_URL_PROPERTY = "ceTaskUrl";
    private static final String PROJECT_STATUS_URL = "/api/qualitygates/project_status?analysisId=";
    private static final String SUCCESS_STATUS = "SUCCESS";

    private final ResourceFetcher resourceFetcher;

    public SonarQualityGateCheckAction() {
        this(new ResourceFetcher(new URLOpener.Default()));
    }

    protected SonarQualityGateCheckAction(ResourceFetcher resourceFetcher) {
        this.resourceFetcher = resourceFetcher;
    }

    /**
     * Executes the quality-gate task. Expects the sonar.login to be defined in the properties.
     * @param task the SonarQube task
     * @throws IllegalStateException in case of any problems
     */
    @Override
    public void execute(Task task) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> properties = (Map<String, Object>) task.getInputs().getProperties().get("properties");
        if (properties == null) {
            throw new IllegalStateException("Sonarqube task properties missing");
        }
        final String hostUrl = (String)properties.getOrDefault("sonar.host.url", "https://sonarcloud.io");
        final String login = (String)properties.get("sonar.login");
        final String password = (String)properties.getOrDefault("sonar.password", "");
        task.getLogger().info("checking quality gate");
        final Properties props = getTaskProperties(new File(task.getProject().getBuildDir(), "sonar/report-task.txt"));
        final String analysisId = waitForAnalysisId(props.getProperty(CE_TASK_URL_PROPERTY), login, password);
        task.getLogger().info("analysis id {}", analysisId);
        final URL analysisUrl = URLOpener.create(hostUrl + PROJECT_STATUS_URL + analysisId);
        final String status = getQualityGateStatus(analysisUrl, login, password);
        task.getLogger().info("quality gate project status is {}", status);
        if (!"OK".equals(status)) {
            throw new IllegalStateException("Quality gate failed: " + status);
        }
    }

    /**
     * Extracts the content of the SonarQube report-task file
     *
     * @param reportFile the sonarqube report-task.txt file
     * @return the contents of the file as a Properties object
     * @throws IllegalStateException in case the file cannot be found or parsed
     */
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

    /**
     * Extracts the analysis project status from the Sonar server
     * @param analysisUrl url of the analysis result
     * @param login sonar login
     * @param password sonar password
     * @return the status
     * @throws IllegalStateException in case the JSON is invalid
     */
    protected String getQualityGateStatus(final URL analysisUrl, final String login, final String password) {
        final JsonObject responseJson = resourceFetcher.queryJsonBasicAuth(analysisUrl, login, password);
        final JsonObject statusJson = responseJson.getMap(ProjectStatusJsonProperty.projectStatus);
        if (statusJson == null) {
            throw new IllegalStateException("Invalid JSON response: missing 'projectStatus'");
        }
        return statusJson.getString(ProjectStatusJsonProperty.status);
    }

    /**
     * Waits for the analysis to be completed
     * @param taskUrlString url of the task result
     * @param login sonar login
     * @param password sonar password
     * @return analysisId of the completed task
     */
    protected String waitForAnalysisId(final String taskUrlString, final String login, final String password) {
        final URL ceTaskUrl = URLOpener.create(taskUrlString);
        try {
            for (int i = 0; i < NR_OF_RETRIES; i++) {
                final JsonObject responseJson = resourceFetcher.queryJsonBasicAuth(ceTaskUrl, login, password);
                final JsonObject taskJson = responseJson.getMap(TaskJsonProperty.task);
                if (taskJson == null) {
                    throw new IllegalStateException("Invalid JSON response: missing 'task'");
                }
                if (!SUCCESS_STATUS.equals(taskJson.getString(TaskJsonProperty.status))) {
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
