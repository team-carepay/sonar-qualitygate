package com.carepay.sonar.qualitygate.util;

/**
 * Interface for accessing environment variables. This allows for mocking environment in
 * unit-tests.
 */
@FunctionalInterface
public interface Env {
    String getEnv(String name);

    class Default implements Env {
        @Override
        public String getEnv(String name) {
            return System.getenv(name);
        }
    }
}
