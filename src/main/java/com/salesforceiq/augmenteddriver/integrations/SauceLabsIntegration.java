package com.salesforceiq.augmenteddriver.integrations;

import com.salesforceiq.augmenteddriver.util.TestRunnerConfig;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.saucelabs.saucerest.SauceREST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * In charge of interacting with SauceLabs
 */
@Singleton
public class SauceLabsIntegration implements Integration {

    private final SauceREST sauceRest;
    private final TestRunnerConfig arguments;

    @Inject
    public SauceLabsIntegration(SauceREST sauceREST, TestRunnerConfig arguments) {
        this.sauceRest = Preconditions.checkNotNull(sauceREST);
        this.arguments = Preconditions.checkNotNull(arguments);
    }

    /**
     * Sets the job name
     *
     * @param jobName the name to set in SauceLabs.
     * @param sessionId the session id of the test.
     */
    public void jobName(String jobName, String sessionId) {
        update("name", jobName, sessionId);
    }

    /**
     * Sets whether the test passed or not.
     *
     * @param testPassed if the test passed or not.
     * @param sessionId the session id of the test.
     */
    public void testPassed(boolean testPassed, String sessionId) {
        update("passed", testPassed, sessionId);
    }

    /**
     * Sets the build name
     *
     * @param buildName the name to assign to the entire build.
     * @param sessionId the session id of the test.
     */
    public void buildName(String buildName, String sessionId) {
        update("build", buildName, sessionId);
    }

    /**
     * Uploads a file to SauceLabs. Usually for an app or an apk.
     *
     * @param fileToUpload which file to upload to sauce.
     * @param destinationFileName the name of the file for SauceLabs.
     * @throws IOException if the file couldnt be uploaded.
     */
    public void uploadFile(Path fileToUpload, String destinationFileName) throws IOException {
        Preconditions.checkNotNull(fileToUpload);
        Preconditions.checkArgument(Files.exists(fileToUpload));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(destinationFileName));

        uploadFile(fileToUpload, destinationFileName, true);
    }

    /**
     * Uploads a file to SauceLabs. Usually for an app or an apk.
     *
     * @param overwrite if set to true, it overwrites the file if it exists in SauceLabs.
     * @param fileToUpload which file to upload to sauce.
     * @param destinationFileName the name of the file for SauceLabs.
     * @throws IOException if the file couldnt be uploaded.
     */
    public void uploadFile(Path fileToUpload, String destinationFileName, boolean overwrite) throws IOException {
        Preconditions.checkNotNull(fileToUpload);
        Preconditions.checkArgument(Files.exists(fileToUpload));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(destinationFileName));

        sauceRest.uploadFile(fileToUpload.toFile(), destinationFileName, overwrite);
    }

    private void update(String key, Object value, String sessionId) {
        if (isEnabled()) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(sessionId));
            Preconditions.checkArgument(!Strings.isNullOrEmpty(key));
            Preconditions.checkNotNull(value);

            Map<String, Object> updates = Maps.newHashMap();
            updates.put(key, value);
            sauceRest.updateJobInfo(sessionId, updates);
        }
    }

    @Override
    public boolean isEnabled() {
        return arguments.sauce();
    }
}
