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
import uk.ac.ebi.intact.portal.indexer.cv.CVCleanerTasklet;
import uk.ac.ebi.intact.portal.indexer.cv.CVIndexerTasklet;
import uk.ac.ebi.intact.portal.indexer.interaction.InteractionCleanerTasklet;
import uk.ac.ebi.intact.portal.indexer.interaction.InteractionIndexerTasklet;
import uk.ac.ebi.intact.portal.indexer.interaction.clusteredInteraction.ClusteredInteractionCleanerTasklet;
import uk.ac.ebi.intact.portal.indexer.interaction.clusteredInteraction.ClusteredInteractionIndexerTasklet;
import uk.ac.ebi.intact.portal.indexer.interactor.InteractorIndexerTasklet;
import uk.ac.ebi.intact.portal.indexer.listeners.JobCompletionNotificationListener;

@Configuration
@EnableBatchProcessing
@Import({})
public class BatchConfiguration {

    @Autowired
    public JobBuilderFactory jobBuilderFactory;

    @Autowired
    public StepBuilderFactory stepBuilderFactory;

    /*Job will all the indexing  steps*/

    @Bean
    public Job intactPortalIndexerJob(JobCompletionNotificationListener listener,
                                      Step interactorCleanerStep,
                                      Step interactorIndexingStep,
                                      Step interactionIndexCleanerStep,
                                      Step interactionIndexingStep,
                                      Step cvIndexCleanerStep,
                                      Step cvIndexingStep,
                                      Step clusteredInteractionIndexCleanerStep,
                                      Step clusteredInteractionIndexingStep) {
        return jobBuilderFactory.get("intactPortalIndexerJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(interactorCleanerStep)
                .next(interactorIndexingStep)
                .next(interactionIndexCleanerStep)
                .next(interactionIndexingStep)
                .next(cvIndexCleanerStep)
                .next(cvIndexingStep)
                .next(clusteredInteractionIndexCleanerStep)
                .next(clusteredInteractionIndexingStep)
                .build();
    }

    @Bean
    public Job interactorIndexerJob(JobCompletionNotificationListener listener,
                                    Step interactorCleanerStep,
                                    Step interactorIndexingStep) {
        return jobBuilderFactory.get("interactorIndexerJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(interactorCleanerStep)
                .next(interactorIndexingStep)
                .build();
    }

    @Bean
    public Job interactionIndexerJob(JobCompletionNotificationListener listener,
                                     Step interactionIndexCleanerStep,
                                     Step interactionIndexingStep) {
        return jobBuilderFactory.get("interactionIndexerJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(interactionIndexCleanerStep)
                .next(interactionIndexingStep)
                .build();
    }

    @Bean
    public Job cvIndexerJob(JobCompletionNotificationListener listener,
                            Step cvIndexCleanerStep, Step cvIndexingStep) {
        return jobBuilderFactory.get("cvIndexerJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(cvIndexCleanerStep)
                .next(cvIndexingStep)
                .build();
    }

    @Bean
    public Job clusteredInteractionIndexerJob(JobCompletionNotificationListener listener,
                                              Step clusteredInteractionIndexCleanerStep,
                                              Step clusteredInteractionIndexingStep) {
        return jobBuilderFactory.get("clusteredInteractionIndexerJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(clusteredInteractionIndexCleanerStep)
                .next(clusteredInteractionIndexingStep)
                .build();
    }

    /*Interactor Indexing*/

    @Bean
    public Step interactorCleanerStep(IndexCleanerTasklet tasklet) {
        return stepBuilderFactory.get("interactorCleanerStep")
                .tasklet(tasklet)
                .build();
    }

    @Bean
    public Step interactorIndexingStep(InteractorIndexerTasklet tasklet) {
        return stepBuilderFactory.get("interactorIndexingStep")
                .tasklet(tasklet)
                .build();
    }

    /*Interaction Indexing*/

    @Bean
    public Step interactionIndexCleanerStep(InteractionCleanerTasklet tasklet) {
        return stepBuilderFactory.get("interactionIndexCleanerStep")
                .tasklet(tasklet)
                .build();
    }

    @Bean
    public Step interactionIndexingStep(InteractionIndexerTasklet tasklet) {
        return stepBuilderFactory.get("interactionIndexingStep")
                .tasklet(tasklet)
                .build();
    }

    /*CV Term Indexing*/
    @Bean
    public Step cvIndexCleanerStep(CVCleanerTasklet tasklet) {
        return stepBuilderFactory.get("cvIndexCleanerStep")
                .tasklet(tasklet)
                .build();
    }

    @Bean
    public Step cvIndexingStep(CVIndexerTasklet tasklet) {
        return stepBuilderFactory.get("cvIndexingStep")
                .tasklet(tasklet)
                .build();
    }

    /*Clustered Interaction Indexing*/

    @Bean
    public Step clusteredInteractionIndexCleanerStep(ClusteredInteractionCleanerTasklet tasklet) {
        return stepBuilderFactory.get("clusteredInteractionIndexCleanerStep")
                .tasklet(tasklet)
                .build();
    }

    @Bean
    public Step clusteredInteractionIndexingStep(ClusteredInteractionIndexerTasklet tasklet) {
        return stepBuilderFactory.get("clusteredInteractionIndexingStep")
                .tasklet(tasklet)
                .build();
    }

    // end::jobstep[]
}