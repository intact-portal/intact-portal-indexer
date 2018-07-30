package uk.ac.ebi.intact.portal.indexer.integration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.StepScopeTestExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ebi.intact.portal.indexer.config.BatchConfiguration;
import uk.ac.ebi.intact.portal.indexer.interaction.InteractionCleanerTasklet;
import uk.ac.ebi.intact.portal.indexer.interaction.InteractionIndexerTasklet;
import uk.ac.ebi.intact.portal.indexer.listeners.JobCompletionNotificationListener;

import javax.annotation.Resource;

import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution;

/**
 * User: anjali
 * Date: 27/07/2014
 * Time: 14:30
 */
@RunWith(SpringRunner.class)
@SpringBootTest
//TODO Review the configuration of the test to be sure that used the localhost, in memory resources
public class InteractionIndexerIntegrationTest {


    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job interactionIndexerJob;



    @Test
    public void simulation() throws Exception {

        JobParametersBuilder jobParametersBuilder =
                new JobParametersBuilder();

        jobParametersBuilder.addLong("time",System.currentTimeMillis()).toJobParameters();
            jobParametersBuilder.addString("interactionIndexerJob","interactionIndexerJob");
        JobParameters jobParameters =jobParametersBuilder.toJobParameters();
        JobExecution jobExecution = jobLauncher.run(interactionIndexerJob, jobParameters);
        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);

    }


}
