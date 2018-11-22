package uk.ac.ebi.intact.portal.indexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;


@SpringBootApplication
public class IntactPortalIndexerApplication {

    public static void main(String[] args) {
        ApplicationContext applicationContext=SpringApplication.run(IntactPortalIndexerApplication.class, args);
        System.exit(SpringApplication.exit(applicationContext));

    }
}
