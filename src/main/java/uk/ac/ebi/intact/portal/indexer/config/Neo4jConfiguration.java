package uk.ac.ebi.intact.portal.indexer.config;


import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@ComponentScan("uk.ac.ebi.intact.graphdb")
public class Neo4jConfiguration {
}