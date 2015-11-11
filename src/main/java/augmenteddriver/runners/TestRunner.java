package augmenteddriver.runners;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import augmenteddriver.integrations.IntegrationFactory;
import augmenteddriver.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Knows how to run one test.
 */
public class TestRunner implements Callable<AugmentedResult> {
    private static final Log LOG = LogFactory.getLog(TestRunner.class);

    private final Method test;
    private final ByteArrayOutputStream outputStream;
    private final String nameAppender;
    private final IntegrationFactory integrationFactory;

    @Inject
    public TestRunner(@Assisted Method test,
                      @Assisted String nameAppender,
                      ByteArrayOutputStream outputStream,
                      IntegrationFactory integrationFactory) {
        this.test = Preconditions.checkNotNull(test);
        this.nameAppender = Preconditions.checkNotNull(nameAppender);
        this.outputStream = Preconditions.checkNotNull(outputStream);
        this.integrationFactory = Preconditions.checkNotNull(integrationFactory);
    }

    @Override
    public AugmentedResult call() throws Exception {
        JUnitCore jUnitCore = getJUnitCore();
        String testName = String.format("%s#%s", test.getDeclaringClass().getCanonicalName(), test.getName());
        long start = System.currentTimeMillis();
        try {
            LOG.info(String.format("STARTING Test %s", testName));
            Result result = jUnitCore.run(Request.method(test.getDeclaringClass(), test.getName()));
            LOG.info(String.format("FINSHED Test %s in %s", testName, Util.TO_PRETTY_FORNAT.apply(System.currentTimeMillis() - start)));
            return new AugmentedResult(result, outputStream);
        } finally {
            outputStream.close();
        }
    }

    private JUnitCore getJUnitCore() {
        JUnitCore jUnitCore = new JUnitCore();
        if (integrationFactory.teamCity().isEnabled()) {
            jUnitCore.addListener(integrationFactory.teamCity().getReporter(outputStream, nameAppender));
        }
        return jUnitCore;
    }
}
