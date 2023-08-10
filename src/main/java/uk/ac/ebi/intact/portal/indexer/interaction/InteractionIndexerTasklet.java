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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import psidev.psi.mi.jami.binary.BinaryInteractionEvidence;
import psidev.psi.mi.jami.model.Organism;
import psidev.psi.mi.jami.utils.CvTermUtils;
import uk.ac.ebi.intact.graphdb.model.nodes.*;
import uk.ac.ebi.intact.graphdb.service.GraphInteractionService;
import uk.ac.ebi.intact.search.interactions.model.SearchChildInteractor;
import uk.ac.ebi.intact.search.interactions.model.SearchInteraction;
import uk.ac.ebi.intact.search.interactions.service.InteractionIndexService;
import uk.ac.ebi.intact.search.interactions.utils.DocumentType;
import uk.ac.ebi.intact.search.interactions.utils.as.converters.DateFieldConverter;
import uk.ac.ebi.intact.style.service.StyleService;
import utilities.CommonUtility;
import utilities.Constants;
import utilities.SolrDocumentConverterUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static utilities.SolrASDocumentConverterUtils.*;
import static utilities.SolrDocumentConverterUtils.*;

@Component
@Transactional
public class InteractionIndexerTasklet implements Tasklet {

    private static final Log log = LogFactory.getLog(InteractionIndexerTasklet.class);

    private static final int PAGE_SIZE = 500;
    private static final int MAX_PING_TIME = 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEPTH = 0;

    private int attempts = 0;
    private boolean simulation = false;
    private int binaryCounter = 1;//TODO... we need this for generating test interactions.xml, separate the logic later

    @Resource
    private GraphInteractionService graphInteractionService;

    @Resource
    private InteractionIndexService interactionIndexService;

    @Resource
    private StyleService styleService;

    @Resource
    private SolrClient solrClient;

    /**
     * Converts Graph interaction to Solr interaction and extract more depth details from graph db if needed.
     *
     * @param interactionEvidence
     * @return
     */
    // TODO try to split this method into methods for specific(eg. Interactor/publication/participant etc.) details
    public static SearchInteraction toSolrDocument(BinaryInteractionEvidence interactionEvidence, int binaryCounter, StyleService styleService) {

        SearchInteraction searchInteraction = new SearchInteraction();
        List<SearchChildInteractor> searchChildInteractors = new ArrayList<>();

        List<GraphAnnotation> graphAnnotations = new ArrayList<GraphAnnotation>();
        List<GraphAlias> graphAliasesA = new ArrayList<GraphAlias>();
        List<GraphAlias> graphAliasesB = new ArrayList<GraphAlias>();
        List<GraphXref> graphXrefsA = new ArrayList<GraphXref>();
        List<GraphXref> graphXrefsB = new ArrayList<GraphXref>();
        List<GraphCvTerm> graphIdentificationMethodsA = new ArrayList<GraphCvTerm>();
        List<GraphCvTerm> graphIdentificationMethodsB = new ArrayList<GraphCvTerm>();
        boolean stoichiometry = false;

        Integer featureCount = 0;

        if (interactionEvidence instanceof GraphBinaryInteractionEvidence) {
            GraphBinaryInteractionEvidence graphBinaryInteractionEvidence = (GraphBinaryInteractionEvidence) interactionEvidence;

            // Interactor details
            if (graphBinaryInteractionEvidence.getInteractorA() != null) {
                GraphInteractor graphInteractorA = (GraphInteractor) graphBinaryInteractionEvidence.getInteractorA();
                Organism organismA = graphInteractorA.getOrganism();
                searchInteraction.setIdA(xrefToSolrDocument(graphInteractorA.getPreferredIdentifier()));
                searchInteraction.setAsIdA(xrefToASSolrDocument(graphInteractorA.getPreferredIdentifier()));
                searchInteraction.setAltIdsA((graphInteractorA.getIdentifiers() != null && graphInteractorA.getIdentifiers().size() > 0) ? xrefsToSolrDocument(graphInteractorA.getIdentifiers()) : null);
                searchInteraction.setAsAltidA((graphInteractorA.getIdentifiers() != null && graphInteractorA.getIdentifiers().size() > 0) ? xrefsToASSolrDocument(graphInteractorA.getIdentifiers()) : null);
                graphAliasesA.addAll(graphInteractorA.getAliases());
                searchInteraction.setAnnotationsA((graphInteractorA.getAnnotations() != null && graphInteractorA.getAnnotations().size() > 0) ? annotationsToSolrDocument(graphInteractorA.getAnnotations()) : null);
                searchInteraction.setChecksumsA((graphInteractorA.getChecksums() != null && graphInteractorA.getChecksums().size() > 0) ? checksumsToSolrDocument(graphInteractorA.getChecksums()) : null);
                searchInteraction.setTaxIdA(organismA != null ? organismA.getTaxId() : null);
                searchInteraction.setTaxIdAStyled(organismA != null ?
                        organismA.getTaxId() + "__" + organismA.getScientificName() + "__" +
                                "#" + Integer.toHexString(styleService.getInteractorColor(String.valueOf(organismA.getTaxId())).getRGB()).substring(2) : null);
                searchInteraction.setSpeciesA(organismA != null ? organismA.getScientificName() : null);
                searchInteraction.setAsTaxIdA(organismToASSolrDocument(organismA));
                final String typeAShortName = graphInteractorA.getInteractorType().getShortName();
                searchInteraction.setTypeA(typeAShortName);
                searchInteraction.setAsTypeA(cvToASSolrDocument(graphInteractorA.getInteractorType()));

                final String typeMIA = graphInteractorA.getInteractorType().getMIIdentifier();
                searchInteraction.setTypeMIA(typeMIA);
                searchInteraction.setTypeMIAStyled(typeMIA + "__" + typeAShortName + "__" + styleService.getInteractorShape(typeMIA));

                searchInteraction.setXrefsA((graphInteractorA.getXrefs() != null && graphInteractorA.getXrefs().size() > 0) ? xrefsToSolrDocument(graphInteractorA.getXrefs()) : null);
                graphXrefsA.addAll(graphInteractorA.getXrefs());
                searchInteraction.setAcA(graphInteractorA.getAc());
                searchInteraction.setUniqueIdA((graphInteractorA.getInteractorType() != null
                        && typeAShortName != null
                        && typeAShortName.equals(Constants.MOLECULE_SET)) ? graphInteractorA.getAc() : graphInteractorA.getPreferredIdentifier() != null ? graphInteractorA.getPreferredIdentifier().getId() : "");
                searchInteraction.setMoleculeA(graphInteractorA.getPreferredName());
                searchInteraction.setIntactNameA(graphInteractorA.getShortName());
                searchInteraction.setDescriptionA(graphInteractorA.getFullName());
                searchInteraction.setAsGeneNameA(graphInteractorA instanceof GraphProtein ? ((GraphProtein) graphInteractorA).getGeneName() : null);

                // Indexing nested interactor documents
                interactorToSolrDocument(searchChildInteractors, graphInteractorA, organismA, styleService);
            }

            if (graphBinaryInteractionEvidence.getInteractorB() != null) {
                GraphInteractor graphInteractorB = (GraphInteractor) graphBinaryInteractionEvidence.getInteractorB();
                Organism organismB = graphInteractorB.getOrganism();
                searchInteraction.setIdB(xrefToSolrDocument(graphInteractorB.getPreferredIdentifier()));
                searchInteraction.setAsIdB(xrefToASSolrDocument(graphInteractorB.getPreferredIdentifier()));
                searchInteraction.setAltIdsB((graphInteractorB.getIdentifiers() != null && graphInteractorB.getIdentifiers().size() > 0) ? xrefsToSolrDocument(graphInteractorB.getIdentifiers()) : null);
                searchInteraction.setAsAltidB((graphInteractorB.getIdentifiers() != null && graphInteractorB.getIdentifiers().size() > 0) ? xrefsToASSolrDocument(graphInteractorB.getIdentifiers()) : null);
                graphAliasesB.addAll(graphInteractorB.getAliases());
                searchInteraction.setAnnotationsB((graphInteractorB.getAnnotations() != null && graphInteractorB.getAnnotations().size() > 0) ? annotationsToSolrDocument(graphInteractorB.getAnnotations()) : null);
                searchInteraction.setChecksumsB((graphInteractorB.getChecksums() != null && graphInteractorB.getChecksums().size() > 0) ? checksumsToSolrDocument(graphInteractorB.getChecksums()) : null);
                searchInteraction.setTaxIdB(organismB != null ? organismB.getTaxId() : null);
                searchInteraction.setTaxIdBStyled(organismB != null ?
                        organismB.getTaxId() + "__" + organismB.getScientificName() + "__" +
                                "#" + Integer.toHexString(styleService.getInteractorColor(String.valueOf(organismB.getTaxId())).getRGB()).substring(2) : null);
                searchInteraction.setSpeciesB(organismB != null ? organismB.getScientificName() : null);
                searchInteraction.setAsTaxIdB(organismToASSolrDocument(organismB));
                final String typeBShortName = graphInteractorB.getInteractorType().getShortName();
                searchInteraction.setTypeB(typeBShortName);
                searchInteraction.setAsTypeB(cvToASSolrDocument(graphInteractorB.getInteractorType()));

                final String typeMIB = graphInteractorB.getInteractorType().getMIIdentifier();
                searchInteraction.setTypeMIB(typeMIB);
                searchInteraction.setTypeMIBStyled(typeMIB + "__" + typeBShortName + "__" + styleService.getInteractorShape(typeMIB));

                searchInteraction.setXrefsB((graphInteractorB.getXrefs() != null && graphInteractorB.getXrefs().size() > 0) ? xrefsToSolrDocument(graphInteractorB.getXrefs()) : null);
                graphXrefsB.addAll(graphInteractorB.getXrefs());
                searchInteraction.setAcB(graphInteractorB.getAc());
                searchInteraction.setUniqueIdB((graphInteractorB.getInteractorType() != null
                        && graphInteractorB.getInteractorType().getShortName() != null
                        && graphInteractorB.getInteractorType().getShortName().equals(Constants.MOLECULE_SET)) ? graphInteractorB.getAc()
                        : graphInteractorB.getPreferredIdentifier() != null ? graphInteractorB.getPreferredIdentifier().getId() : "");
                searchInteraction.setMoleculeB(graphInteractorB.getPreferredName());
                searchInteraction.setIntactNameB(graphInteractorB.getShortName());
                searchInteraction.setDescriptionB(graphInteractorB.getFullName());
                searchInteraction.setAsGeneNameB(graphInteractorB instanceof GraphProtein ? ((GraphProtein) graphInteractorB).getGeneName() : null);
                // Indexing nested interactor documents
                interactorToSolrDocument(searchChildInteractors, graphInteractorB, organismB, styleService);
            }

            searchInteraction.setSearchChildInteractors(searchChildInteractors);

            //participant details
            if (graphBinaryInteractionEvidence.getParticipantA() != null) {
                GraphParticipantEvidence graphParticipantEvidenceA = (GraphParticipantEvidence) graphBinaryInteractionEvidence.getParticipantA();

                searchInteraction.setBiologicalRoleA(graphParticipantEvidenceA.getBiologicalRole().getShortName());
                searchInteraction.setAsBioRoleA(cvToASSolrDocument(graphParticipantEvidenceA.getBiologicalRole()));
                searchInteraction.setExperimentalRoleA(graphParticipantEvidenceA.getExperimentalRole().getShortName());
                searchInteraction.setBiologicalRoleMIIdentifierA(graphParticipantEvidenceA.getBiologicalRole().getMIIdentifier());
                searchInteraction.setExperimentalRoleMIIdentifierA(graphParticipantEvidenceA.getExperimentalRole().getMIIdentifier());
                searchInteraction.setExperimentalPreparationsA((graphParticipantEvidenceA.getExperimentalPreparations() != null && !graphParticipantEvidenceA.getExperimentalPreparations().isEmpty()) ? cvTermsToSolrDocument(graphParticipantEvidenceA.getExperimentalPreparations()) : null);
                searchInteraction.setStoichiometryA(graphParticipantEvidenceA.getStoichiometry() != null ? graphParticipantEvidenceA.getStoichiometry().getMinValue() + "-" + graphParticipantEvidenceA.getStoichiometry().getMaxValue() : null);
                if (graphParticipantEvidenceA.getStoichiometry() != null && (graphParticipantEvidenceA.getStoichiometry().getMinValue() > 0 || graphParticipantEvidenceA.getStoichiometry().getMaxValue() > 0)) {
                    stoichiometry = true;
                }
                searchInteraction.setIdentificationMethodsA((graphParticipantEvidenceA.getIdentificationMethods() != null && !graphParticipantEvidenceA.getIdentificationMethods().isEmpty()) ? cvTermsToSolrDocument(graphParticipantEvidenceA.getIdentificationMethods()) : null);
                graphIdentificationMethodsA.addAll(graphParticipantEvidenceA.getIdentificationMethods());
                searchInteraction.setIdentificationMethodMIIdentifiersA((graphParticipantEvidenceA.getIdentificationMethods() != null && !graphParticipantEvidenceA.getIdentificationMethods().isEmpty()) ? cvTermsMIToSolrDocument(graphParticipantEvidenceA.getIdentificationMethods()) : null);
                graphAliasesA.addAll(graphParticipantEvidenceA.getAliases());
                graphXrefsA.addAll(graphParticipantEvidenceA.getXrefs());
                // featureDetails
                List<GraphFeature> ographFeaturesA = (List<GraphFeature>) graphParticipantEvidenceA.getFeatures();
                featureCount += (ographFeaturesA != null ? ographFeaturesA.size() : 0);
                searchInteraction.setFeaturesA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? featuresToSolrDocument(ographFeaturesA) : null);
                searchInteraction.setFeatureShortLabelsA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? featuresShortlabelToSolrDocument(ographFeaturesA) : null);
                searchInteraction.setFeatureTypesA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? featuresTypeToSolrDocument(ographFeaturesA) : null);
                searchInteraction.setAsFeatureTypesA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? featuresTypeToASSolrDocument(ographFeaturesA) : null);
                searchInteraction.setFeatureRangesA((ographFeaturesA != null && !ographFeaturesA.isEmpty()) ? featuresRangesToSolrDocument(ographFeaturesA) : null);
                searchInteraction.setMutationA(affectedByMutationToSolrDocument(styleService, searchInteraction, ographFeaturesA));
                searchInteraction.setAsMutationA(searchInteraction.isMutationA());
            }

            if (graphBinaryInteractionEvidence.getParticipantB() != null) {
                GraphParticipantEvidence graphParticipantEvidenceB = (GraphParticipantEvidence) graphBinaryInteractionEvidence.getParticipantB();

                searchInteraction.setBiologicalRoleB(graphParticipantEvidenceB.getBiologicalRole().getShortName());
                searchInteraction.setAsBioRoleB(cvToASSolrDocument(graphParticipantEvidenceB.getBiologicalRole()));
                searchInteraction.setExperimentalRoleB(graphParticipantEvidenceB.getExperimentalRole().getShortName());
                searchInteraction.setBiologicalRoleMIIdentifierB(graphParticipantEvidenceB.getBiologicalRole().getMIIdentifier());
                searchInteraction.setExperimentalRoleMIIdentifierB(graphParticipantEvidenceB.getExperimentalRole().getMIIdentifier());
                searchInteraction.setExperimentalPreparationsB((graphParticipantEvidenceB.getExperimentalPreparations() != null && !graphParticipantEvidenceB.getExperimentalPreparations().isEmpty()) ? cvTermsToSolrDocument(graphParticipantEvidenceB.getExperimentalPreparations()) : null);
                searchInteraction.setStoichiometryB(graphParticipantEvidenceB.getStoichiometry() != null ? graphParticipantEvidenceB.getStoichiometry().getMinValue() + "-" + graphParticipantEvidenceB.getStoichiometry().getMaxValue() : null);
                if (graphParticipantEvidenceB.getStoichiometry() != null && (graphParticipantEvidenceB.getStoichiometry().getMinValue() > 0 || graphParticipantEvidenceB.getStoichiometry().getMaxValue() > 0)) {
                    stoichiometry = true;
                }
                searchInteraction.setIdentificationMethodsB((graphParticipantEvidenceB.getIdentificationMethods() != null && !graphParticipantEvidenceB.getIdentificationMethods().isEmpty()) ? cvTermsToSolrDocument(graphParticipantEvidenceB.getIdentificationMethods()) : null);
                graphIdentificationMethodsB.addAll(graphParticipantEvidenceB.getIdentificationMethods());
                searchInteraction.setIdentificationMethodMIIdentifiersB((graphParticipantEvidenceB.getIdentificationMethods() != null && !graphParticipantEvidenceB.getIdentificationMethods().isEmpty()) ? cvTermsMIToSolrDocument(graphParticipantEvidenceB.getIdentificationMethods()) : null);
                graphAliasesB.addAll(graphParticipantEvidenceB.getAliases());
                graphXrefsB.addAll(graphParticipantEvidenceB.getXrefs());
                // featureDetails
                List<GraphFeature> ographFeaturesB = (List<GraphFeature>) graphParticipantEvidenceB.getFeatures();
                featureCount += (ographFeaturesB != null ? ographFeaturesB.size() : 0);
                searchInteraction.setFeaturesB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? featuresToSolrDocument(ographFeaturesB) : null);
                searchInteraction.setFeatureShortLabelsB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? featuresShortlabelToSolrDocument(ographFeaturesB) : null);
                searchInteraction.setFeatureTypesB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? featuresTypeToSolrDocument(ographFeaturesB) : null);
                searchInteraction.setAsFeatureTypesB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? featuresTypeToASSolrDocument(ographFeaturesB) : null);
                searchInteraction.setFeatureRangesB((ographFeaturesB != null && !ographFeaturesB.isEmpty()) ? featuresRangesToSolrDocument(ographFeaturesB) : null);
                searchInteraction.setMutationB(affectedByMutationToSolrDocument(styleService, searchInteraction, ographFeaturesB));
                searchInteraction.setAsMutationB(searchInteraction.isMutationB());
            }

            searchInteraction.setFeatureCount(featureCount);
            searchInteraction.setAliasesA(!graphAliasesA.isEmpty() ? aliasesWithTypesToSolrDocument(graphAliasesA) : null);
            searchInteraction.setAliasesB(!graphAliasesB.isEmpty() ? aliasesWithTypesToSolrDocument(graphAliasesB) : null);
            searchInteraction.setAsAliasA(!graphAliasesA.isEmpty() ? aliasesWithTypesToASSolrDocument(graphAliasesA) : null);
            searchInteraction.setAsAliasB(!graphAliasesB.isEmpty() ? aliasesWithTypesToASSolrDocument(graphAliasesB) : null);
            searchInteraction.setAsXrefsA(!graphXrefsA.isEmpty() ? xrefsToASSolrDocument(graphXrefsA) : null);
            searchInteraction.setAsXrefsB(!graphXrefsB.isEmpty() ? xrefsToASSolrDocument(graphXrefsB) : null);
            searchInteraction.setAsStoichiometry(stoichiometry);
            //experiment details
            GraphExperiment experiment = (GraphExperiment) graphBinaryInteractionEvidence.getExperiment();

            graphAnnotations.addAll(experiment.getAnnotations());
            searchInteraction.setDetectionMethod((graphBinaryInteractionEvidence.getExperiment() != null && graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod() != null) ? graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod().getShortName() : null);
            searchInteraction.setDetectionMethodMIIdentifier((graphBinaryInteractionEvidence.getExperiment() != null && graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod() != null) ? graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod().getMIIdentifier() : null);
            searchInteraction.setAsIDetectionMethod(graphBinaryInteractionEvidence.getExperiment() != null ? cvToASSolrDocument(graphBinaryInteractionEvidence.getExperiment().getInteractionDetectionMethod()) : null);
            if (experiment.getParticipantDetectionMethod() != null) {
                graphIdentificationMethodsA.add((GraphCvTerm) experiment.getParticipantDetectionMethod());
                graphIdentificationMethodsB.add((GraphCvTerm) experiment.getParticipantDetectionMethod());
            }
            searchInteraction.setAsIdentificationMethodsA(!graphIdentificationMethodsA.isEmpty() ? cvTermsToASSolrDocument(graphIdentificationMethodsA) : null);
            searchInteraction.setAsIdentificationMethodsB(!graphIdentificationMethodsB.isEmpty() ? cvTermsToASSolrDocument(graphIdentificationMethodsB) : null);

            final Organism hostOrganism = experiment.getHostOrganism();
            searchInteraction.setHostOrganism(hostOrganism != null ? hostOrganism.getScientificName() : null);
            searchInteraction.setHostOrganismTaxId(hostOrganism != null ? hostOrganism.getTaxId() : null);
            searchInteraction.setHostOrganismTaxIdStyled(hostOrganism != null ? hostOrganism.getTaxId() + "__"
                    + hostOrganism.getScientificName() + "__"
                    + "#" + Integer.toHexString(styleService.getInteractorColor(String.valueOf(hostOrganism.getTaxId())).getRGB()).substring(2) : null);
            searchInteraction.setAsHostOrganism(organismToASSolrDocument(hostOrganism));
            //interaction details
            searchInteraction.setBinaryInteractionId(graphBinaryInteractionEvidence.getGraphId());// use binary counter while generating test interactions.xml
            searchInteraction.setDocumentType(DocumentType.INTERACTION);
            GraphClusteredInteraction graphClusteredInteraction = graphBinaryInteractionEvidence.getClusteredInteraction();
            Set<String> intactConfidence = new HashSet<String>();
            if (graphClusteredInteraction != null) {
                intactConfidence.add("intact-miscore:" + graphClusteredInteraction.getMiscore());
                searchInteraction.setIntactMiscore(graphClusteredInteraction.getMiscore());
                searchInteraction.setAsIntactMiscore(graphClusteredInteraction.getMiscore());
            }
            searchInteraction.setIdentifiers((graphBinaryInteractionEvidence.getIdentifiers() != null && !graphBinaryInteractionEvidence.getIdentifiers().isEmpty()) ? xrefsToSolrDocument(graphBinaryInteractionEvidence.getIdentifiers()) : null);
            Set<String> interactionIds = new HashSet<>();
            if (graphBinaryInteractionEvidence.getImexId() != null) {
                interactionIds.addAll(xrefDBAndIdToASSolrDocument(CvTermUtils.createImexDatabase().getShortName(), graphBinaryInteractionEvidence.getImexId()));
            }
            interactionIds.addAll(xrefsToASSolrDocument(graphBinaryInteractionEvidence.getIdentifiers()));
            searchInteraction.setAsInteractionIds(!interactionIds.isEmpty() ? interactionIds : null);

            searchInteraction.setConfidenceValues(!intactConfidence.isEmpty() ? intactConfidence : null);

            final String shortName = graphBinaryInteractionEvidence.getInteractionType().getShortName();
            searchInteraction.setType(graphBinaryInteractionEvidence.getInteractionType() != null ? shortName : null);
            searchInteraction.setAsType(cvToASSolrDocument(graphBinaryInteractionEvidence.getInteractionType()));

            if (searchInteraction.getType() != null) {
                final String miIdentifier = graphBinaryInteractionEvidence.getInteractionType().getMIIdentifier();
                searchInteraction.setTypeMIIdentifier(miIdentifier);
                searchInteraction.setTypeMIIdentifierStyled(miIdentifier != null ?
                        miIdentifier + "__" + shortName + "__" +
                                "#" + Integer.toHexString(styleService.getInteractionColor(miIdentifier).getRGB()).substring(2) : null);
            }
            searchInteraction.setAc(graphBinaryInteractionEvidence.getAc());

            if (graphBinaryInteractionEvidence.getConfidences() != null) {
                Set<String> confidences = confidencesToSolrDocument(graphBinaryInteractionEvidence.getConfidences());
                if (searchInteraction.getConfidenceValues() != null) {
                    searchInteraction.getConfidenceValues().addAll(confidences);
                } else {
                    searchInteraction.setConfidenceValues(confidences);
                }
            }

            graphAnnotations.addAll(graphBinaryInteractionEvidence.getAnnotations());
            searchInteraction.setAnnotations(!graphBinaryInteractionEvidence.getAnnotations().isEmpty() ? annotationsToSolrDocument(graphBinaryInteractionEvidence.getAnnotations()) : null);
            searchInteraction.setExpansionMethod((graphBinaryInteractionEvidence.getComplexExpansion() != null) ? graphBinaryInteractionEvidence.getComplexExpansion().getShortName() : null);
            searchInteraction.setAsExpansionMethod(cvToASSolrDocument(graphBinaryInteractionEvidence.getComplexExpansion()));
            searchInteraction.setXrefs((graphBinaryInteractionEvidence.getXrefs() != null && !graphBinaryInteractionEvidence.getXrefs().isEmpty()) ? xrefsToSolrDocument(graphBinaryInteractionEvidence.getXrefs()) : null);
            searchInteraction.setAsInteractionXrefs((graphBinaryInteractionEvidence.getXrefs() != null && !graphBinaryInteractionEvidence.getXrefs().isEmpty()) ? xrefsToASSolrDocument(graphBinaryInteractionEvidence.getXrefs()) : null);
            searchInteraction.setParameters((graphBinaryInteractionEvidence.getParameters() != null && !graphBinaryInteractionEvidence.getParameters().isEmpty()) ? parametersToSolrDocument(graphBinaryInteractionEvidence.getParameters()) : null);
            searchInteraction.setAsParam((graphBinaryInteractionEvidence.getParameters() != null && !graphBinaryInteractionEvidence.getParameters().isEmpty()) ? true : false);
            searchInteraction.setParameterTypes((graphBinaryInteractionEvidence.getParameters() != null && !graphBinaryInteractionEvidence.getParameters().isEmpty()) ? parameterTypeToSolrDocument(graphBinaryInteractionEvidence.getParameters()) : null);
            searchInteraction.setCreationDate(graphBinaryInteractionEvidence.getCreatedDate());
            searchInteraction.setUpdationDate(graphBinaryInteractionEvidence.getUpdatedDate());
            searchInteraction.setAsUpdationDate(DateFieldConverter.indexFieldValues(graphBinaryInteractionEvidence.getUpdatedDate()));
            searchInteraction.setChecksums((graphBinaryInteractionEvidence.getChecksums() != null && !graphBinaryInteractionEvidence.getChecksums().isEmpty()) ? checksumsToSolrDocument(graphBinaryInteractionEvidence.getChecksums()) : null);
            searchInteraction.setNegative(graphBinaryInteractionEvidence.isNegative());
            searchInteraction.setAsNegative(graphBinaryInteractionEvidence.isNegative());

            if (searchInteraction.getTaxIdA() != null && searchInteraction.getTaxIdA().equals(searchInteraction.getTaxIdB())) {
                searchInteraction.setIntraTaxId(searchInteraction.getTaxIdA());
                searchInteraction.setIntraTaxIdStyled(searchInteraction.getTaxIdA() + "__"
                        + searchInteraction.getSpeciesA() + "__"
                        + "#" + Integer.toHexString(styleService.getInteractorColor(String.valueOf(searchInteraction.getTaxIdA())).getRGB()).substring(2));
            }

            if (searchInteraction.getSpeciesA() != null && searchInteraction.getSpeciesA().equals(searchInteraction.getSpeciesB())) {
                searchInteraction.setIntraSpecies(searchInteraction.getSpeciesA());
            }

            // publications details
            if (experiment != null) {
                GraphPublication publication = (GraphPublication) experiment.getPublication();
                graphAnnotations.addAll(publication.getAnnotations());
                if (publication != null) {
                    searchInteraction.setAuthors((publication.getAuthors() != null && !publication.getAuthors().isEmpty()) ? new LinkedHashSet(publication.getAuthors()) : null);
                    searchInteraction.setAsPubAuthors((publication.getAuthors() != null && !publication.getAuthors().isEmpty()) ? authorsToASSolrDocument(publication.getAuthors()) : null);
                    String firstAuthor = (searchInteraction.getAuthors() != null && !searchInteraction.getAuthors().isEmpty()) ? searchInteraction.getAuthors().iterator().next() + " et al." : "";
                    if (!firstAuthor.isEmpty()) {
                        searchInteraction.setAsPubFirstAuthor(authorToASSolrDocument(firstAuthor));
                        if (publication.getPublicationDate() != null) {
                            int publicationYear = CommonUtility.getYearOutOfDate(publication.getPublicationDate());
                            firstAuthor = firstAuthor + " (" + publicationYear + ")";
                            searchInteraction.setAsPubYear(publicationYear);
                        }
                        firstAuthor = firstAuthor + "\t\n";
                    }
                    searchInteraction.setFirstAuthor(firstAuthor);

//                    searchInteraction.setPublicationId((publication.getAuthors() != null) ? publication.getPubmedId() : "");
                    searchInteraction.setSourceDatabase((publication.getSource() != null) ? publication.getSource().getShortName() : "");
                    searchInteraction.setAsSource(cvToASSolrDocument(publication.getSource()));
                    searchInteraction.setReleaseDate((publication.getReleasedDate() != null) ? publication.getReleasedDate() : null);
                    searchInteraction.setAsReleaseDate(DateFieldConverter.indexFieldValues(publication.getReleasedDate()));
                    searchInteraction.setPublicationIdentifiers((publication.getIdentifiers() != null && !publication.getIdentifiers().isEmpty()) ? xrefsToSolrDocument(publication.getIdentifiers()) : null);
                    HashSet<String> publicationIds = new HashSet<>();
                    if (publication.getIdentifiers() != null && !publication.getIdentifiers().isEmpty()) {
                        publicationIds.addAll(xrefsToASSolrDocument(publication.getIdentifiers()));
                    }
                    if (publication.getXrefs() != null && !publication.getXrefs().isEmpty()) {
                        publicationIds.addAll(primaryXrefsToASSolrDocument(publication.getXrefs()));
                    }
                    if (publication.getImexId() != null) {
                        publicationIds.addAll(xrefDBAndIdToASSolrDocument(CvTermUtils.createImexDatabase().getShortName(), publication.getImexId()));
                    }
                    if (!publicationIds.isEmpty()) {
                        searchInteraction.setAsPubId(publicationIds);
                    }
                    //TODO... Enable in graphdb to get from pubmedId instead
                    //this is needed for sorting on publication id
                    searchInteraction.setPublicationPubmedIdentifier((publication.getPubmedIdStr() != null) ? publication.getPubmedIdStr() : null);
                }
                searchInteraction.setPublicationAnnotations(!publication.getAnnotations().isEmpty() ? annotationValuesOnlyToSolrDocument(publication.getAnnotations()) : null);
            }
            searchInteraction.setAllAnnotations(!graphAnnotations.isEmpty() ? annotationsToSolrDocument(graphAnnotations) : null);
            searchInteraction.setAsAnnotations(!graphAnnotations.isEmpty() ? annotationsToASSolrDocument(graphAnnotations) : null);

            if (graphBinaryInteractionEvidence.getSerialisedInteraction() != null) {
                searchInteraction.setSerialisedInteraction(graphBinaryInteractionEvidence.getSerialisedInteraction().getSerialisedInteraction());
            }
        }

        return searchInteraction;
    }

    private static boolean affectedByMutationToSolrDocument(StyleService styleService, SearchInteraction searchInteraction, List<GraphFeature> ographFeatures) {
        boolean affectedByMutation = (ographFeatures != null && !ographFeatures.isEmpty()) && doesAnyFeatureHaveMutation(ographFeatures);
        if (!searchInteraction.isAffectedByMutation()) {
            searchInteraction.setAffectedByMutation(affectedByMutation);
            searchInteraction.setAsAffectedByMutation(affectedByMutation);
            searchInteraction.setAffectedByMutationStyled("none__" + affectedByMutation + "__" + "#" + Integer.toHexString(styleService.getMutationInteractionColor(affectedByMutation).getRGB()).substring(2));
        }
        return affectedByMutation;
    }

    private static void interactorToSolrDocument(List<SearchChildInteractor> searchChildInteractors, GraphInteractor graphInteractor, Organism organism, StyleService styleService) {

        SearchChildInteractor searchChildInteractor = new SearchChildInteractor();
        searchChildInteractor.setDocumentType(DocumentType.INTERACTOR);
        searchChildInteractor.setInteractorName(graphInteractor.getPreferredName());
        searchChildInteractor.setInteractorIntactName(graphInteractor.getShortName());
        searchChildInteractor.setInteractorPreferredIdentifier(SolrDocumentConverterUtils.xrefToSolrDocument(graphInteractor.getPreferredIdentifier()));
        searchChildInteractor.setInteractorDescription(graphInteractor.getFullName());
        searchChildInteractor.setInteractorAlias(aliasesWithTypesToSolrDocument(graphInteractor.getAliases()));
        searchChildInteractor.setInteractorAltIds(xrefsToSolrDocument(graphInteractor.getIdentifiers()));

        final String shortName = graphInteractor.getInteractorType().getShortName();
        searchChildInteractor.setInteractorType(shortName);

        final String interactorTypeMIIdentifier = graphInteractor.getInteractorType().getMIIdentifier();
        searchChildInteractor.setInteractorTypeMIIdentifier(interactorTypeMIIdentifier);

        // We add the interactor shape and the display name in indexing time to avoid remapping the results
        searchChildInteractor.setInteractorTypeMIIdentifierStyled(interactorTypeMIIdentifier + "__"
                + shortName + "__"
                + styleService.getInteractorShape(interactorTypeMIIdentifier));

        searchChildInteractor.setInteractorSpecies(organism != null ? organism.getScientificName() : null);
        searchChildInteractor.setInteractorTaxId(organism != null ? organism.getTaxId() : null);
        searchChildInteractor.setInteractorTaxIdStyled(organism != null ? organism.getTaxId() + "__"
                + organism.getScientificName() + "__"
                + "#" + Integer.toHexString(styleService.getInteractorColor(String.valueOf(organism.getTaxId())).getRGB()).substring(2) : null);

        searchChildInteractor.setInteractorXrefs(xrefsToSolrDocument(graphInteractor.getXrefs()));
        searchChildInteractor.setInteractorAc(graphInteractor.getAc());
        searchChildInteractor.setInteractionCount(graphInteractor.getInteractions().size());

        Collection<GraphFeature> featureEvidences = new ArrayList<>();
        if (graphInteractor.getParticipantEvidences() != null) {
            for (GraphParticipantEvidence participantEvidence : graphInteractor.getParticipantEvidences()) {
                if (participantEvidence.getFeatures() != null) {
                    featureEvidences.addAll(participantEvidence.getFeatures());
                }
            }

            searchChildInteractor.setInteractorFeatureShortLabels(featuresShortlabelToSolrDocument(featureEvidences));
        }
        searchChildInteractors.add(searchChildInteractor);
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
                    interactions.add(toSolrDocument(graphInteraction, binaryCounter, styleService));
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

                log.info("Saving " + interactions.size() + " interactions");
                interactionIndexService.save(interactions, Duration.ofMinutes(5));
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