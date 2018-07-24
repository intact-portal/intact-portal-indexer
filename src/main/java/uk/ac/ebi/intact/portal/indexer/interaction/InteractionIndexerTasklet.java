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
import uk.ac.ebi.intact.graphdb.model.nodes.GraphParticipantEvidence;
import uk.ac.ebi.intact.graphdb.services.GraphInteractionService;
import uk.ac.ebi.intact.graphdb.services.GraphInteractorService;
import uk.ac.ebi.intact.graphdb.services.GraphParticipantService;
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
    private GraphParticipantService graphParticipantService;

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

                    interactionIndexService.save(interactions);
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


            // Interactor fields
            if(graphBinaryInteractionEvidence.getInteractorA()!=null) {
                Interactor graphInteractorA = oGraphInteractorA.get();
                interaction.setIdA(SolrDocumentConverter.xrefToSolrDocument(graphInteractorA.getPreferredIdentifier()));
                interaction.setAltIdsA((graphInteractorA.getIdentifiers() != null && graphInteractorA.getIdentifiers().size() > 0) ? SolrDocumentConverter.xrefsToSolrDocument(graphInteractorA.getIdentifiers()) : null);
                interaction.setAliasesA((graphInteractorA.getAliases() != null && graphInteractorA.getAliases().size() > 0) ? SolrDocumentConverter.aliasesToSolrDocument(graphInteractorA.getAliases()) : null);
                interaction.setAnnotationsA((graphInteractorA.getAnnotations() != null && graphInteractorA.getAnnotations().size() > 0) ? SolrDocumentConverter.annotationsToSolrDocument(graphInteractorA.getAnnotations()) : null);
                interaction.setChecksumsA((graphInteractorA.getChecksums() != null && graphInteractorA.getChecksums().size() > 0) ? SolrDocumentConverter.checksumsToSolrDocument(graphInteractorA.getChecksums()) : null);
                interaction.setTaxIdA(graphInteractorA.getOrganism().getTaxId());
                interaction.setTypeA(graphInteractorA.getInteractorType().getShortName());
                interaction.setXrefsA((graphInteractorA.getXrefs() != null && graphInteractorA.getXrefs().size() > 0) ? SolrDocumentConverter.xrefsToSolrDocument(graphInteractorA.getXrefs()) : null);
                interaction.setSpeciesA(graphInteractorA.getOrganism().getScientificName());
            }

            if(graphBinaryInteractionEvidence.getInteractorB()!=null) {
                Optional<GraphInteractor> oGraphInteractorB = graphInteractorService.findWithDepth(graphBinaryInteractionEvidence.getInteractorB().getUniqueKey(), DEPTH);
                Interactor graphInteractorB = oGraphInteractorB.get();

                interaction.setIdB(SolrDocumentConverter.xrefToSolrDocument(graphInteractorB.getPreferredIdentifier()));
                interaction.setAltIdsB((graphInteractorB.getIdentifiers() != null && graphInteractorB.getIdentifiers().size() > 0) ? SolrDocumentConverter.xrefsToSolrDocument(graphInteractorB.getIdentifiers()) : null);
                interaction.setAliasesB((graphInteractorB.getAliases() != null && graphInteractorB.getAliases().size() > 0) ? SolrDocumentConverter.aliasesToSolrDocument(graphInteractorB.getAliases()) : null);
                interaction.setAnnotationsB((graphInteractorB.getAnnotations() != null && graphInteractorB.getAnnotations().size() > 0) ? SolrDocumentConverter.annotationsToSolrDocument(graphInteractorB.getAnnotations()) : null);
                interaction.setChecksumsB((graphInteractorB.getChecksums() != null && graphInteractorB.getChecksums().size() > 0) ? SolrDocumentConverter.checksumsToSolrDocument(graphInteractorB.getChecksums()) : null);
                interaction.setTaxIdB(graphInteractorB.getOrganism().getTaxId());
                interaction.setTypeB(graphInteractorB.getInteractorType().getShortName());
                interaction.setXrefsB((graphInteractorB.getXrefs() != null && graphInteractorB.getXrefs().size() > 0) ? SolrDocumentConverter.xrefsToSolrDocument(graphInteractorB.getXrefs()) : null);
                interaction.setSpeciesB(graphInteractorB.getOrganism().getScientificName());
            }

            //participant fields

            if(graphBinaryInteractionEvidence.getParticipantA()!=null){
                Optional<GraphParticipantEvidence> ographParticpantA = graphParticipantService.findWithDepth(((GraphParticipantEvidence)graphBinaryInteractionEvidence.getParticipantA()).getUniqueKey(), DEPTH);
                GraphParticipantEvidence graphParticipantEvidenceA = ographParticpantA.get();

                interaction.setBiologicalRoleA(graphParticipantEvidenceA.getBiologicalRole().getShortName());
                interaction.setExperimentalRoleA(graphParticipantEvidenceA.getExperimentalRole().getShortName());
                interaction.setFeatureA((graphParticipantEvidenceA.getFeatures()!=null&&!graphParticipantEvidenceA.getFeatures().isEmpty())?SolrDocumentConverter.featuresToSolrDocument(graphParticipantEvidenceA.getFeatures()):null);
                interaction.setStoichiometryA(graphParticipantEvidenceA.getStoichiometry()!=null?graphParticipantEvidenceA.getStoichiometry().getMinValue()+"-"+graphParticipantEvidenceA.getStoichiometry().getMaxValue():null);
                interaction.setIdentificationMethodA((graphParticipantEvidenceA.getIdentificationMethods()!=null&&!graphParticipantEvidenceA.getIdentificationMethods().isEmpty())?SolrDocumentConverter.cvTermsToSolrDocument(graphParticipantEvidenceA.getIdentificationMethods()):null);

            }

            if(graphBinaryInteractionEvidence.getParticipantB()!=null){
                Optional<GraphParticipantEvidence> ographParticpantB = graphParticipantService.findWithDepth(((GraphParticipantEvidence)graphBinaryInteractionEvidence.getParticipantB()).getUniqueKey(), DEPTH);
                GraphParticipantEvidence graphParticipantEvidenceB = ographParticpantB.get();

                interaction.setBiologicalRoleB(graphParticipantEvidenceB.getBiologicalRole().getShortName());
                interaction.setExperimentalRoleB(graphParticipantEvidenceB.getExperimentalRole().getShortName());
                interaction.setFeatureB((graphParticipantEvidenceB.getFeatures()!=null&&!graphParticipantEvidenceB.getFeatures().isEmpty())?SolrDocumentConverter.featuresToSolrDocument(graphParticipantEvidenceB.getFeatures()):null);
                interaction.setStoichiometryB(graphParticipantEvidenceB.getStoichiometry()!=null?graphParticipantEvidenceB.getStoichiometry().getMinValue()+"-"+graphParticipantEvidenceB.getStoichiometry().getMaxValue():null);
                interaction.setIdentificationMethodB((graphParticipantEvidenceB.getIdentificationMethods()!=null&&!graphParticipantEvidenceB.getIdentificationMethods().isEmpty())?SolrDocumentConverter.cvTermsToSolrDocument(graphParticipantEvidenceB.getIdentificationMethods()):null);
            }

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
