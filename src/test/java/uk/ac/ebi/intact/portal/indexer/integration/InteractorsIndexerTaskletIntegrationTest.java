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
 * User: ntoro
 * Date: 17/01/2014
 * Time: 16:03
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class InteractorsIndexerTaskletIntegrationTest {


    private JobLauncherTestUtils jobLauncherTestUtils;

    @Resource
    private Tasklet interactorCleanerTasklet;

    @Resource
    private Tasklet interactorIndexerTasklet;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private Job interactorIndexerJob;

    @ClassRule
    public static RequiresSolrServer requiresRunningServer = RequiresSolrServer.onLocalhost();

    private void initializeJobLauncherTestUtils() {
        this.jobLauncherTestUtils = new JobLauncherTestUtils();
        this.jobLauncherTestUtils.setJobLauncher(jobLauncher);
        this.jobLauncherTestUtils.setJobRepository(jobRepository);
        this.jobLauncherTestUtils.setJob(interactorIndexerJob);
    }

    /**
     * Tests if interactor index cleaning and creation (interactorIndexerJob) Job runs completely
     */
    @Test
    public void jobSimulation() {

        if (jobLauncherTestUtils == null) {
            initializeJobLauncherTestUtils();
        }
        JobExecution jobExecution = null;
        try {
            jobExecution = jobLauncherTestUtils.launchJob();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not have thrown any exception: " + e.toString());
        }

        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);

    }

    /**
     * Tests if interactor index cleaning (interactorCleanerStep) Step runs completely
     */
    @Test
    public void cleanerStepSimulation() {

        if (jobLauncherTestUtils == null) {
            initializeJobLauncherTestUtils();
        }
        JobExecution jobExecution = jobLauncherTestUtils.launchStep("interactorCleanerStep");
        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);
    }

    /**
     * Tests if interactor indexing (interactorIndexingStep) Step runs completely
     */
    @Test
    public void indexingStepSimulation() {

        if (jobLauncherTestUtils == null) {
            initializeJobLauncherTestUtils();
        }
        JobExecution jobExecution = jobLauncherTestUtils.launchStep("interactorIndexingStep");
        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);
    }

    /**
     * Tests if interactor index cleaning (indexCleanerTasklet) Tasklet runs completely
     */
    @Test
    public void cleanerTaskletSimulation(){

        StepExecution execution = createStepExecution();

        ChunkContext chunkContext = new ChunkContext(new StepContext(execution));
        StepContribution stepContribution = new StepContribution(execution);

        RepeatStatus repeatStatus = null;
        try {
            repeatStatus = interactorCleanerTasklet.execute(stepContribution, chunkContext);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not have thrown any exception: " + e.toString());
        }

        Assert.assertNotNull(repeatStatus);
        Assert.assertFalse(repeatStatus.isContinuable());
    }

    /**
     * Tests if interactor indexing (interactorIndexerTasklet) Tasklet runs completely
     */
    @Test
    public void indexerTaskletSimulation() {

        StepExecution execution = createStepExecution();

        ChunkContext chunkContext = new ChunkContext(new StepContext(execution));
        StepContribution stepContribution = new StepContribution(execution);

        RepeatStatus repeatStatus = null;

        try {
            repeatStatus = interactorIndexerTasklet.execute(stepContribution, chunkContext);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not have thrown any exception: " + e.toString());
        }

        Assert.assertNotNull(repeatStatus);
        Assert.assertFalse(repeatStatus.isContinuable());
    }
}
