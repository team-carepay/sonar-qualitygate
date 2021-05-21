[![Build](https://github.com/team-carepay/sonar-qualitygate/workflows/build/badge.svg)](https://github.com/team-carepay/sonar-qualitygate/actions/workflows/build.yml)
[![Release](https://github.com/team-carepay/sonar-qualitygate/workflows/release/badge.svg)](https://github.com/team-carepay/sonar-qualitygate/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Version](https://badge.fury.io/gh/team-carepay%2Fsonar-qualitygate.svg)](https://github.com/team-carepay/sonar-qualitygate/releases)

# sonar-qualitygate
Gradle plugin to verify Sonar quality-gate outcome

## Usage
```groovy
plugins {
    id 'org.sonarqube' version '3.2.0'
    id 'com.carepay.sonar-qualitygate' version '0.0.5'
}
```

When the plugin is included in your build file, it will automatically run after Sonar has finished.
The plugin will wait for the analysis to be completed, then it will look at the qualitygate project status.
If the qualitygate fails, the build will fail.

The plugin will use the Sonar login token from the `sonarqube` task.

# License
Apache License 2.0