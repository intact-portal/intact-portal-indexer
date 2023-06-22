package uk.ac.ebi.intact.portal.indexer.interactor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import psidev.psi.mi.jami.model.Organism;
import psidev.psi.mi.jami.model.Xref;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphBinaryInteractionEvidence;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphFeature;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphInteractor;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphParticipantEvidence;
import uk.ac.ebi.intact.graphdb.service.GraphInteractorService;
import uk.ac.ebi.intact.search.interactors.model.SearchInteractor;
import uk.ac.ebi.intact.search.interactors.service.InteractorIndexService;
import uk.ac.ebi.intact.style.service.StyleService;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static utilities.SolrDocumentConverterUtils.*;

@Component
public class InteractorIndexerTasklet implements Tasklet {

    private static final Log log = LogFactory.getLog(InteractorIndexerTasklet.class);

    private static final int PAGE_SIZE = 200;
    private static final int MAX_PING_TIME = 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEPTH = 0;
    private int attempts = 0;
    private boolean simulation = false;

    @Resource
    private GraphInteractorService graphInteractorService;

    @Resource
    private InteractorIndexService interactorIndexService;

    @Resource
    private StyleService styleService;

    @Resource
    private SolrClient solrClient;


    private static SearchInteractor toSolrDocument(GraphInteractor graphInteractor,
                                                   Collection<GraphBinaryInteractionEvidence> interactionEvidences,
                                                   StyleService styleService) {

        SearchInteractor searchInteractor = new SearchInteractor();

        //Ac
        searchInteractor.setInteractorAc(graphInteractor.getAc());

        //Features
        Collection<GraphFeature> featureEvidences = new ArrayList<>();
        if (graphInteractor.getParticipantEvidences() != null) {
            for (GraphParticipantEvidence participantEvidence : graphInteractor.getParticipantEvidences()) {
                if (participantEvidence.getFeatures() != null) {
                    featureEvidences.addAll(participantEvidence.getFeatures());
                }
            }

            searchInteractor.setInteractorFeatureShortLabels(featuresShortlabelToSolrDocument(featureEvidences));
            searchInteractor.setInteractorFeatureTypes(featuresTypeToSolrDocument(featureEvidences));

        }

        int interactionCount = interactionEvidences.size();
        Set<String> interactionsIds = new HashSet<>();
        Collection<Xref> interactionXrefs = new ArrayList<>();

        if (interactionCount > 0) {
            for (GraphBinaryInteractionEvidence binaryInteractionEvidence : interactionEvidences) {
                interactionsIds.add(binaryInteractionEvidence.getIdentifiers().iterator().next().getId());
                interactionXrefs.addAll(binaryInteractionEvidence.getXrefs());
            }
        } else {
            log.warn("Interactor without interactions: " + graphInteractor.getAc());
        }

        //TODO Deal with complexes and sets

        searchInteractor.setInteractorName(graphInteractor.getPreferredName());
        searchInteractor.setInteractorIntactName(graphInteractor.getShortName());
        searchInteractor.setInteractorPreferredIdentifier(graphInteractor.getPreferredIdentifier().getId());
        searchInteractor.setInteractorDescription(graphInteractor.getFullName());
        searchInteractor.setInteractorAlias(aliasesWithTypesToSolrDocument(graphInteractor.getAliases()));
        searchInteractor.setInteractorAliasNames(aliasesToSolrDocument(graphInteractor.getAliases()));
        searchInteractor.setInteractorAltIds(xrefsToSolrDocument(graphInteractor.getIdentifiers()));

        //TODO Refactor to avoid accessing several times to the same fields
        final String shortName = graphInteractor.getInteractorType().getShortName();
        searchInteractor.setInteractorType(shortName);

        final String miIdentifier = graphInteractor.getInteractorType().getMIIdentifier();
        searchInteractor.setInteractorTypeMIIdentifier(miIdentifier);

        // We add the interactor shape and the display name in indexing time to avoid remapping the results
        searchInteractor.setInteractorTypeMIIdentifierStyled(miIdentifier + "__" + shortName + "__" + styleService.getInteractorShape(miIdentifier));

        final Organism organism = graphInteractor.getOrganism();
        searchInteractor.setInteractorSpecies(organism != null ? organism.getScientificName() : null);
        searchInteractor.setInteractorTaxId(organism != null ? organism.getTaxId() : null);

        searchInteractor.setInteractorTaxIdStyled(organism != null ?
                organism.getTaxId() + "__" + organism.getScientificName() + "__"
                        + "#" + Integer.toHexString(styleService.getInteractorColor(String.valueOf(organism.getTaxId())).getRGB()).substring(2) : null);

        searchInteractor.setInteractorXrefs(xrefsToSolrDocument(graphInteractor.getXrefs()));
        searchInteractor.setInteractionCount(interactionCount);
        searchInteractor.setInteractionIds(interactionsIds);
        searchInteractor.setInteractionXrefs(xrefsToSolrDocument(interactionXrefs));

        return searchInteractor;
    }

    /**
     * It reads interactors from graph db and create interactor index in solr
     *
     * @param stepContribution
     * @param chunkContext
     * @return
     * @throws Exception
     */
    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {

//        try {
        log.info("Start indexing Interactors data");

        int pageNumber = 0;
        Page<GraphInteractor> graphInteractorPage;

        log.debug("Starting to retrieve data");
        // loop over the data in pages until we are done with all
        long totalTime = System.currentTimeMillis();

        do {
            log.info("Retrieving page : " + pageNumber);
            long dbStart = System.currentTimeMillis();
            graphInteractorPage = graphInteractorService.findAll(PageRequest.of(pageNumber, PAGE_SIZE), DEPTH);
            log.info("Main DB query took [ms] : " + (System.currentTimeMillis() - dbStart));

            List<GraphInteractor> interactorList = graphInteractorPage.getContent();
            List<SearchInteractor> searchInteractors = new ArrayList<>();

            long convStart = System.currentTimeMillis();
            for (GraphInteractor graphInteractor : interactorList) {
                try {
                    searchInteractors.add(toSolrDocument(graphInteractor, graphInteractor.getInteractions(), styleService));
                } catch (Exception e) {
                    log.error("Interactor with ac: " + graphInteractor.getAc() + " could not be indexed because of exception  :- ");
                    e.printStackTrace();
                }
            }
            log.info("Conversion of " + searchInteractors.size() + " records took [ms] : " + (System.currentTimeMillis() - convStart));

            long indexStart = System.currentTimeMillis();

            if (!simulation) {
//                    solrServerCheck();

                log.info("Saving " + searchInteractors.size() + " interactors");
                interactorIndexService.saveAll(searchInteractors, Duration.ofSeconds(5));
                log.info("Index save took [ms] : " + (System.currentTimeMillis() - indexStart));
            }

            // increase the page number
            pageNumber++;
        } while (graphInteractorPage.hasNext());

        log.info("Indexing complete.");
        log.info("Total indexing took [ms] : " + (System.currentTimeMillis() - totalTime));

//        } catch (Exception e) {
//            System.out.println("Unexpected exception: " + e.toString());
//            e.printStackTrace();
//        }

        return RepeatStatus.FINISHED;
    }

    public void setSimulation(boolean simulation) {
        this.simulation = simulation;
    }

    private void solrServerCheck() {
        if (attempts < MAX_ATTEMPTS) {
            try {
                //TODO Ping method gives an error so solrServerCheck() is not call for now
                SolrPingResponse response = solrClient.ping();
                long elapsedTime = response.getElapsedTime();
                if (elapsedTime > MAX_PING_TIME) {
                    log.debug("Solr response too slow: " + elapsedTime + ". Attempt: " + attempts + ". Waiting... ");
                    //Wait 30 seconds before next attemp
                    Thread.sleep(30 * 1000);
                    attempts++;
                } else {
                    attempts = 0;
                }
            } catch (SolrServerException | IOException | InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            throw new IllegalStateException("Solr server not responding in time. Aborting.");
        }
    }

}
