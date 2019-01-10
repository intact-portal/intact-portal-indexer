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
import uk.ac.ebi.intact.graphdb.model.nodes.*;
import uk.ac.ebi.intact.graphdb.services.GraphExperimentService;
import uk.ac.ebi.intact.graphdb.services.GraphInteractionService;
import uk.ac.ebi.intact.graphdb.services.GraphInteractorService;
import uk.ac.ebi.intact.search.interactor.model.SearchInteractor;
import uk.ac.ebi.intact.search.interactor.service.InteractorIndexService;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

import static utilities.SolrDocumentConverterUtils.*;

@Component
public class InteractorIndexerTasklet implements Tasklet {

    private static final Log log = LogFactory.getLog(InteractorIndexerTasklet.class);


    private static final int pageSize = 1000;

    private static final int MAX_PING_TIME = 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEPTH = 2;
    private int attempts = 0;

    @Resource
    private GraphInteractorService graphInteractorService;

    @Resource
    private InteractorIndexService interactorIndexService;

    @Resource
    private GraphExperimentService graphExperimentService;

    @Resource
    private GraphInteractionService graphInteractionService;

    @Resource
    private SolrClient solrClient;


    private boolean simulation = false;


    /**
     * It reads interactors from graph db and create interactor index in solr
     * @param stepContribution
     * @param chunkContext
     * @return
     * @throws Exception
     */
    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {

        try {
            log.info("Start indexing Interactors data");

            int page = 0;
            PageRequest request;
            boolean done = false;

            log.debug("Starting to retrieve data");
            // loop over the data in pages until we are done with all
            long totalTime = System.currentTimeMillis();

            while (!done) {
                log.info("Retrieving page : " + page);
                request = new PageRequest(page, pageSize);

                long dbStart = System.currentTimeMillis();

                Page<GraphInteractor> graphInteractorPage = graphInteractorService.findAll(request, DEPTH);
                log.info("\tMain DB query took [ms] : " + (System.currentTimeMillis() - dbStart));

                done = (page >= graphInteractorPage.getTotalPages() - 1); // stop criteria when using paged results
//                done = (page >= 9); // testing with 10 pages (0-9)

                List<GraphInteractor> interactorList = graphInteractorPage.getContent();
                List<SearchInteractor> searchInteractors = new ArrayList<>();

                long convStart = System.currentTimeMillis();
                for (GraphInteractor graphInteractor : interactorList) {
                    searchInteractors.add(toSolrDocument(graphInteractor, graphInteractor.getInteractions(),
                            graphExperimentService, graphInteractionService));
                }
                log.info("\tConversion of " + searchInteractors.size() + " records took [ms] : " + (System.currentTimeMillis() - convStart));

                long indexStart = System.currentTimeMillis();

                if (!simulation) {
//                    solrServerCheck();

                    interactorIndexService.saveAll(searchInteractors);
                    log.info("\tIndex save took [ms] : " + (System.currentTimeMillis() - indexStart));
                }

                // increase the page number
                page++;
            }

            log.info("Indexing complete.");
            log.info("\tTotal indexing took [ms] : " + (System.currentTimeMillis() - totalTime));

        } catch (Exception e) {
            System.out.println("Unexpected exception: " + e.toString());
            e.printStackTrace();
        }

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


    private static SearchInteractor toSolrDocument(GraphInteractor graphInteractor,
                                                   Collection<GraphBinaryInteractionEvidence> interactionEvidences,
                                                   GraphExperimentService graphExperimentService,
                                                   GraphInteractionService graphInteractionService) {

        SearchInteractor searchInteractor = new SearchInteractor();

        //Id
        searchInteractor.setInteractorId(graphInteractor.getAc());

        //Features
        Collection<GraphFeatureEvidence> featureEvidences = new ArrayList<>();
        if (graphInteractor.getParticipantEvidences() != null) {
            for (GraphParticipantEvidence participantEvidence : graphInteractor.getParticipantEvidences()) {
                if (participantEvidence.getFeatures() != null) {
                    featureEvidences.addAll(participantEvidence.getFeatures());
                }
            }

            searchInteractor.setFeatureShortLabels(featuresShortlabelToSolrDocument(featureEvidences));
//            searchInteractor.setFeatureShortLabels(!featureEvidences.isEmpty() ? featuresShortlabelToSolrDocument(featureEvidences) : null);
        }

        int interactionCount = interactionEvidences.size();
        Set<String> interactionsIds = new HashSet<>();
        Set<String> interactionDetectionMethods = new HashSet<>();
        Set<String> interactionsType = new HashSet<>();
        Set<String> interactionExpansionMethods = new HashSet<>();
        Set<String> interactionsAc = new HashSet<>();
        Set<String> interactionHostOrganism = new HashSet<>();
        Set<Boolean> interactionNegative = new HashSet<>();
        Set<Double> interactionMiScore = new HashSet<>();

        if (interactionCount > 0) {
            for (GraphBinaryInteractionEvidence binaryInteractionEvidence : interactionEvidences) {
                interactionsIds.add(binaryInteractionEvidence.getIdentifiers().iterator().next().getId());
                interactionsType.add(cvTermToSolrDocument(binaryInteractionEvidence.getInteractionType()));
                interactionsAc.add(binaryInteractionEvidence.getAc());
                GraphExperiment experiment =
                        graphExperimentService.findByAc(((GraphExperiment)binaryInteractionEvidence.getExperiment()).getAc());
                interactionDetectionMethods.add(cvTermToSolrDocument(experiment.getInteractionDetectionMethod()));
                interactionHostOrganism.add(experiment.getHostOrganism().getScientificName());
                interactionExpansionMethods.add(cvTermToSolrDocument(binaryInteractionEvidence.getComplexExpansion()));
                interactionNegative.add(binaryInteractionEvidence.isNegative());

                GraphClusteredInteraction graphClusteredInteraction =
                        graphInteractionService.findClusteredInteraction(binaryInteractionEvidence.getUniqueKey());

                if (graphClusteredInteraction != null ) {
                    interactionMiScore.add(graphClusteredInteraction.getMiscore());
                }
            }
        } else {
            log.warn("Interactor without interactions: " + graphInteractor.getAc());
        }

        //TODO Deal with complexes and sets

        searchInteractor.setInteractorName(graphInteractor.getPreferredIdentifier().getId());
        searchInteractor.setDescription(graphInteractor.getFullName());
        searchInteractor.setInteractorAlias(aliasesToSolrDocument(graphInteractor.getAliases()));
        searchInteractor.setInteractorAltIds(xrefsToSolrDocument(graphInteractor.getIdentifiers()));

        searchInteractor.setInteractorType(graphInteractor.getInteractorType().getShortName());
        searchInteractor.setSpecies(graphInteractor.getOrganism().getScientificName());
        searchInteractor.setTaxId(graphInteractor.getOrganism().getTaxId());
        searchInteractor.setInteractorXrefs(xrefsToSolrDocument(graphInteractor.getXrefs()));
        searchInteractor.setInteractionCount(interactionCount);
        searchInteractor.setInteractionIds(interactionsIds);

        searchInteractor.setInteractionType(interactionsType);
        searchInteractor.setInteractionAc(interactionsAc);
        searchInteractor.setInteractionDetectionMethod(interactionDetectionMethods);
        searchInteractor.setInteractionExpansionMethod(interactionExpansionMethods);
        searchInteractor.setInteractionHostOrganism(interactionHostOrganism);
        searchInteractor.setInteractionNegative(interactionNegative);
        searchInteractor.setInteractionMiScore(interactionMiScore);

        return searchInteractor;
    }

}
