package uk.ac.ebi.intact.portal.indexer.interactor;

import org.springframework.stereotype.Component;
import uk.ac.ebi.intact.graphdb.model.nodes.*;
import uk.ac.ebi.intact.graphdb.services.*;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * Created by anjali on 23/11/18.
 */
@Component
public class InteractorUtility {

    @Resource
    private GraphInteractorService graphInteractorService;

    public GraphInteractor fetchInteractorAccToType(GraphInteractor graphInteractor, int depth) {
            Optional<GraphInteractor> oGraphInteractorA = graphInteractorService.findByAc(graphInteractor.getAc(), depth);
            return oGraphInteractorA.get();
    }
}
