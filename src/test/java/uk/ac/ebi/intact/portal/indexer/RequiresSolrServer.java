package uk.ac.ebi.intact.portal.indexer;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hamcrest.core.Is;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;

/**
 * {@link TestRule} implementation using {@link CloseableHttpClient} to check if Solr is running by sending
 * {@literal GET} request to {@literal /admin/info/system}.
 *
 * @author Elisabet Barrera
 */

public class RequiresSolrServer implements TestRule {

    private static final String PING_PATH = "/";

    private final String baseUrl;

    private RequiresSolrServer(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static RequiresSolrServer onLocalhost() {
        return new RequiresSolrServer("http://localhost:8983/solr");
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {

                checkServerRunning();
                base.evaluate();
            }
        };
    }

    private void checkServerRunning() {

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            CloseableHttpResponse response = client.execute(new HttpGet(baseUrl + PING_PATH));
            if (response != null && response.getStatusLine() != null) {
                Assume.assumeThat(response.getStatusLine().getStatusCode(), Is.is(200));
            }
        } catch (IOException e) {
            throw new AssumptionViolatedException("SolrServer does not seem to be running", e);
        }
    }

}