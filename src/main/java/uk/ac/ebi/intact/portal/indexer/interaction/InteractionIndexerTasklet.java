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
import uk.ac.ebi.intact.graphdb.model.nodes.*;
import uk.ac.ebi.intact.graphdb.services.*;
import uk.ac.ebi.intact.search.interactions.model.Interaction;
import uk.ac.ebi.intact.search.interactions.service.InteractionIndexService;
import utilities.CommonUtility;
import utilities.Constants;
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
    private static final int DEPTH_3 = 3;
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
    private GraphExprimentService graphExprimentService;

    @Resource
    private GraphFeatureService graphFeatureService;

    @Resource
    private GraphPublicationService graphPublicationService;

    @Resource
    private SolrClient solrClient;

    private boolean simulation = false;

    /**
     * It reads interactions from graph db and create interaction index in solr
     *
     * @param stepContribution
     * @param chunkContext
     * @return
     * @throws Exception
     */
    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {

        try {
            log.info("Start indexing Interaction data");

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
     * Converts Graph interaction to Solr interaction and extract more depth details from graph db if needed.
     *
     * @param interactionEvidence
     * @return
     */
    // TODO try to split this method into methods for specific(eg. Interactor/publication/participant etc.) details
    private Interaction toSolrDocument(BinaryInteractionEvidence interactionEvidence) {
        Interaction interaction = new Interaction();
        List<GraphAnnotation> graphAnnotations = new ArrayList<GraphAnnotation>();
        List<GraphAlias> graphAliasesA = new ArrayList<GraphAlias>();
        List<GraphAlias> graphAliasesB = new ArrayList<GraphAlias>();

        if (interactionEvidence instanceof GraphBinaryInteractionEvidence) {
            GraphBinaryInteractionEvidence graphBinaryInteractionEvidence = (GraphBinaryInteractionEvidence) interactionEvidence;
            interaction.setUniqueKey(graphBinaryInteractionEvidence.getUniqueKey());

            // Interactor details
            if (graphBinaryInteractionEvidence.getInteractorA() != null) {
                Optional<GraphInteractor> oGraphInteractorA = graphInteractorService.findWithDepth(graphBinaryInteractionEvidence.getInteractorA().getUniqueKey(), DEPTH);
                GraphInteractor graphInteractorA = oGraphInteractorA.get();
                interaction.setIdA(SolrDocumentConverter.xrefToSolrDocument(graphInteractorA.getPreferredIdentifier()));
                interaction.setAltIdsA((graphInteractorA.getIdentifiers() != null && graphInteractorA.getIdentifiers().size() > 0) ? SolrDocumentConverter.xrefsToSolrDocument(graphInteractorA.getIdentifiers()) : null);
                graphAliasesA.addAll(graphInteractorA.getAliases());
                interaction.setAnnotationsA((graphInteractorA.getAnnotations() != null && graphInteractorA.getAnnotations().size() > 0) ? SolrDocumentConverter.annotationsToSolrDocument(graphInteractorA.getAnnotations()) : null);
                interaction.setChecksumsA((graphInteractorA.getChecksums() != null && graphInteractorA.getChecksums().size() > 0) ? SolrDocumentConverter.checksumsToSolrDocument(graphInteractorA.getChecksums()) : null);
                interaction.setTaxIdA(graphInteractorA.getOrganism().getTaxId());
                interaction.setTypeA(graphInteractorA.getInteractorType().getShortName());
                interaction.setXrefsA((graphInteractorA.getXrefs() != null && graphInteractorA.getXrefs().size() > 0) ? SolrDocumentConverter.xrefsToSolrDocument(graphInteractorA.getXrefs()) : null);
                interaction.setSpeciesA(graphInteractorA.getOrganism().getScientificName());
                interaction.setInteractorAAc(graphInteractorA.getAc());
                interaction.setUniqueIdA((graphInteractorA.getInteractorType() != null
                        && graphInteractorA.getInteractorType().getShortName() != null
                        && graphInteractorA.getInteractorType().getShortName().equals(Constants.MOLECULE_SET)) ? graphInteractorA.getAc() : graphInteractorA.getPreferredIdentifier() != null ? graphInteractorA.getPreferredIdentifier().getId() : "");
                interaction.setMoleculeA(graphInteractorA.getPreferredName());

            }

            if (graphBinaryInteractionEvidence.getInteractorB() != null) {
                Optional<GraphInteractor> oGraphInteractorB = graphInteractorService.findWithDepth(graphBinaryInteractionEvidence.getInteractorB().getUniqueKey(), DEPTH);
                GraphInteractor graphInteractorB = oGraphInteractorB.get();

                interaction.setIdB(SolrDocumentConverter.xrefToSolrDocument(graphInteractorB.getPreferredIdentifier()));
                interaction.setAltIdsB((graphInteractorB.getIdentifiers() != null && graphInteractorB.getIdentifiers().size() > 0) ? SolrDocumentConverter.xrefsToSolrDocument(graphInteractorB.getIdentifiers()) : null);
                graphAliasesB.addAll(graphInteractorB.getAliases());
                interaction.setAnnotationsB((graphInteractorB.getAnnotations() != null && graphInteractorB.getAnnotations().size() > 0) ? SolrDocumentConverter.annotationsToSolrDocument(graphInteractorB.getAnnotations()) : null);
                interaction.setChecksumsB((graphInteractorB.getChecksums() != null && graphInteractorB.getChecksums().size() > 0) ? SolrDocumentConverter.checksumsToSolrDocument(graphInteractorB.getChecksums()) : null);
                interaction.setTaxIdB(graphInteractorB.getOrganism().getTaxId());
                interaction.setTypeB(graphInteractorB.getInteractorType().getShortName());
                interaction.setXrefsB((graphInteractorB.getXrefs() != null && graphInteractorB.getXrefs().size() > 0) ? SolrDocumentConverter.xrefsToSolrDocument(graphInteractorB.getXrefs()) : null);
                interaction.setSpeciesB(graphInteractorB.getOrganism().getScientificName());
                interaction.setInteractorBAc(graphInteractorB.getAc());
                interaction.setUniqueIdB((graphInteractorB.getInteractorType() != null
                        && graphInteractorB.getInteractorType().getShortName() != null
                        && graphInteractorB.getInteractorType().getShortName().equals(Constants.MOLECULE_SET)) ? graphInteractorB.getAc()
                        : graphInteractorB.getPreferredIdentifier() != null ? graphInteractorB.getPreferredIdentifier().getId() : "");
                interaction.setMoleculeB(graphInteractorB.getPreferredName());
            }

            //participant details
            if (graphBinaryInteractionEvidence.getParticipantA() != null) {
                Optional<GraphParticipantEvidence> ographParticpantA = graphParticipantService.findWithDepth(((GraphParticipantEvidence) graphBinaryInteractionEvidence.getParticipantA()).getUniqueKey(), DEPTH);
                GraphParticipantEvidence graphParticipantEvidenceA = ographParticpantA.get();

                interaction.setBiologicalRoleA(graphParticipantEvidenceA.getBiologicalRole().getShortName());
                interaction.setExperimentalRoleA(graphParticipantEvidenceA.getExperimentalRole().getShortName());
                interaction.setExperimentalPreparationsA((graphParticipantEvidenceA.getExperimentalPreparations() != null && !graphParticipantEvidenceA.getExperimentalPreparations().isEmpty()) ? SolrDocumentConverter.cvTermsToSolrDocument(graphParticipantEvidenceA.getExperimentalPreparations()) : null);
                interaction.setStoichiometryA(graphParticipantEvidenceA.getStoichiometry() != null ? graphParticipantEvidenceA.getStoichiometry().getMinValue() + "-" + graphParticipantEvidenceA.getStoichiometry().getMaxValue() : null);
                interaction.setIdentificationMethodA((graphParticipantEvidenceA.getIdentificationMethods() != null && !graphParticipantEvidenceA.getIdentificationMethods().isEmpty()) ? SolrDocumentConverter.cvTermsToSolrDocument(graphParticipantEvidenceA.getIdentificationMethods()) : null);
                graphAliasesA.addAll(graphParticipantEvidenceA.getAliases());

                // featureDetails
                List<GraphFeatureEvidence> ographFeaturesA = graphFeatureService.findByUniqueKeyIn(CommonUtility.getGraphObjectsUniqueKeys(graphParticipantEvidenceA.getFeatures()), DEPTH);
                interaction.setFeatureA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? SolrDocumentConverter.featuresToSolrDocument(ographFeaturesA) : null);
                interaction.setFeatureShortLabelA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? SolrDocumentConverter.featuresShortlabelToSolrDocument(ographFeaturesA) : null);
            }

            if (graphBinaryInteractionEvidence.getParticipantB() != null) {
                Optional<GraphParticipantEvidence> ographParticpantB = graphParticipantService.findWithDepth(((GraphParticipantEvidence) graphBinaryInteractionEvidence.getParticipantB()).getUniqueKey(), DEPTH);
                GraphParticipantEvidence graphParticipantEvidenceB = ographParticpantB.get();

                interaction.setBiologicalRoleB(graphParticipantEvidenceB.getBiologicalRole().getShortName());
                interaction.setExperimentalRoleB(graphParticipantEvidenceB.getExperimentalRole().getShortName());
                interaction.setExperimentalPreparationsB((graphParticipantEvidenceB.getExperimentalPreparations() != null && !graphParticipantEvidenceB.getExperimentalPreparations().isEmpty()) ? SolrDocumentConverter.cvTermsToSolrDocument(graphParticipantEvidenceB.getExperimentalPreparations()) : null);
                interaction.setStoichiometryB(graphParticipantEvidenceB.getStoichiometry() != null ? graphParticipantEvidenceB.getStoichiometry().getMinValue() + "-" + graphParticipantEvidenceB.getStoichiometry().getMaxValue() : null);
                interaction.setIdentificationMethodB((graphParticipantEvidenceB.getIdentificationMethods() != null && !graphParticipantEvidenceB.getIdentificationMethods().isEmpty()) ? SolrDocumentConverter.cvTermsToSolrDocument(graphParticipantEvidenceB.getIdentificationMethods()) : null);
                graphAliasesB.addAll(graphParticipantEvidenceB.getAliases());

                // featureDetails
                List<GraphFeatureEvidence> ographFeaturesB = graphFeatureService.findByUniqueKeyIn(CommonUtility.getGraphObjectsUniqueKeys(graphParticipantEvidenceB.getFeatures()), DEPTH);
                interaction.setFeatureB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? SolrDocumentConverter.featuresToSolrDocument(ographFeaturesB) : null);
                interaction.setFeatureShortLabelB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? SolrDocumentConverter.featuresShortlabelToSolrDocument(ographFeaturesB) : null);
            }

            interaction.setAliasesA(!graphAliasesA.isEmpty() ? SolrDocumentConverter.aliasesToSolrDocument(graphAliasesA) : null);
            interaction.setAliasesB(!graphAliasesB.isEmpty() ? SolrDocumentConverter.aliasesToSolrDocument(graphAliasesB) : null);

            //experiment details
            Optional<GraphExperiment> oGraphExperiment = graphExprimentService.findWithDepth(((GraphExperiment) graphBinaryInteractionEvidence.getExperiment()).getUniqueKey(), DEPTH);
            GraphExperiment experiment = oGraphExperiment.get();

            graphAnnotations.addAll(experiment.getAnnotations());
            interaction.setInteractionDetectionMethod((graphBinaryInteractionEvidence.getExperiment() != null && graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod() != null) ? graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod().getShortName() : null);
            interaction.setHostOrganism(experiment.getHostOrganism() != null ? experiment.getHostOrganism().getScientificName() : null);


            //interaction details

            GraphClusteredInteraction graphClusteredInteraction = graphInteractionService.findClusteredInteraction(graphBinaryInteractionEvidence.getUniqueKey());
            Set<String> intactConfidence = new HashSet<String>();
            if (graphClusteredInteraction != null) {
                intactConfidence.add("intact-miscore:" + graphClusteredInteraction.getMiscore());
                interaction.setIntactMiscore(graphClusteredInteraction.getMiscore());
            }
            interaction.setInteractionIdentifiers((graphBinaryInteractionEvidence.getIdentifiers() != null && !graphBinaryInteractionEvidence.getIdentifiers().isEmpty()) ? SolrDocumentConverter.xrefsToSolrDocument(graphBinaryInteractionEvidence.getIdentifiers()) : null);
            interaction.setConfidenceValues(!intactConfidence.isEmpty() ? intactConfidence : null);
            interaction.setInteractionType(graphBinaryInteractionEvidence.getInteractionType() != null ? graphBinaryInteractionEvidence.getInteractionType().getShortName() : null);
            interaction.setInteractionAc(graphBinaryInteractionEvidence.getAc());

            if (graphBinaryInteractionEvidence.getConfidences() != null) {
                Set<String> confidences = SolrDocumentConverter.confidencesToSolrDocument(graphBinaryInteractionEvidence.getConfidences());
                if (interaction.getConfidenceValues() != null) {
                    interaction.getConfidenceValues().addAll(confidences);
                } else {
                    interaction.setConfidenceValues(confidences);
                }
            }

            graphAnnotations.addAll(graphBinaryInteractionEvidence.getAnnotations());
            interaction.setExpansionMethod((graphBinaryInteractionEvidence.getComplexExpansion() != null) ? graphBinaryInteractionEvidence.getComplexExpansion().getShortName() : null);
            interaction.setInteractionXrefs((graphBinaryInteractionEvidence.getXrefs() != null && !graphBinaryInteractionEvidence.getXrefs().isEmpty()) ? SolrDocumentConverter.xrefsToSolrDocument(graphBinaryInteractionEvidence.getXrefs()) : null);
            interaction.setInteractionParameters((graphBinaryInteractionEvidence.getParameters() != null && !graphBinaryInteractionEvidence.getParameters().isEmpty()) ? SolrDocumentConverter.parametersToSolrDocument(graphBinaryInteractionEvidence.getParameters()) : null);
            interaction.setCreationDate(graphBinaryInteractionEvidence.getCreatedDate());
            interaction.setUpdationDate(graphBinaryInteractionEvidence.getUpdatedDate());
            interaction.setInteractionChecksums((graphBinaryInteractionEvidence.getChecksums() != null && !graphBinaryInteractionEvidence.getChecksums().isEmpty()) ? SolrDocumentConverter.checksumsToSolrDocument(graphBinaryInteractionEvidence.getChecksums()) : null);
            interaction.setNegative(graphBinaryInteractionEvidence.isNegative());

            // publications details

            if (experiment != null) {
                Optional<GraphPublication> oGraphPublication = graphPublicationService.findWithDepth(((GraphPublication) experiment.getPublication()).getUniqueKey(), DEPTH);
                GraphPublication publication = oGraphPublication.get();
                graphAnnotations.addAll(publication.getAnnotations());
                if (publication != null) {
                    interaction.setAuthors((publication.getAuthors() != null && !publication.getAuthors().isEmpty()) ? new LinkedHashSet(publication.getAuthors()) : null);
                    interaction.setFirstAuthor((interaction.getAuthors() != null && !interaction.getAuthors().isEmpty()) ? interaction.getAuthors().iterator().next() + " et al. (" + CommonUtility.getYearOutOfDate(publication.getPublicationDate()) + ")" +
                            "\t\n" : "");
                    interaction.setPublicationId((publication.getAuthors() != null) ? publication.getPubmedId() : "");
                    interaction.setSourceDatabase((publication.getSource() != null) ? publication.getSource().getShortName() : "");
                    interaction.setReleaseDate((publication.getReleasedDate() != null) ? publication.getReleasedDate() : null);
                    interaction.setPublicationIdentifiers((publication.getIdentifiers() != null && !publication.getIdentifiers().isEmpty()) ? SolrDocumentConverter.xrefsToSolrDocument(publication.getIdentifiers()) : null);
                }
            }

            interaction.setInteractionAnnotations(!graphAnnotations.isEmpty() ? SolrDocumentConverter.annotationsToSolrDocument(graphAnnotations) : null);

        }


        return interaction;
    }


}
