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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import psidev.psi.mi.jami.binary.BinaryInteractionEvidence;
import psidev.psi.mi.jami.model.Organism;
import uk.ac.ebi.intact.graphdb.model.nodes.*;
import uk.ac.ebi.intact.graphdb.service.GraphInteractionService;
import uk.ac.ebi.intact.portal.indexer.interactor.InteractorUtility;
import uk.ac.ebi.intact.search.interactions.model.SearchChildInteractor;
import uk.ac.ebi.intact.search.interactions.model.SearchInteraction;
import uk.ac.ebi.intact.search.interactions.service.InteractionIndexService;
import uk.ac.ebi.intact.search.interactions.utils.DocumentType;
import utilities.CommonUtility;
import utilities.Constants;
import utilities.SolrDocumentConverterUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

import static utilities.SolrDocumentConverterUtils.*;

@Component
@Transactional
public class InteractionIndexerTasklet implements Tasklet {

    private static final Log log = LogFactory.getLog(InteractionIndexerTasklet.class);

    private static final int PAGE_SIZE = 2000;
    private static final int MAX_PING_TIME = 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEPTH = 0;
    @Autowired
    InteractorUtility interactorUtility;
    private int attempts = 0;
    private boolean simulation = false;
    private int binaryCounter = 1;
    @Resource
    private GraphInteractionService graphInteractionService;
    @Resource
    private InteractionIndexService interactionIndexService;

    @Resource
    private SolrClient solrClient;

    /**
     * Converts Graph interaction to Solr interaction and extract more depth details from graph db if needed.
     *
     * @param interactionEvidence
     * @return
     */
    // TODO try to split this method into methods for specific(eg. Interactor/publication/participant etc.) details
    private static SearchInteraction toSolrDocument(BinaryInteractionEvidence interactionEvidence, int binaryCounter) {

        SearchInteraction searchInteraction = new SearchInteraction();
        List<SearchChildInteractor> searchChildInteractors = new ArrayList<>();

        List<GraphAnnotation> graphAnnotations = new ArrayList<GraphAnnotation>();
        List<GraphAlias> graphAliasesA = new ArrayList<GraphAlias>();
        List<GraphAlias> graphAliasesB = new ArrayList<GraphAlias>();
        Integer featureCount = 0;

        if (interactionEvidence instanceof GraphBinaryInteractionEvidence) {
            GraphBinaryInteractionEvidence graphBinaryInteractionEvidence = (GraphBinaryInteractionEvidence) interactionEvidence;

            // Interactor details
            if (graphBinaryInteractionEvidence.getInteractorA() != null) {
                GraphInteractor graphInteractorA = (GraphInteractor) graphBinaryInteractionEvidence.getInteractorA();
                Organism organismA = graphInteractorA.getOrganism();
                searchInteraction.setIdA(SolrDocumentConverterUtils.xrefToSolrDocument(graphInteractorA.getPreferredIdentifier()));
                searchInteraction.setAltIdsA((graphInteractorA.getIdentifiers() != null && graphInteractorA.getIdentifiers().size() > 0) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphInteractorA.getIdentifiers()) : null);
                graphAliasesA.addAll(graphInteractorA.getAliases());
                searchInteraction.setAnnotationsA((graphInteractorA.getAnnotations() != null && graphInteractorA.getAnnotations().size() > 0) ? SolrDocumentConverterUtils.annotationsToSolrDocument(graphInteractorA.getAnnotations()) : null);
                searchInteraction.setChecksumsA((graphInteractorA.getChecksums() != null && graphInteractorA.getChecksums().size() > 0) ? SolrDocumentConverterUtils.checksumsToSolrDocument(graphInteractorA.getChecksums()) : null);
                searchInteraction.setTaxIdA(organismA != null ? organismA.getTaxId() : null);
                searchInteraction.setSpeciesA(organismA != null ? organismA.getScientificName() : null);
                searchInteraction.setTypeA(graphInteractorA.getInteractorType().getShortName());
                searchInteraction.setTypeMIA(graphInteractorA.getInteractorType().getMIIdentifier());
                searchInteraction.setXrefsA((graphInteractorA.getXrefs() != null && graphInteractorA.getXrefs().size() > 0) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphInteractorA.getXrefs()) : null);
                searchInteraction.setAcA(graphInteractorA.getAc());
                searchInteraction.setUniqueIdA((graphInteractorA.getInteractorType() != null
                        && graphInteractorA.getInteractorType().getShortName() != null
                        && graphInteractorA.getInteractorType().getShortName().equals(Constants.MOLECULE_SET)) ? graphInteractorA.getAc() : graphInteractorA.getPreferredIdentifier() != null ? graphInteractorA.getPreferredIdentifier().getId() : "");
                searchInteraction.setMoleculeA(graphInteractorA.getPreferredName());
                searchInteraction.setIntactNameA(graphInteractorA.getShortName());
                searchInteraction.setDescriptionA(graphInteractorA.getFullName());

                // Indexing nested interactor documents
                SearchChildInteractor searchChildInteractorA = new SearchChildInteractor();
                searchChildInteractorA.setDocumentType(DocumentType.INTERACTOR);
                searchChildInteractorA.setInteractorName(graphInteractorA.getPreferredName());
                searchChildInteractorA.setInteractorIntactName(graphInteractorA.getShortName());
                searchChildInteractorA.setInteractorDescription(graphInteractorA.getFullName());
                searchChildInteractorA.setInteractorPreferredIdentifier(graphInteractorA.getPreferredIdentifier() != null ? graphInteractorA.getPreferredIdentifier().getId() : "");
                searchChildInteractorA.setInteractorAlias(aliasesWithTypesToSolrDocument(graphInteractorA.getAliases()));
                searchChildInteractorA.setInteractorAltIds(xrefsToSolrDocument(graphInteractorA.getIdentifiers()));

                searchChildInteractorA.setInteractorType(graphInteractorA.getInteractorType().getShortName());

                searchChildInteractorA.setInteractorSpecies(organismA != null ? organismA.getScientificName() : null);
                searchChildInteractorA.setInteractorTaxId(organismA != null ? organismA.getTaxId() : null);
                searchChildInteractorA.setInteractorXrefs(xrefsToSolrDocument(graphInteractorA.getXrefs()));
                searchChildInteractorA.setInteractorAc(graphInteractorA.getAc());
                searchChildInteractorA.setInteractionCount(graphInteractorA.getInteractions().size());

                Collection<GraphFeature> featureEvidences = new ArrayList<>();
                if (graphInteractorA.getParticipantEvidences() != null) {
                    for (GraphParticipantEvidence participantEvidence : graphInteractorA.getParticipantEvidences()) {
                        if (participantEvidence.getFeatures() != null) {
                            featureEvidences.addAll(participantEvidence.getFeatures());
                        }
                    }

                    searchChildInteractorA.setInteractorFeatureShortLabels(featuresShortlabelToSolrDocument(featureEvidences));
                }
                searchChildInteractors.add(searchChildInteractorA);
            }

            if (graphBinaryInteractionEvidence.getInteractorB() != null) {
                GraphInteractor graphInteractorB = (GraphInteractor) graphBinaryInteractionEvidence.getInteractorB();
                Organism organismB = graphInteractorB.getOrganism();
                searchInteraction.setIdB(SolrDocumentConverterUtils.xrefToSolrDocument(graphInteractorB.getPreferredIdentifier()));
                searchInteraction.setAltIdsB((graphInteractorB.getIdentifiers() != null && graphInteractorB.getIdentifiers().size() > 0) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphInteractorB.getIdentifiers()) : null);
                graphAliasesB.addAll(graphInteractorB.getAliases());
                searchInteraction.setAnnotationsB((graphInteractorB.getAnnotations() != null && graphInteractorB.getAnnotations().size() > 0) ? SolrDocumentConverterUtils.annotationsToSolrDocument(graphInteractorB.getAnnotations()) : null);
                searchInteraction.setChecksumsB((graphInteractorB.getChecksums() != null && graphInteractorB.getChecksums().size() > 0) ? SolrDocumentConverterUtils.checksumsToSolrDocument(graphInteractorB.getChecksums()) : null);
                searchInteraction.setTaxIdB(organismB != null ? organismB.getTaxId() : null);
                searchInteraction.setSpeciesB(organismB != null ? organismB.getScientificName() : null);
                searchInteraction.setTypeB(graphInteractorB.getInteractorType().getShortName());
                searchInteraction.setTypeMIB(graphInteractorB.getInteractorType().getMIIdentifier());
                searchInteraction.setXrefsB((graphInteractorB.getXrefs() != null && graphInteractorB.getXrefs().size() > 0) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphInteractorB.getXrefs()) : null);
                searchInteraction.setSpeciesB(organismB != null ? organismB.getScientificName() : null);
                searchInteraction.setAcB(graphInteractorB.getAc());
                searchInteraction.setUniqueIdB((graphInteractorB.getInteractorType() != null
                        && graphInteractorB.getInteractorType().getShortName() != null
                        && graphInteractorB.getInteractorType().getShortName().equals(Constants.MOLECULE_SET)) ? graphInteractorB.getAc()
                        : graphInteractorB.getPreferredIdentifier() != null ? graphInteractorB.getPreferredIdentifier().getId() : "");
                searchInteraction.setMoleculeB(graphInteractorB.getPreferredName());
                searchInteraction.setIntactNameB(graphInteractorB.getShortName());
                searchInteraction.setDescriptionB(graphInteractorB.getFullName());

                // Indexing nested interactor documents
                SearchChildInteractor searchChildInteractorB = new SearchChildInteractor();
                searchChildInteractorB.setDocumentType(DocumentType.INTERACTOR);
                searchChildInteractorB.setInteractorName(graphInteractorB.getPreferredName());
                searchChildInteractorB.setInteractorIntactName(graphInteractorB.getShortName());
                searchChildInteractorB.setInteractorPreferredIdentifier(graphInteractorB.getPreferredIdentifier() != null ? graphInteractorB.getPreferredIdentifier().getId() : "");
                searchChildInteractorB.setInteractorDescription(graphInteractorB.getFullName());
                searchChildInteractorB.setInteractorAlias(aliasesWithTypesToSolrDocument(graphInteractorB.getAliases()));
                searchChildInteractorB.setInteractorAltIds(xrefsToSolrDocument(graphInteractorB.getIdentifiers()));
                searchChildInteractorB.setInteractorType(graphInteractorB.getInteractorType().getShortName());
                searchChildInteractorB.setInteractorSpecies(organismB != null ? organismB.getScientificName() : null);
                searchChildInteractorB.setInteractorTaxId(organismB != null ? organismB.getTaxId() : null);
                searchChildInteractorB.setInteractorXrefs(xrefsToSolrDocument(graphInteractorB.getXrefs()));
                searchChildInteractorB.setInteractorAc(graphInteractorB.getAc());
                searchChildInteractorB.setInteractionCount(graphInteractorB.getInteractions().size());

                Collection<GraphFeature> featureEvidences = new ArrayList<>();
                if (graphInteractorB.getParticipantEvidences() != null) {
                    for (GraphParticipantEvidence participantEvidence : graphInteractorB.getParticipantEvidences()) {
                        if (participantEvidence.getFeatures() != null) {
                            featureEvidences.addAll(participantEvidence.getFeatures());
                        }
                    }

                    searchChildInteractorB.setInteractorFeatureShortLabels(featuresShortlabelToSolrDocument(featureEvidences));
                }
                searchChildInteractors.add(searchChildInteractorB);
            }

            searchInteraction.setSearchChildInteractors(searchChildInteractors);

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
                featureCount += (ographFeaturesA != null ? ographFeaturesA.size() : 0);
                searchInteraction.setFeatureA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? SolrDocumentConverterUtils.featuresToSolrDocument(ographFeaturesA) : null);
                searchInteraction.setFeatureShortLabelA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? SolrDocumentConverterUtils.featuresShortlabelToSolrDocument(ographFeaturesA) : null);
                boolean mutation = (ographFeaturesA != null && !ographFeaturesA.isEmpty()) && SolrDocumentConverterUtils.doesAnyFeatureHaveMutation(ographFeaturesA);
                searchInteraction.setDisruptedByMutation(mutation);
                searchInteraction.setMutationA(mutation);
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
                featureCount += (ographFeaturesB != null ? ographFeaturesB.size() : 0);
                searchInteraction.setFeatureB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? SolrDocumentConverterUtils.featuresToSolrDocument(ographFeaturesB) : null);
                searchInteraction.setFeatureShortLabelB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? SolrDocumentConverterUtils.featuresShortlabelToSolrDocument(ographFeaturesB) : null);
                boolean mutation = (ographFeaturesB != null && !ographFeaturesB.isEmpty()) && SolrDocumentConverterUtils.doesAnyFeatureHaveMutation(ographFeaturesB);
                if (!searchInteraction.isDisruptedByMutation()) {
                    searchInteraction.setDisruptedByMutation(mutation);
                }
                searchInteraction.setMutationB(mutation);

            }

            searchInteraction.setFeatureCount(featureCount);
            searchInteraction.setAliasesA(!graphAliasesA.isEmpty() ? SolrDocumentConverterUtils.aliasesWithTypesToSolrDocument(graphAliasesA) : null);
            searchInteraction.setAliasesB(!graphAliasesB.isEmpty() ? SolrDocumentConverterUtils.aliasesWithTypesToSolrDocument(graphAliasesB) : null);

            //experiment details
            GraphExperiment experiment = (GraphExperiment) graphBinaryInteractionEvidence.getExperiment();


            graphAnnotations.addAll(experiment.getAnnotations());
            searchInteraction.setDetectionMethod((graphBinaryInteractionEvidence.getExperiment() != null && graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod() != null) ? graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod().getShortName() : null);
            searchInteraction.setHostOrganism(experiment.getHostOrganism() != null ? experiment.getHostOrganism().getScientificName() : null);


            //interaction details
            searchInteraction.setBinaryInteractionId(binaryCounter);
            searchInteraction.setDocumentType(DocumentType.INTERACTION);
            GraphClusteredInteraction graphClusteredInteraction = graphBinaryInteractionEvidence.getClusteredInteraction();
            Set<String> intactConfidence = new HashSet<String>();
            if (graphClusteredInteraction != null) {
                intactConfidence.add("intact-miscore:" + graphClusteredInteraction.getMiscore());
                searchInteraction.setIntactMiscore(graphClusteredInteraction.getMiscore());
            }
            searchInteraction.setIdentifiers((graphBinaryInteractionEvidence.getIdentifiers() != null && !graphBinaryInteractionEvidence.getIdentifiers().isEmpty()) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphBinaryInteractionEvidence.getIdentifiers()) : null);
            searchInteraction.setConfidenceValues(!intactConfidence.isEmpty() ? intactConfidence : null);
            searchInteraction.setType(graphBinaryInteractionEvidence.getInteractionType() != null ? graphBinaryInteractionEvidence.getInteractionType().getShortName() : null);
            if (searchInteraction.getType() != null) {
                searchInteraction.setTypeMIIdentifier(graphBinaryInteractionEvidence.getInteractionType().getMIIdentifier() != null ? graphBinaryInteractionEvidence.getInteractionType().getMIIdentifier() : null);
            }
            searchInteraction.setAc(graphBinaryInteractionEvidence.getAc());

            if (graphBinaryInteractionEvidence.getConfidences() != null) {
                Set<String> confidences = SolrDocumentConverterUtils.confidencesToSolrDocument(graphBinaryInteractionEvidence.getConfidences());
                if (searchInteraction.getConfidenceValues() != null) {
                    searchInteraction.getConfidenceValues().addAll(confidences);
                } else {
                    searchInteraction.setConfidenceValues(confidences);
                }
            }

            graphAnnotations.addAll(graphBinaryInteractionEvidence.getAnnotations());
            searchInteraction.setAnnotations(!graphBinaryInteractionEvidence.getAnnotations().isEmpty() ? SolrDocumentConverterUtils.annotationsToSolrDocument(graphBinaryInteractionEvidence.getAnnotations()) : null);
            searchInteraction.setExpansionMethod((graphBinaryInteractionEvidence.getComplexExpansion() != null) ? graphBinaryInteractionEvidence.getComplexExpansion().getShortName() : null);
            searchInteraction.setXrefs((graphBinaryInteractionEvidence.getXrefs() != null && !graphBinaryInteractionEvidence.getXrefs().isEmpty()) ? SolrDocumentConverterUtils.xrefsToSolrDocument(graphBinaryInteractionEvidence.getXrefs()) : null);
            searchInteraction.setParameters((graphBinaryInteractionEvidence.getParameters() != null && !graphBinaryInteractionEvidence.getParameters().isEmpty()) ? SolrDocumentConverterUtils.parametersToSolrDocument(graphBinaryInteractionEvidence.getParameters()) : null);
            searchInteraction.setCreationDate(graphBinaryInteractionEvidence.getCreatedDate());
            searchInteraction.setUpdationDate(graphBinaryInteractionEvidence.getUpdatedDate());
            searchInteraction.setChecksums((graphBinaryInteractionEvidence.getChecksums() != null && !graphBinaryInteractionEvidence.getChecksums().isEmpty()) ? SolrDocumentConverterUtils.checksumsToSolrDocument(graphBinaryInteractionEvidence.getChecksums()) : null);
            searchInteraction.setNegative(graphBinaryInteractionEvidence.isNegative());

            // publications details

            if (experiment != null) {
                GraphPublication publication = (GraphPublication) experiment.getPublication();
                graphAnnotations.addAll(publication.getAnnotations());
                if (publication != null) {
                    searchInteraction.setAuthors((publication.getAuthors() != null && !publication.getAuthors().isEmpty()) ? new LinkedHashSet(publication.getAuthors()) : null);
                    String firstAuthor = (searchInteraction.getAuthors() != null && !searchInteraction.getAuthors().isEmpty()) ? searchInteraction.getAuthors().iterator().next() + " et al." : "";
                    if (!firstAuthor.isEmpty()) {
                        if (publication.getPublicationDate() != null) {
                            firstAuthor = firstAuthor + " (" + CommonUtility.getYearOutOfDate(publication.getPublicationDate()) + ")";
                        }
                        firstAuthor = firstAuthor + "\t\n";
                    }
                    searchInteraction.setFirstAuthor(firstAuthor);
//                    searchInteraction.setPublicationId((publication.getAuthors() != null) ? publication.getPubmedId() : "");
                    searchInteraction.setSourceDatabase((publication.getSource() != null) ? publication.getSource().getShortName() : "");
                    searchInteraction.setReleaseDate((publication.getReleasedDate() != null) ? publication.getReleasedDate() : null);
                    searchInteraction.setPublicationIdentifiers((publication.getIdentifiers() != null && !publication.getIdentifiers().isEmpty()) ? SolrDocumentConverterUtils.xrefsToSolrDocument(publication.getIdentifiers()) : null);
                    //TODO... Enable in graphdb to get from pubmedId instead
                    //this is needed for sorting on publication id
                    searchInteraction.setPublicationPubmedIdentifier((publication.getPubmedIdStr() != null) ? publication.getPubmedIdStr() : null);
                }
            }

            searchInteraction.setAllAnnotations(!graphAnnotations.isEmpty() ? SolrDocumentConverterUtils.annotationsToSolrDocument(graphAnnotations) : null);

        }


        return searchInteraction;
    }

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

//        try {
        log.info("Start indexing Interaction data");

        int pageNumber = 0;
        Slice<GraphBinaryInteractionEvidence> graphInteractionSlice;

        log.debug("Starting to retrieve data");
        // loop over the data in pages until we are done with all
        long totalTime = System.currentTimeMillis();

        do {
            log.info("Retrieving page : " + pageNumber);
            long dbStart = System.currentTimeMillis();
            graphInteractionSlice = graphInteractionService.getAllGraphBinaryInteractionEvidences(PageRequest.of(pageNumber, PAGE_SIZE));
            log.info("Main DB query took [ms] : " + (System.currentTimeMillis() - dbStart));

            List<GraphBinaryInteractionEvidence> interactionList = graphInteractionSlice.getContent();
            List<SearchInteraction> interactions = new ArrayList<>();

            long convStart = System.currentTimeMillis();
            for (GraphBinaryInteractionEvidence graphInteraction : interactionList) {

                try {
                    interactions.add(toSolrDocument(graphInteraction, binaryCounter));
                    this.binaryCounter++;
                } catch (Exception e) {
                    log.error("Interaction with ac: " + graphInteraction.getAc() + " could not be indexed because of exception  :- ");
                    e.printStackTrace();
                }
            }
            log.info("Conversion of " + interactions.size() + " records took [ms] : " + (System.currentTimeMillis() - convStart));

            long indexStart = System.currentTimeMillis();
            if (!simulation) {
//                    solrServerCheck();

                interactionIndexService.save(interactions);
                log.info("Index save took [ms] : " + (System.currentTimeMillis() - indexStart));
            }

            // increase the page number
            pageNumber++;
        } while (graphInteractionSlice.hasNext());

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
