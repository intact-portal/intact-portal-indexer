package uk.ac.ebi.intact.portal.indexer.integration;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.intact.portal.indexer.RequiresSolrServer;

import javax.annotation.Resource;

import static org.junit.Assert.fail;
import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution;

/**
 * User: anjali
 * Date: 27/07/2014
 * Time: 14:30
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class InteractionIndexerIntegrationTest {

    private JobLauncherTestUtils jobLauncherTestUtils;
    @Resource
    private Tasklet interactionCleanerTasklet;
    @Resource
    private Tasklet interactionIndexerTasklet;
    @Autowired
    private JobLauncher jobLauncher;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private Job interactionIndexerJob;

    @ClassRule
    public static RequiresSolrServer requiresRunningServer = RequiresSolrServer.onLocalhost();

    private void initializeJobLauncherTestUtils() {
        this.jobLauncherTestUtils = new JobLauncherTestUtils();
        this.jobLauncherTestUtils.setJobLauncher(jobLauncher);
        this.jobLauncherTestUtils.setJobRepository(jobRepository);
        this.jobLauncherTestUtils.setJob(interactionIndexerJob);
    }

    /**
     * Tests if interaction index cleaning and creation 'interactionIndexerJob' Job runs completely
     *
     * @throws Exception thrown if error occurs during execution.
     */
    @Test
    public void jobSimulation() throws Exception {

        if (jobLauncherTestUtils == null) {
            initializeJobLauncherTestUtils();
        }
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();
        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);

    }

    /**
     * Tests if interaction index cleaning (interactionIndexCleanerStep) Step runs completely
     */
    @Test
    public void cleanerStepSimulation() {

        if (jobLauncherTestUtils == null) {
            initializeJobLauncherTestUtils();
        }
        JobExecution jobExecution = jobLauncherTestUtils.launchStep("interactionIndexCleanerStep");
        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);

    }

    /**
     * Tests if interaction indexing (interactionIndexingStep) Step runs completely
     */
    @Test
    public void indexingStepSimulation() {

        if (jobLauncherTestUtils == null) {
            initializeJobLauncherTestUtils();
        }
        JobExecution jobExecution = jobLauncherTestUtils.launchStep("interactionIndexingStep");
        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);

    }

    /**
     * Tests if interaction index cleaning (interactionCleanerTasklet) Tasklet runs completely
     */
    @Test
    public void cleanerTaskletSimulation() {

        StepExecution execution = createStepExecution();

        ChunkContext chunkContext = new ChunkContext(new StepContext(execution));
        StepContribution stepContribution = new StepContribution(execution);

        RepeatStatus repeatStatus = null;
        try {
            repeatStatus = interactionCleanerTasklet.execute(stepContribution, chunkContext);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not have thrown any exception: " + e.toString());
        }

        Assert.assertNotNull(repeatStatus);
        Assert.assertFalse(repeatStatus.isContinuable());
    }

    /**
     * Tests if interaction indexing (interactionIndexingStep) Tasklet runs completely
     */
    @Test
    public void indexerTaskletSimulation() {

        StepExecution execution = createStepExecution();

        ChunkContext chunkContext = new ChunkContext(new StepContext(execution));
        StepContribution stepContribution = new StepContribution(execution);

        RepeatStatus repeatStatus = null;
        try {
            repeatStatus = interactionIndexerTasklet.execute(stepContribution, chunkContext);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not have thrown any exception: " + e.toString());
        }

        Assert.assertNotNull(repeatStatus);
        Assert.assertFalse(repeatStatus.isContinuable());

    }
}
