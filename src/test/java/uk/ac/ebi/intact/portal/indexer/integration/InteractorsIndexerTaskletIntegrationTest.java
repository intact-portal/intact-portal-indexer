package uk.ac.ebi.intact.portal.indexer.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.test.StepScopeTestExecutionListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ebi.intact.portal.indexer.config.BatchConfiguration;

import javax.annotation.Resource;

import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution;

/**
 * User: ntoro
 * Date: 17/01/2014
 * Time: 16:03
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {BatchConfiguration.class})
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class,
        TransactionalTestExecutionListener.class,
        StepScopeTestExecutionListener.class})
//TODO Review the configuration of the test to be sure that used the localhost, in memory resources
public class InteractorsIndexerTaskletIntegrationTest {

    @Resource
    private Tasklet interactorIndexerTasklet;

    @Test
    @DirtiesContext
    @Transactional(readOnly = true)
    public void testTasklet() throws Exception {

        StepExecution execution = createStepExecution();

        ChunkContext chunkContext = new ChunkContext(new StepContext(execution));
        StepContribution stepContribution = new StepContribution(execution);

        interactorIndexerTasklet.execute(stepContribution, chunkContext);

    }
}
