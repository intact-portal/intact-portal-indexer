package uk.ac.ebi.intact.portal.indexer.interactor;

import org.springframework.stereotype.Component;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphInteractor;
import uk.ac.ebi.intact.graphdb.service.GraphInteractorService;

import javax.annotation.Resource;

/**
 * Created by anjali on 23/11/18.
 */
@Component
public class InteractorUtility {

    @Resource
    private GraphInteractorService graphInteractorService;

    public GraphInteractor fetchInteractorAccToType(GraphInteractor graphInteractor, int depth) {
        GraphInteractor oGraphInteractorA = graphInteractorService.findByAc(graphInteractor.getAc(), depth);
        return oGraphInteractorA;
    }
}
