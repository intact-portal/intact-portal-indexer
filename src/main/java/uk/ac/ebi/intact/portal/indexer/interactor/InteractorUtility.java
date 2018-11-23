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

    @Resource
    private GraphProteinService graphProteinService;

    @Resource
    private GraphMoleculeService graphMoleculeService;

    @Resource
    private GraphNucleicAcidService graphNucleicAcidService;

    @Resource
    private GraphPolymerService graphPolymerService;

    @Resource
    private GraphGeneService graphGeneService;

    public  GraphInteractor fetchInteractorAccToType(GraphInteractor graphInteractor,int depth){
        if(graphInteractor instanceof GraphProtein) {
            Optional<GraphProtein> oGraphInteractorA = graphProteinService.findWithDepth(graphInteractor.getGraphId(), depth);
            return oGraphInteractorA.get();
        }else if (graphInteractor instanceof GraphGene) {
            Optional<GraphGene> oGraphInteractorA = graphGeneService.findWithDepth(graphInteractor.getGraphId(), depth);
            return oGraphInteractorA.get();
        } else if (graphInteractor instanceof GraphPolymer) {
            Optional<GraphPolymer> oGraphInteractorA = graphPolymerService.findWithDepth(graphInteractor.getGraphId(), depth);
            return oGraphInteractorA.get();
        } else if (graphInteractor instanceof GraphMolecule) {
            Optional<GraphMolecule> oGraphInteractorA = graphMoleculeService.findWithDepth(graphInteractor.getGraphId(), depth);
            return oGraphInteractorA.get();
        } else if (graphInteractor instanceof GraphNucleicAcid) {
            Optional<GraphNucleicAcid> oGraphInteractorA = graphNucleicAcidService.findWithDepth(graphInteractor.getGraphId(), depth);
            return oGraphInteractorA.get();
        }else{
            Optional<GraphInteractor> oGraphInteractorA = graphInteractorService.findWithDepth(graphInteractor.getUniqueKey(), depth);
            return oGraphInteractorA.get();
        }
    }
}
