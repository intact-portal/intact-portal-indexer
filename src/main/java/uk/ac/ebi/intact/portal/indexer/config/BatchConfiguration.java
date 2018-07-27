package uk.ac.ebi.intact.portal.indexer.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import uk.ac.ebi.intact.portal.indexer.IndexCleanerTasklet;
import uk.ac.ebi.intact.portal.indexer.interaction.InteractionCleanerTasklet;
import uk.ac.ebi.intact.portal.indexer.interaction.InteractionIndexerTasklet;
import uk.ac.ebi.intact.portal.indexer.listeners.JobCompletionNotificationListener;
import uk.ac.ebi.intact.portal.indexer.interactor.InteractorIndexerTasklet;

@Configuration
@EnableBatchProcessing
@Import({})
public class BatchConfiguration {

    @Autowired
    public JobBuilderFactory jobBuilderFactory;

    @Autowired
    public StepBuilderFactory stepBuilderFactory;

//    // tag::readerwriterprocessor[]
//    @Bean
//    public FlatFileItemReader<Person> reader() {
//        return new FlatFileItemReaderBuilder<Person>()
//                .name("personItemReader")
//                .resource(new ClassPathResource("sample-data.csv"))
//                .delimited()
//                .names(new String[]{"firstName", "lastName"})
//                .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
//                    setTargetType(Person.class);
//                }})
//                .build();
//    }
//
//    @Bean
//    public PersonItemProcessor processor() {
//        return new PersonItemProcessor();
//    }
//
//    @Bean
//    public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
//        return new JdbcBatchItemWriterBuilder<Person>()
//                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
//                .sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")
//                .dataSource(dataSource)
//                .build();
//    }
//    // end::readerwriterprocessor[]

    // tag::jobstep[]
    @Bean
    public Job intactPortalIndexerJob(JobCompletionNotificationListener listener,
                                      Step indexCleanerStep,
                                      Step interactorIndexingStep,Step interactionIndexCleanerStep,Step interactionIndexingStep ) {
        return jobBuilderFactory.get("intactPortalIndexerJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
   //             .start(indexCleanerStep)
   //             .next(interactorIndexingStep)
                .start(interactionIndexCleanerStep)
                .next(interactionIndexingStep)
                .build();
    }

    @Bean
    public Step indexCleanerStep(IndexCleanerTasklet tasklet) {
        return stepBuilderFactory.get("indexCleanerStep")
                .tasklet(tasklet)
                .build();
    }

    @Bean
    public Step interactionIndexCleanerStep(InteractionCleanerTasklet tasklet) {
        return stepBuilderFactory.get("interactionIndexCleanerStep")
                .tasklet(tasklet)
                .build();
    }

    @Bean
    public Step interactorIndexingStep(InteractorIndexerTasklet tasklet) {
        return stepBuilderFactory.get("interactorIndexingStep")
                .tasklet(tasklet)
                .build();
    }

    @Bean
    public Step interactionIndexingStep(InteractionIndexerTasklet tasklet) {
        return stepBuilderFactory.get("interactionIndexingStep")
                .tasklet(tasklet)
                .build();
    }
    // end::jobstep[]
}