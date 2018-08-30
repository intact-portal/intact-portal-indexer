package uk.ac.ebi.intact.portal.indexer.interaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import uk.ac.ebi.intact.search.interactions.service.InteractionIndexService;
import uk.ac.ebi.intact.search.interactor.service.InteractorIndexService;

import javax.annotation.Resource;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;


@Component
public class InteractionCleanerTasklet implements Tasklet {

    private static final Logger logger = LoggerFactory.getLogger(InteractionCleanerTasklet.class);

    @Resource
    private InteractionIndexService interactionIndexService;

//    @Resource
//    private InteractionIndexService interactionIndexService;

//    @Resource
//    private TermIndexService termsIndexService;

//    @Resource
//    private ClusteredInteractionIndexService clusteredInteractionIndexService;


    /**
     * It cleans the interaction index in solr
     * @param stepContribution
     * @param chunkContext
     * @return
     * @throws Exception
     */
    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {


        //By default the index it will not take into account contaminant proteins)
        Calendar cal = Calendar.getInstance();
        DateFormat df = new SimpleDateFormat("dd-MM-yy HH:mm:ss");
        cal.setTimeInMillis(System.currentTimeMillis());
        logger.info("Started cleaning process at: " + df.format(cal.getTime()));

        try {
            long start = System.currentTimeMillis();
            interactionIndexService.deleteAll();
            long end = System.currentTimeMillis();
            logger.info("Cleaning time [ms]: " + (end-start));
        } catch (Exception e) {
            cal.setTimeInMillis(System.currentTimeMillis());
            logger.error("Unexpected exception at: " + df.format(cal.getTime()) + " Exception: " + e.toString());
            e.printStackTrace();
        }

        return RepeatStatus.FINISHED;
    }

}
