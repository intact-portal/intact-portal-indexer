package uk.ac.ebi.intact.portal.indexer.config;


import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@ComponentScan("uk.ac.ebi.intact.graphdb")
@EntityScan({"uk.ac.ebi.intact.graphdb.model"})
@EnableNeo4jRepositories("uk.ac.ebi.intact.graphdb.repositories")
public class Neo4jConfiguration {

//    @Bean
//    public SessionFactory getSessionFactory() {
//        return new SessionFactory(configuration(), "uk.ac.ebi.intact.graphdb");
//    }

//    @Bean
//    public Neo4jTransactionManager transactionManager() throws Exception {
//        return new Neo4jTransactionManager(getSessionFactory());
//    }
//
//    @Bean
//    public org.neo4j.ogm.config.Configuration configuration() {
//        return new org.neo4j.ogm.config.Configuration.Builder().uri("bolt://localhost").build();
//    }

}