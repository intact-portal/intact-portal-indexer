package uk.ac.ebi.intact.portal.indexer.interaction;

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
import psidev.psi.mi.jami.binary.BinaryInteractionEvidence;
import psidev.psi.mi.jami.model.Alias;
import psidev.psi.mi.jami.model.Interactor;
import psidev.psi.mi.jami.model.Xref;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphBinaryInteractionEvidence;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphInteractor;
import uk.ac.ebi.intact.graphdb.services.GraphInteractionService;
import uk.ac.ebi.intact.graphdb.services.GraphInteractorService;
import uk.ac.ebi.intact.search.interactions.model.Interaction;
import uk.ac.ebi.intact.search.interactions.service.InteractionIndexService;
import utilities.SolrDocumentConverter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

@Component
public class InteractionIndexerTasklet implements Tasklet {

    private static final Log log = LogFactory.getLog(InteractionIndexerTasklet.class);


    private static final int pageSize = 10;

    private static final int MAX_PING_TIME = 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEPTH = 2;
    private int attempts = 0;

    @Resource
    private GraphInteractionService graphInteractionService;

    @Resource
    private GraphInteractorService graphInteractorService;

    @Resource
    private InteractionIndexService interactionIndexService;

    @Resource
    private SolrClient interactorsSolrServer;

    private boolean simulation = false;

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

                Page<GraphBinaryInteractionEvidence> graphInteractionPage = graphInteractionService.findAll(request, DEPTH);
                log.info("\tMain DB query took [ms] : " + (System.currentTimeMillis() - dbStart));

                done = (page >= graphInteractionPage.getTotalPages() - 1); // stop criteria when using paged results
//                done = (page >= 9); // testing with 10 pages (0-9)

                List<GraphBinaryInteractionEvidence> interactionList = graphInteractionPage.getContent();
                List<Interaction> interactions = new ArrayList<>();

                long convStart = System.currentTimeMillis();
                for (GraphBinaryInteractionEvidence graphInteraction : interactionList) {
                    Set<String> interactionsIds = new HashSet<>();
                    interactions.add(toSolrDocument(graphInteraction));
                }
                log.info("\tConversion of " + interactions.size() + " records took [ms] : " + (System.currentTimeMillis() - convStart));

                long indexStart = System.currentTimeMillis();

                if (!simulation) {
//                    solrServerCheck();

                    interactionIndexService.save(interactions.get(0));
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
                SolrPingResponse response = interactorsSolrServer.ping();
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

    private Interaction toSolrDocument(BinaryInteractionEvidence interactionEvidence) {
        Interaction interaction = new Interaction();


        if (interactionEvidence instanceof GraphBinaryInteractionEvidence ) {
            GraphBinaryInteractionEvidence graphBinaryInteractionEvidence=(GraphBinaryInteractionEvidence)interactionEvidence;
            interaction.setUniqueKey(graphBinaryInteractionEvidence.getUniqueKey());

            Optional<GraphInteractor> oGraphInteractorA=graphInteractorService.findWithDepth(graphBinaryInteractionEvidence.getInteractorA().getUniqueKey(),DEPTH);
            Interactor graphInteractorA=oGraphInteractorA.get();
            interaction.setIdA(SolrDocumentConverter.xrefToSolrDocument(graphInteractorA.getPreferredIdentifier()));
            interaction.setAltIdsA(SolrDocumentConverter.xrefsToSolrDocument(graphInteractorA.getIdentifiers()));
            interaction.setAliasesA(SolrDocumentConverter.aliasesToSolrDocument(graphInteractorA.getAliases()));
            interaction.setAnnotationsA(SolrDocumentConverter.annotationsToSolrDocument(graphInteractorA.getAnnotations()));
            interaction.setChecksumsA(SolrDocumentConverter.checksumsToSolrDocument(graphInteractorA.getChecksums()));
            interaction.setTaxIdA(graphInteractorA.getOrganism().getTaxId());
            interaction.setTypeA(graphInteractorA.getInteractorType().getShortName());
            interaction.setXrefsA(SolrDocumentConverter.xrefsToSolrDocument(graphInteractorA.getXrefs()));
            interaction.setSpeciesA(graphInteractorA.getOrganism().getScientificName());

            Optional<GraphInteractor> oGraphInteractorB=graphInteractorService.findWithDepth(graphBinaryInteractionEvidence.getInteractorB().getUniqueKey(),DEPTH);
            Interactor graphInteractorB=oGraphInteractorB.get();

            interaction.setIdB(SolrDocumentConverter.xrefToSolrDocument(graphInteractorB.getPreferredIdentifier()));
            interaction.setAltIdsB(SolrDocumentConverter.xrefsToSolrDocument(graphInteractorB.getIdentifiers()));
            interaction.setAliasesB(SolrDocumentConverter.aliasesToSolrDocument(graphInteractorB.getAliases()));
            interaction.setAnnotationsB(SolrDocumentConverter.annotationsToSolrDocument(graphInteractorB.getAnnotations()));
            interaction.setChecksumsB(SolrDocumentConverter.checksumsToSolrDocument(graphInteractorB.getChecksums()));
            interaction.setTaxIdB(graphInteractorB.getOrganism().getTaxId());
            interaction.setTypeB(graphInteractorB.getInteractorType().getShortName());
            interaction.setXrefsB(SolrDocumentConverter.xrefsToSolrDocument(graphInteractorB.getXrefs()));
            interaction.setSpeciesB(graphInteractorB.getOrganism().getScientificName());
        }


        return interaction;
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
