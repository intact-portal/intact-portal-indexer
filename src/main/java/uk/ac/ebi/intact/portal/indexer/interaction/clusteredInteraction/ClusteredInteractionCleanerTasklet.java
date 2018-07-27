package uk.ac.ebi.intact.portal.indexer.interaction.clusteredInteraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;


@Component
public class ClusteredInteractionCleanerTasklet implements Tasklet {

    private static final Logger logger = LoggerFactory.getLogger(ClusteredInteractionCleanerTasklet.class);

     //TODO...
//    @Resource
//    private TermIndexService termsIndexService;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {


        //By default the index it will not take into account contaminant proteins)
        Calendar cal = Calendar.getInstance();
        DateFormat df = new SimpleDateFormat("dd-MM-yy HH:mm:ss");
        cal.setTimeInMillis(System.currentTimeMillis());
        logger.info("Started cleaning process at: " + df.format(cal.getTime()));

        try {
            long start = System.currentTimeMillis();
            //TODO...
            // interactionIndexService.deleteAll();
            long end = System.currentTimeMillis();
            logger.info("Cleaning time [ms]: " + (end - start));
        } catch (Exception e) {
            cal.setTimeInMillis(System.currentTimeMillis());
            logger.error("Unexpected exception at: " + df.format(cal.getTime()) + " Exception: " + e.toString());
            e.printStackTrace();
        }

        return RepeatStatus.FINISHED;
    }

}
