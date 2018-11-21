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
import psidev.psi.mi.jami.model.Alias;
import psidev.psi.mi.jami.model.Interactor;
import psidev.psi.mi.jami.model.ParticipantEvidence;
import psidev.psi.mi.jami.model.Xref;
import psidev.psi.mi.jami.utils.XrefUtils;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphBinaryInteractionEvidence;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphFeatureEvidence;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphInteractor;
import uk.ac.ebi.intact.graphdb.services.GraphInteractorService;
import uk.ac.ebi.intact.search.interactor.model.SearchInteractor;
import uk.ac.ebi.intact.search.interactor.service.InteractorIndexService;
import utilities.SolrDocumentConverter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

@Component
public class InteractorIndexerTasklet implements Tasklet {

    private static final Log log = LogFactory.getLog(InteractorIndexerTasklet.class);


    private static final int pageSize = 10;

    private static final int MAX_PING_TIME = 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEPTH = 2;
    private int attempts = 0;

    @Resource
    private GraphInteractorService graphInteractorService;

    @Resource
    private InteractorIndexService interactorIndexService;

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
                    Set<String> interactionsIds = new HashSet<>();
                    long interactionCount = graphInteractor.getInteractions().size();
                    if (interactionCount > 0) {
                        for (GraphBinaryInteractionEvidence graphBinaryInteractionEvidence : graphInteractor.getInteractions()) {
                            interactionsIds.add(graphBinaryInteractionEvidence.getIdentifiers().iterator().next().getId());
                        }
                    } else {
                        log.warn("Interactor without interactions: " + graphInteractor.getAc());
                    }
                    searchInteractors.add(toSolrDocument(graphInteractor, interactionsIds, interactionCount));
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

    /**
     * Converts Graph interactor to Solr interactor
     * @param interactor
     * @param interactionIds
     * @param interactionCount
     * @return
     */
    private static SearchInteractor toSolrDocument(Interactor interactor, Set<String> interactionIds, long interactionCount) {
        SearchInteractor searchInteractor = new SearchInteractor();

        if (interactor instanceof GraphInteractor) { //|| interactor instanceof IntactInteractor if we add intact-jami
            searchInteractor.setInteractorId(XrefUtils.collectFirstIdentifierWithDatabase(interactor.getIdentifiers(), "MI:0469", "intact").getId());
            GraphInteractor graphInteractor = (GraphInteractor) interactor;
            Collection<GraphFeatureEvidence> featureEvidences = new ArrayList<GraphFeatureEvidence>();
            if (graphInteractor.getParticipantEvidences() != null) {
                for (ParticipantEvidence participantEvidence : graphInteractor.getParticipantEvidences()) {
                    if (participantEvidence.getFeatures() != null) {
                        featureEvidences.addAll(participantEvidence.getFeatures());
                    }
                }
            }
            searchInteractor.setFeatureShortLabels(!featureEvidences.isEmpty() ? SolrDocumentConverter.featuresShortlabelToSolrDocument(featureEvidences) : null);
        }

        //TODO Deal with complexes and sets

        searchInteractor.setInteractorName(interactor.getPreferredIdentifier().getId());
        searchInteractor.setDescription(interactor.getFullName());
        searchInteractor.setInteractorAlias(aliasToSolrDocument(interactor.getAliases()));
        searchInteractor.setInteractorAltIds(xrefToSolrDocument(interactor.getIdentifiers()));

        searchInteractor.setInteractorType(interactor.getInteractorType().getShortName());
        searchInteractor.setSpecies(interactor.getOrganism().getScientificName());
        searchInteractor.setTaxId(interactor.getOrganism().getTaxId());
        searchInteractor.setInteractorXrefs(xrefToSolrDocument(interactor.getXrefs()));
        searchInteractor.setInteractionCount(interactionIds.size());
        searchInteractor.setInteractionIds(interactionIds);


        return searchInteractor;
    }

    private static Set<String> aliasToSolrDocument(Collection<Alias> aliases) {

        Set<String> searchInteractorAliases = new HashSet<>();
        for (Alias alias : aliases) {
            searchInteractorAliases.add(alias.getName() + " (" + alias.getType() + ")");
        }
        return searchInteractorAliases;

    }

    private static Set<String> xrefToSolrDocument(Collection<Xref> xrefs) {

        Set<String> searchInteractorXrefs = new HashSet<>();
        for (Xref xref : xrefs) {
            searchInteractorXrefs.add(xref.getId() + " (" + xref.getDatabase().getShortName() + ")");
        }

        return searchInteractorXrefs;

    }

}
