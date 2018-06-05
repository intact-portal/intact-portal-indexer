package uk.ac.ebi.intact.portal.indexer.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.solr.repository.config.EnableSolrRepositories;

@Configuration
@ComponentScan("uk.ac.ebi.intact.search")
@EnableSolrRepositories(value = "uk.ac.ebi.intact.search",
        schemaCreationSupport = true)
public class SolrConfiguration {

}