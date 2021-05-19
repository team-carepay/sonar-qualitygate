package com.carepay.sonar.qualitygate;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin, which waits for SonarQube analysis to finish, optionally breaking the build if the
 * quality gate is not OK.
 */
public class SonarQualityGatePlugin implements Plugin<Project> {

    @SuppressWarnings("unchecked")
    public void apply(Project project) {
        project.getTasksByName("sonarqube", true).forEach(task -> {
            task.doLast(new SonarQualityGateCheckAction());
        });
    }
}