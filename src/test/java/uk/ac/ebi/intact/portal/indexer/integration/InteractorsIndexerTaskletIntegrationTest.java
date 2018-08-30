package uk.ac.ebi.intact.portal.indexer.integration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution;

/**
 * User: ntoro
 * Date: 17/01/2014
 * Time: 16:03
 */
@RunWith(SpringRunner.class)
@SpringBootTest
//TODO Review the configuration of the test to be sure that used the localhost, in memory resources
public class InteractorsIndexerTaskletIntegrationTest {

    private JobLauncherTestUtils jobLauncherTestUtils;

    @Resource
    private Tasklet indexCleanerTasklet;

    @Resource
    private Tasklet interactorIndexerTasklet;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private Job interactorIndexerJob;

    private void initializeJobLauncherTestUtils() {
        this.jobLauncherTestUtils = new JobLauncherTestUtils();
        this.jobLauncherTestUtils.setJobLauncher(jobLauncher);
        this.jobLauncherTestUtils.setJobRepository(jobRepository);
        this.jobLauncherTestUtils.setJob(interactorIndexerJob);
    }

    /**
     * Tests if interactor index cleaning and creation (interactorIndexerJob) Job runs completely
     * @throws Exception
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
     * Tests if interactor index cleaning (interactorCleanerStep) Step runs completely
     * @throws Exception
     */
    @Test
    public void cleanerStepSimulation() throws Exception {

        if (jobLauncherTestUtils == null) {
            initializeJobLauncherTestUtils();
        }
        JobExecution jobExecution = jobLauncherTestUtils.launchStep("interactorCleanerStep");
        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);

    }

    /**
     * Tests if interactor indexing (interactorIndexingStep) Step runs completely
     * @throws Exception
     */
    @Test
    public void indexingStepSimulation() throws Exception {

        if (jobLauncherTestUtils == null) {
            initializeJobLauncherTestUtils();
        }
        JobExecution jobExecution = jobLauncherTestUtils.launchStep("interactorIndexingStep");
        Assert.assertEquals(jobExecution.getStatus(), BatchStatus.COMPLETED);

    }

    /**
     * Tests if interactor index cleaning (indexCleanerTasklet) Tasklet runs completely
     * @throws Exception
     */
    // TODO check for completion
    @Test
    public void cleanerTaskletSimulation() throws Exception {

        StepExecution execution = createStepExecution();

        ChunkContext chunkContext = new ChunkContext(new StepContext(execution));
        StepContribution stepContribution = new StepContribution(execution);

        indexCleanerTasklet.execute(stepContribution, chunkContext);

    }

    /**
     * Tests if interactor indexing (interactorIndexerTasklet) Tasklet runs completely
     * @throws Exception
     */
    @Test
    public void indexerTaskletSimulation() throws Exception {

        StepExecution execution = createStepExecution();

        ChunkContext chunkContext = new ChunkContext(new StepContext(execution));
        StepContribution stepContribution = new StepContribution(execution);

        interactorIndexerTasklet.execute(stepContribution, chunkContext);

    }
}
