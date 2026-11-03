package gov.nasa.pds.search.servlet;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;

/**
 * Unit tests for per-request Solr request handler selection in RegistryLegacyServlet.
 */
public class RegistryLegacyServletTest {

    private static final String DEFAULT_HANDLER = "archive-filter";

    @Test
    public void testNoQtUsesDefault() {
        assertEquals(DEFAULT_HANDLER,
            RegistryLegacyServlet.resolveRequestHandler(null, DEFAULT_HANDLER));
    }

    @Test
    public void testAllowedQtIsUsed() {
        for (String handler : new String[] {"search", "archive-filter", "select", "keyword", "all"}) {
            assertEquals(handler, RegistryLegacyServlet.resolveRequestHandler(handler, "select"));
        }
    }

    @Test
    public void testUnknownQtUsesDefault() {
        assertEquals(DEFAULT_HANDLER,
            RegistryLegacyServlet.resolveRequestHandler("admin/luke", DEFAULT_HANDLER));
        assertEquals(DEFAULT_HANDLER,
            RegistryLegacyServlet.resolveRequestHandler("", DEFAULT_HANDLER));
    }

    @Test
    public void testKeywordRequestDoesNotAffectLaterRequest() {
        assertEquals("keyword",
            RegistryLegacyServlet.resolveRequestHandler("keyword", DEFAULT_HANDLER));
        assertEquals(DEFAULT_HANDLER,
            RegistryLegacyServlet.resolveRequestHandler(null, DEFAULT_HANDLER));
    }

    @Test
    public void testConcurrentRequestsDoNotInterfere() throws InterruptedException,
        ExecutionException {
        final int threads = 16;
        final int iterations = 5000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < threads; i++) {
                // Even threads send qt=keyword, odd threads send no qt.
                final String qt = (i % 2 == 0) ? "keyword" : null;
                final String expected = (qt != null) ? qt : DEFAULT_HANDLER;
                results.add(executor.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws InterruptedException {
                        start.await();
                        for (int n = 0; n < iterations; n++) {
                            if (!expected.equals(
                                RegistryLegacyServlet.resolveRequestHandler(qt, DEFAULT_HANDLER))) {
                                return Boolean.FALSE;
                            }
                        }
                        return Boolean.TRUE;
                    }
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
