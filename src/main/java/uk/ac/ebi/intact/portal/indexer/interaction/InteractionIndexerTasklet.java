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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import psidev.psi.mi.jami.binary.BinaryInteractionEvidence;
import uk.ac.ebi.intact.graphdb.model.nodes.*;
import uk.ac.ebi.intact.graphdb.services.GraphInteractionService;
import uk.ac.ebi.intact.graphdb.services.GraphParticipantService;
import uk.ac.ebi.intact.portal.indexer.interactor.InteractorUtility;
import uk.ac.ebi.intact.search.interactions.model.SearchInteraction;
import uk.ac.ebi.intact.search.interactions.service.InteractionIndexService;
import utilities.CommonUtility;
import utilities.Constants;
import utilities.SolrDocumentConverterUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

@Component
public class InteractionIndexerTasklet implements Tasklet {

    private static final Log log = LogFactory.getLog(InteractionIndexerTasklet.class);


    private static final int pageSize = 1000;

    private static final int MAX_PING_TIME = 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEPTH = 0;
    private static final int DEPTH_3 = 3;
    @Autowired
    InteractorUtility interactorUtility;
    private int attempts = 0;
    @Resource
    private GraphInteractionService graphInteractionService;
    @Resource
    private GraphParticipantService graphParticipantService;
    @Resource
    private InteractionIndexService interactionIndexService;
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
                log.info("Main DB query took [ms] : " + (System.currentTimeMillis() - dbStart));

                done = (page >= graphInteractionPage.getTotalPages() - 1); // stop criteria when using paged results
//                done = (page >= 9); // testing with 10 pages (0-9)

                List<GraphBinaryInteractionEvidence> interactionList = graphInteractionPage.getContent();
                List<SearchInteraction> interactions = new ArrayList<>();

                long convStart = System.currentTimeMillis();
                for (GraphBinaryInteractionEvidence graphInteraction : interactionList) {
                    Set<String> interactionsIds = new HashSet<>();
                    interactions.add(toSolrDocument(graphInteraction));
                }
                log.info("Conversion of " + interactions.size() + " records took [ms] : " + (System.currentTimeMillis() - convStart));

                long indexStart = System.currentTimeMillis();

                if (!simulation) {
//                    solrServerCheck();

                    interactionIndexService.save(interactions);
                    log.info("Index save took [ms] : " + (System.currentTimeMillis() - indexStart));
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
    private SearchInteraction toSolrDocument(BinaryInteractionEvidence interactionEvidence) {

        SearchInteraction searchInteraction = new SearchInteraction();

        List<GraphAnnotation> graphAnnotations = new ArrayList<GraphAnnotation>();
        List<GraphAlias> graphAliasesA = new ArrayList<GraphAlias>();
        List<GraphAlias> graphAliasesB = new ArrayList<GraphAlias>();

        if (interactionEvidence instanceof GraphBinaryInteractionEvidence) {
            GraphBinaryInteractionEvidence graphBinaryInteractionEvidence = (GraphBinaryInteractionEvidence) interactionEvidence;

            // Interactor details
            if (graphBinaryInteractionEvidence.getInteractorA() != null) {
                GraphInteractor graphInteractorA = (GraphInteractor) graphBinaryInteractionEvidence.getInteractorA();
                searchInteraction.setIdA(SolrDocumentConverterUtils.xrefToSolrDocument(graphInteractorA.getPreferredIdentifier()));
                searchInteraction.setAltIdsA((graphInteractorA.getIdentifiers() != null && graphInteractorA.getIdentifiers().size() > 0) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphInteractorA.getIdentifiers()) : null);
                graphAliasesA.addAll(graphInteractorA.getAliases());
                searchInteraction.setAnnotationsA((graphInteractorA.getAnnotations() != null && graphInteractorA.getAnnotations().size() > 0) ? SolrDocumentConverterUtils.annotationsToSolrDocument(graphInteractorA.getAnnotations()) : null);
                searchInteraction.setChecksumsA((graphInteractorA.getChecksums() != null && graphInteractorA.getChecksums().size() > 0) ? SolrDocumentConverterUtils.checksumsToSolrDocument(graphInteractorA.getChecksums()) : null);
                searchInteraction.setTaxIdA(graphInteractorA.getOrganism().getTaxId());
                searchInteraction.setTypeA(graphInteractorA.getInteractorType().getShortName());
                searchInteraction.setXrefsA((graphInteractorA.getXrefs() != null && graphInteractorA.getXrefs().size() > 0) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphInteractorA.getXrefs()) : null);
                searchInteraction.setSpeciesA(graphInteractorA.getOrganism().getScientificName());
                searchInteraction.setInteractorAAc(graphInteractorA.getAc());
                searchInteraction.setUniqueIdA((graphInteractorA.getInteractorType() != null
                        && graphInteractorA.getInteractorType().getShortName() != null
                        && graphInteractorA.getInteractorType().getShortName().equals(Constants.MOLECULE_SET)) ? graphInteractorA.getAc() : graphInteractorA.getPreferredIdentifier() != null ? graphInteractorA.getPreferredIdentifier().getId() : "");
                searchInteraction.setMoleculeA(graphInteractorA.getPreferredName());

            }

            if (graphBinaryInteractionEvidence.getInteractorB() != null) {
                GraphInteractor graphInteractorB = (GraphInteractor) graphBinaryInteractionEvidence.getInteractorB();

                searchInteraction.setIdB(SolrDocumentConverterUtils.xrefToSolrDocument(graphInteractorB.getPreferredIdentifier()));
                searchInteraction.setAltIdsB((graphInteractorB.getIdentifiers() != null && graphInteractorB.getIdentifiers().size() > 0) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphInteractorB.getIdentifiers()) : null);
                graphAliasesB.addAll(graphInteractorB.getAliases());
                searchInteraction.setAnnotationsB((graphInteractorB.getAnnotations() != null && graphInteractorB.getAnnotations().size() > 0) ? SolrDocumentConverterUtils.annotationsToSolrDocument(graphInteractorB.getAnnotations()) : null);
                searchInteraction.setChecksumsB((graphInteractorB.getChecksums() != null && graphInteractorB.getChecksums().size() > 0) ? SolrDocumentConverterUtils.checksumsToSolrDocument(graphInteractorB.getChecksums()) : null);
                searchInteraction.setTaxIdB(graphInteractorB.getOrganism().getTaxId());
                searchInteraction.setTypeB(graphInteractorB.getInteractorType().getShortName());
                searchInteraction.setXrefsB((graphInteractorB.getXrefs() != null && graphInteractorB.getXrefs().size() > 0) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphInteractorB.getXrefs()) : null);
                searchInteraction.setSpeciesB(graphInteractorB.getOrganism().getScientificName());
                searchInteraction.setInteractorBAc(graphInteractorB.getAc());
                searchInteraction.setUniqueIdB((graphInteractorB.getInteractorType() != null
                        && graphInteractorB.getInteractorType().getShortName() != null
                        && graphInteractorB.getInteractorType().getShortName().equals(Constants.MOLECULE_SET)) ? graphInteractorB.getAc()
                        : graphInteractorB.getPreferredIdentifier() != null ? graphInteractorB.getPreferredIdentifier().getId() : "");
                searchInteraction.setMoleculeB(graphInteractorB.getPreferredName());
            }

            //participant details
            if (graphBinaryInteractionEvidence.getParticipantA() != null) {
                GraphParticipantEvidence graphParticipantEvidenceA = (GraphParticipantEvidence) graphBinaryInteractionEvidence.getParticipantA();

                searchInteraction.setBiologicalRoleA(graphParticipantEvidenceA.getBiologicalRole().getShortName());
                searchInteraction.setExperimentalRoleA(graphParticipantEvidenceA.getExperimentalRole().getShortName());
                searchInteraction.setExperimentalPreparationsA((graphParticipantEvidenceA.getExperimentalPreparations() != null && !graphParticipantEvidenceA.getExperimentalPreparations().isEmpty()) ? SolrDocumentConverterUtils.cvTermsToSolrDocument(graphParticipantEvidenceA.getExperimentalPreparations()) : null);
                searchInteraction.setStoichiometryA(graphParticipantEvidenceA.getStoichiometry() != null ? graphParticipantEvidenceA.getStoichiometry().getMinValue() + "-" + graphParticipantEvidenceA.getStoichiometry().getMaxValue() : null);
                searchInteraction.setIdentificationMethodA((graphParticipantEvidenceA.getIdentificationMethods() != null && !graphParticipantEvidenceA.getIdentificationMethods().isEmpty()) ? SolrDocumentConverterUtils.cvTermsToSolrDocument(graphParticipantEvidenceA.getIdentificationMethods()) : null);
                graphAliasesA.addAll(graphParticipantEvidenceA.getAliases());

                // featureDetails
                List<GraphFeature> ographFeaturesA = (List<GraphFeature>) graphParticipantEvidenceA.getFeatures();
                searchInteraction.setFeatureA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? SolrDocumentConverterUtils.featuresToSolrDocument(ographFeaturesA) : null);
                searchInteraction.setFeatureShortLabelA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? SolrDocumentConverterUtils.featuresShortlabelToSolrDocument(ographFeaturesA) : null);
            }

            if (graphBinaryInteractionEvidence.getParticipantB() != null) {
                GraphParticipantEvidence graphParticipantEvidenceB = (GraphParticipantEvidence) graphBinaryInteractionEvidence.getParticipantB();

                searchInteraction.setBiologicalRoleB(graphParticipantEvidenceB.getBiologicalRole().getShortName());
                searchInteraction.setExperimentalRoleB(graphParticipantEvidenceB.getExperimentalRole().getShortName());
                searchInteraction.setExperimentalPreparationsB((graphParticipantEvidenceB.getExperimentalPreparations() != null && !graphParticipantEvidenceB.getExperimentalPreparations().isEmpty()) ? SolrDocumentConverterUtils.cvTermsToSolrDocument(graphParticipantEvidenceB.getExperimentalPreparations()) : null);
                searchInteraction.setStoichiometryB(graphParticipantEvidenceB.getStoichiometry() != null ? graphParticipantEvidenceB.getStoichiometry().getMinValue() + "-" + graphParticipantEvidenceB.getStoichiometry().getMaxValue() : null);
                searchInteraction.setIdentificationMethodB((graphParticipantEvidenceB.getIdentificationMethods() != null && !graphParticipantEvidenceB.getIdentificationMethods().isEmpty()) ? SolrDocumentConverterUtils.cvTermsToSolrDocument(graphParticipantEvidenceB.getIdentificationMethods()) : null);
                graphAliasesB.addAll(graphParticipantEvidenceB.getAliases());

                // featureDetails
                List<GraphFeature> ographFeaturesB = (List<GraphFeature>) graphParticipantEvidenceB.getFeatures();
                searchInteraction.setFeatureB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? SolrDocumentConverterUtils.featuresToSolrDocument(ographFeaturesB) : null);
                searchInteraction.setFeatureShortLabelB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? SolrDocumentConverterUtils.featuresShortlabelToSolrDocument(ographFeaturesB) : null);
            }

            searchInteraction.setAliasesA(!graphAliasesA.isEmpty() ? SolrDocumentConverterUtils.aliasesToSolrDocument(graphAliasesA) : null);
            searchInteraction.setAliasesB(!graphAliasesB.isEmpty() ? SolrDocumentConverterUtils.aliasesToSolrDocument(graphAliasesB) : null);

            //experiment details
            GraphExperiment experiment = (GraphExperiment) graphBinaryInteractionEvidence.getExperiment();


            graphAnnotations.addAll(experiment.getAnnotations());
            searchInteraction.setInteractionDetectionMethod((graphBinaryInteractionEvidence.getExperiment() != null && graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod() != null) ? graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod().getShortName() : null);
            searchInteraction.setHostOrganism(experiment.getHostOrganism() != null ? experiment.getHostOrganism().getScientificName() : null);


            //interaction details

            GraphClusteredInteraction graphClusteredInteraction = graphBinaryInteractionEvidence.getClusteredInteraction();
            Set<String> intactConfidence = new HashSet<String>();
            if (graphClusteredInteraction != null) {
                intactConfidence.add("intact-miscore:" + graphClusteredInteraction.getMiscore());
                searchInteraction.setIntactMiscore(graphClusteredInteraction.getMiscore());
            }
            searchInteraction.setInteractionIdentifiers((graphBinaryInteractionEvidence.getIdentifiers() != null && !graphBinaryInteractionEvidence.getIdentifiers().isEmpty()) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphBinaryInteractionEvidence.getIdentifiers()) : null);
            searchInteraction.setConfidenceValues(!intactConfidence.isEmpty() ? intactConfidence : null);
            searchInteraction.setInteractionType(graphBinaryInteractionEvidence.getInteractionType() != null ? graphBinaryInteractionEvidence.getInteractionType().getShortName() : null);
            searchInteraction.setInteractionAc(graphBinaryInteractionEvidence.getAc());

            if (graphBinaryInteractionEvidence.getConfidences() != null) {
                Set<String> confidences = SolrDocumentConverterUtils.confidencesToSolrDocument(graphBinaryInteractionEvidence.getConfidences());
                if (searchInteraction.getConfidenceValues() != null) {
                    searchInteraction.getConfidenceValues().addAll(confidences);
                } else {
                    searchInteraction.setConfidenceValues(confidences);
                }
            }

            graphAnnotations.addAll(graphBinaryInteractionEvidence.getAnnotations());
            searchInteraction.setExpansionMethod((graphBinaryInteractionEvidence.getComplexExpansion() != null) ? graphBinaryInteractionEvidence.getComplexExpansion().getShortName() : null);
            searchInteraction.setInteractionXrefs((graphBinaryInteractionEvidence.getXrefs() != null && !graphBinaryInteractionEvidence.getXrefs().isEmpty()) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphBinaryInteractionEvidence.getXrefs()) : null);
            searchInteraction.setInteractionParameters((graphBinaryInteractionEvidence.getParameters() != null && !graphBinaryInteractionEvidence.getParameters().isEmpty()) ? SolrDocumentConverterUtils.parametersToSolrDocument(graphBinaryInteractionEvidence.getParameters()) : null);
            searchInteraction.setCreationDate(graphBinaryInteractionEvidence.getCreatedDate());
            searchInteraction.setUpdationDate(graphBinaryInteractionEvidence.getUpdatedDate());
            searchInteraction.setInteractionChecksums((graphBinaryInteractionEvidence.getChecksums() != null && !graphBinaryInteractionEvidence.getChecksums().isEmpty()) ? SolrDocumentConverterUtils.checksumsToSolrDocument(graphBinaryInteractionEvidence.getChecksums()) : null);
            searchInteraction.setNegative(graphBinaryInteractionEvidence.isNegative());

            // publications details

            if (experiment != null) {
                GraphPublication publication = (GraphPublication) experiment.getPublication();
                graphAnnotations.addAll(publication.getAnnotations());
                if (publication != null) {
                    searchInteraction.setAuthors((publication.getAuthors() != null && !publication.getAuthors().isEmpty()) ? new LinkedHashSet(publication.getAuthors()) : null);
                    searchInteraction.setFirstAuthor((searchInteraction.getAuthors() != null && !searchInteraction.getAuthors().isEmpty()) ? searchInteraction.getAuthors().iterator().next() + " et al. (" + CommonUtility.getYearOutOfDate(publication.getPublicationDate()) + ")" +
                            "\t\n" : "");
//                    searchInteraction.setPublicationId((publication.getAuthors() != null) ? publication.getPubmedId() : "");
                    searchInteraction.setSourceDatabase((publication.getSource() != null) ? publication.getSource().getShortName() : "");
                    searchInteraction.setReleaseDate((publication.getReleasedDate() != null) ? publication.getReleasedDate() : null);
                    searchInteraction.setPublicationIdentifiers((publication.getIdentifiers() != null && !publication.getIdentifiers().isEmpty()) ? SolrDocumentConverterUtils.xrefsToSolrDocument(publication.getIdentifiers()) : null);
                }
            }

            searchInteraction.setInteractionAnnotations(!graphAnnotations.isEmpty() ? SolrDocumentConverterUtils.annotationsToSolrDocument(graphAnnotations) : null);

        }


        return searchInteraction;
    }


}
