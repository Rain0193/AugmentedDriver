package com.salesforceiq.augmenteddriver.integrations;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.salesforceiq.augmenteddriver.modules.PropertiesModule;
import com.salesforceiq.augmenteddriver.runners.AugmentedResult;
import com.ullink.slack.simpleslackapi.SlackAttachment;
import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackField;
import com.ullink.slack.simpleslackapi.SlackMessage;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In charge of broadcasting via SLACK.
 */
@Singleton
public class SlackIntegration implements Integration, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SlackIntegration.class);

    /**
     *  <P> IMPORTANT: WHY THESE ARE STATIC??</P>
     *
     *  The reason is that there is one Injector for the Main Test Runner/Suite Runner (where these fields
     *  are initialized) but each TEST also has its own Injector, meaning the @Singleton annotation is only
     *  local to each Injector.
     *  I do not want to create a connection for each test (I could initialize this on each setup and destroy
     *  on each tear down). This way is a little bit cleaner, even though I am sharing these configuration
     *  via static.
     */
    private static SlackChannel digestChannel;
    private static SlackSession slackSession;
    private static SlackChannel verboseChannel;


    private final boolean enabled;
    private final String slackVerboseChannel;
    private final String slackDigestChannel;
    private final String slackBotToken;

    @Inject
    public SlackIntegration(@Named(PropertiesModule.SLACK_INTEGRATION) String slackIntegration,
                            @Named(PropertiesModule.SLACK_BOT_TOKEN) String slackBotToken,
                            @Named(PropertiesModule.SLACK_DIGEST_CHANNEL) String slackDigestChannel,
                            @Named(PropertiesModule.SLACK_VERBOSE_CHANNEL) String slackVerboseChannel) {
        this.enabled = Boolean.valueOf(Preconditions.checkNotNull(slackIntegration));
        this.slackVerboseChannel = Preconditions.checkNotNull(slackVerboseChannel);
        this.slackDigestChannel = Preconditions.checkNotNull(slackDigestChannel);
        this.slackBotToken = Preconditions.checkNotNull(slackBotToken);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void failed(Description description, Throwable error, String sessionId) {
        if (verboseEnabled()) {
            String text = String.format("%s#%s", description.getClassName(), description.getMethodName());
            String title = "FAILED";

            SlackAttachment header = new SlackAttachment(title, "WHAT", text, null);
            header
                    .setColor("danger");
            slackSession
                    .sendMessage(verboseChannel,
                            "",
                            header);

            //-----------------------------------------------------------------------

            title = "Reason";
            text = String.format(error.getMessage());
            SlackAttachment reason = new SlackAttachment(title, "WHAT", text, null);
            reason
                    .setColor("warning");
            slackSession
                    .sendMessage(verboseChannel,
                            "",
                            reason);

            
            //-----------------------------------------------------------------------

            StringBuilder exception = new StringBuilder();
            exception.append("```");
            Arrays.asList(error.getStackTrace())
                    .stream()
                    .forEach(stackTraceElement -> {
                        exception
                                .append(stackTraceElement.toString() + "\n");
                    });
            exception.append("```");
            slackSession
                    .sendMessage(verboseChannel,
                            exception.toString());
        }
    }

    public void passed(Description description, String sessionId) {
        if (verboseEnabled()) {
            String text = String.format("%s#%s", description.getClassName(), description.getMethodName());
            String title = "SUCCEEDED";

            SlackAttachment slackAttachment = new SlackAttachment(title, "WHAT", text, null);
            slackAttachment
                    .setColor("good");
            slackSession
                    .sendMessage(verboseChannel,
                            "",
                            slackAttachment);
        }
    }

    public void digest(String title, List<AugmentedResult> results) {
        if (digestEnabled()) {
            List<AugmentedResult> failed = failedTests(results);
            String slackTitle = String.format("%s %s", title, failed.isEmpty() ? " SUCCEEDED" : " FAILED");
            String slackText = String.format("TOTAL: %s SUCCEEDED: %s FAILED %s",
                    results.size(),
                    results.size() - failed.size(),
                    failed.size());

            SlackAttachment slackAttachment = new SlackAttachment(slackTitle, "WHAT", slackText, null);
            slackAttachment.setColor(failed.isEmpty() ? "good" : "danger");
            slackSession
                    .sendMessage(digestChannel,
                            "",
                            slackAttachment);

            failed
                    .stream()
                    .forEach(failedTest -> {
                        String failedTestTitle = String.format("%s", failedTest.getTestName());
                        String failedTestText = failedTest.getResult().getFailures().get(0).getMessage();
                        SlackAttachment faildTestAttachment = new SlackAttachment(failedTestTitle, "WHAT", failedTestText, null);
                        faildTestAttachment
                                .setColor("warning");
                        slackSession
                                .sendMessage(digestChannel,
                                        "",
                                        faildTestAttachment);
                    });
        }
    }

    /**
     * Initialization of slack connection and slack channels
     */
    public void initialize() {
        if (Strings.isNullOrEmpty(slackBotToken)) {
            LOG.warn("No Slack Bot Token, Slack Integration will not broadcast at all");
        } else {
            try {
                slackSession = SlackSessionFactory
                        .createWebSocketSlackSession(slackBotToken);
                slackSession.connect();
                if (Strings.isNullOrEmpty(slackVerboseChannel)) {
                    LOG.warn("No Slack Verbose Channel, Slack Integration will not broadcast success/failures");
                } else {
                    verboseChannel = slackSession.findChannelByName(slackVerboseChannel);
                    if (verboseChannel == null) {
                        LOG.warn(String.format("Verbose Channel %s not found, Slack Integration will not broadcast success/failures", slackVerboseChannel));
                    } else {
                        slackSession
                                .joinChannel(slackVerboseChannel);
                    }
                }
                if (Strings.isNullOrEmpty(slackDigestChannel)) {
                    LOG.warn("No Slack Digest Channel, Slack Integration will not broadcast summaries");
                } else {
                    digestChannel = slackSession.findChannelByName(slackDigestChannel);
                    if (digestChannel == null) {
                        LOG.warn(String.format("Digest Channel %s not found, Slack Integration will not broadcast summaries", slackDigestChannel));
                    } else {
                        slackSession
                                .joinChannel(slackDigestChannel);
                    }
                }
            } catch (IOException e) {
                LOG.warn(String.format("Could not create session with token %s, Slack Integration will not broadcast at all", slackBotToken), e);
            }
        }
    }

    private boolean verboseEnabled() {
        return enabled && slackSession != null && verboseChannel != null;
    }

    private boolean digestEnabled() {
        return enabled && slackSession != null && digestChannel != null;
    }

    @Override
    public void close() throws Exception {
        if (slackSession != null) {
            slackSession.disconnect();
        }
    }

    private List<AugmentedResult> failedTests(List<AugmentedResult> results) {
        return results.stream()
                .filter(result -> !result.getResult().wasSuccessful())
                .collect(Collectors.toList());
    }
}