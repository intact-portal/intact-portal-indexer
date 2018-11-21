package uk.ac.ebi.intact.portal.indexer.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.util.NamedList;
import org.apache.zookeeper.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.repository.config.EnableSolrRepositories;

import javax.annotation.Resource;
import java.io.IOException;

@Configuration
@ComponentScan("uk.ac.ebi.intact.search")
@EnableSolrRepositories(value = "uk.ac.ebi.intact.search",
        schemaCreationSupport = true)
public class SolrConfiguration {

    @Value("${spring.data.solr.host}")
    private String solrHost;

    @Bean
    public SolrClient solrClient() {
        SolrClient solrClient = new HttpSolrClient.Builder(solrHost).build();
        return solrClient;
    }
}