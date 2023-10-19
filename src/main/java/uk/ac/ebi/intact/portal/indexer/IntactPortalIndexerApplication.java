package uk.ac.ebi.intact.portal.indexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.solr.repository.config.EnableSolrRepositories;
import uk.ac.ebi.intact.graphdb.aop.LazyFetchAspect;

import static org.springframework.boot.SpringApplication.run;

@EntityScan({"uk.ac.ebi.intact.graphdb.model"})
@EnableNeo4jRepositories(basePackages = "uk.ac.ebi.intact.graphdb.repository")
@EnableSolrRepositories(basePackages = "uk.ac.ebi.intact.search", schemaCreationSupport = true)
@SpringBootApplication(scanBasePackages = {
        "uk.ac.ebi.intact.graphdb",
        "uk.ac.ebi.intact.search",
        "uk.ac.ebi.intact.style",
        "uk.ac.ebi.intact.portal"})
public class IntactPortalIndexerApplication {

    public static void main(String[] args) {
        ApplicationContext applicationContext = run(IntactPortalIndexerApplication.class, args);
        System.exit(SpringApplication.exit(applicationContext));
    }

    @Bean
    public boolean isEmbeddedSolr() {
        return false;
    }

    /* This enables aspectJ (together with the aop.enable property in application.properties) */
    @Bean
    public LazyFetchAspect lazyFetchAspect() {
        return org.aspectj.lang.Aspects.aspectOf(LazyFetchAspect.class);
    }
}