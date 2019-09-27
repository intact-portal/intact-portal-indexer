package utilities;

import org.apache.commons.lang.StringUtils;
import psidev.psi.mi.jami.model.*;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphFeature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by anjali on 12/07/18.
 */
public class SolrDocumentConverterUtils {

    public static String xrefToSolrDocument(Xref xref) {
        return xref.getId() + " (" + xref.getDatabase().getShortName() + ")";

    }

    public static Set<String> xrefsToSolrDocument(Collection<? extends Xref> xrefs) {

        Set<String> searchInteractorXrefs = new HashSet<>();
        for (Xref xref : xrefs) {
            searchInteractorXrefs.add(xref.getId() + " (" + xref.getDatabase().getShortName() + ")");
        }

        return searchInteractorXrefs;

    }

    public static Set<String> aliasesToSolrDocument(Collection<? extends Alias> aliases) {

        Set<String> searchInteractorAliases = new HashSet<>();
        for (Alias alias : aliases) {
            searchInteractorAliases.add(alias.getName() + " (" + alias.getType() + ")");
        }
        return searchInteractorAliases;

    }

    public static Set<String> annotationsToSolrDocument(Collection<? extends Annotation> annotations) {

        Set<String> searchInteractorAliases = new HashSet<>();
        for (Annotation annotation : annotations) {
            searchInteractorAliases.add(annotation.getTopic().getShortName() + " (" + annotation.getValue() + ")");
        }
        return searchInteractorAliases;

    }

    public static Set<String> checksumsToSolrDocument(Collection<? extends Checksum> checksums) {

        Set<String> searchInteractorAliases = new HashSet<>();
        for (Checksum checksum : checksums) {
            searchInteractorAliases.add(checksum.getMethod().getShortName() + " (" + checksum.getValue() + ")");
        }
        return searchInteractorAliases;

    }

    public static Set<String> featuresToSolrDocument(Collection<? extends GraphFeature> featureEvidences) {

        Set<String> features = new HashSet<>();
        for (Feature featureEvidence : featureEvidences) {
            String ranges;
            if (featureEvidence.getRanges() != null) {
                ranges = StringUtils.join(featureEvidence.getRanges(), ",");
            } else {
                ranges = "";
            }
            features.add(featureEvidence.getType().getShortName() + ":" + ranges + "(" + (featureEvidence.getShortName() != null ? featureEvidence.getShortName() : "") + ")");
        }
        return features;

    }

    public static Set<String> featuresShortlabelToSolrDocument(Collection<? extends GraphFeature> featureEvidences) {

        Set<String> features = new HashSet<>();
        for (Feature featureEvidence : featureEvidences) {
            features.add(featureEvidence.getShortName());
        }
        return features;

    }

    public static boolean doesAnyFeatureHaveDisruptedMutation(Collection<? extends GraphFeature> featureEvidences) {

        /*TODO... Code to be changed when parent child relationship is stored in graphdb*/
        boolean isDisruptedMutation = false;
        ArrayList<String> mutationIdentifiers = new ArrayList<>();
        mutationIdentifiers.add("MI:0429");
        mutationIdentifiers.add("MI:1128");
        mutationIdentifiers.add("MI:1129");
        mutationIdentifiers.add("MI:0573");
        mutationIdentifiers.add("MI:0119");
        mutationIdentifiers.add("MI:0118");

        for (Feature featureEvidence : featureEvidences) {
            if (featureEvidence.getType() != null && featureEvidence.getType().getMIIdentifier() != null &&
                    mutationIdentifiers.contains(featureEvidence.getType().getMIIdentifier())) {
                isDisruptedMutation = true;
                break;
            }
        }
        return isDisruptedMutation;

    }

    public static Set<String> cvTermsToSolrDocument(Collection<? extends CvTerm> cvTerms) {
        Set<String> terms = new HashSet<>();
        for (CvTerm cvTerm : cvTerms) {
            terms.add(cvTermToSolrDocument(cvTerm));
        }
        return terms;

    }

    public static String cvTermToSolrDocument(CvTerm cvTerm) {
        return (cvTerm != null) ? cvTerm.getShortName() : null;
    }

    public static Set<String> confidencesToSolrDocument(Collection<? extends Confidence> graphConfidences) {

        Set<String> confidences = new HashSet<>();
        for (Confidence confidence : graphConfidences) {
            confidences.add(confidence.getType().getShortName() + "(" + confidence.getValue() + ")");
        }
        return confidences;

    }

    public static Set<String> parametersToSolrDocument(Collection<? extends Parameter> graphParameters) {

        Set<String> parameters = new HashSet<>();
        for (Parameter parameter : graphParameters) {
            parameters.add(parameter.getType().getShortName() + ":" + parameter.getValue() + "(" + parameter.getUnit().getShortName() + ")");
        }
        return parameters;

    }


}
