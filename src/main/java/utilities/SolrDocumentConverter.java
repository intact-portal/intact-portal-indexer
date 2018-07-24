package utilities;

import org.apache.commons.lang.StringUtils;
import psidev.psi.mi.jami.model.*;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphCvTerm;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphFeatureEvidence;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by anjali on 12/07/18.
 */
public class SolrDocumentConverter {

    public static String xrefToSolrDocument(Xref xref) {
        String solr_xref = xref.getId() + " (" + xref.getDatabase().getShortName() + ")";
        return solr_xref;

    }

    public static Set<String> xrefsToSolrDocument(Collection<Xref> xrefs) {

        Set<String> searchInteractorXrefs = new HashSet<>();
        for (Xref xref : xrefs) {
            searchInteractorXrefs.add(xref.getId() + " (" + xref.getDatabase().getShortName() + ")");
        }

        return searchInteractorXrefs;

    }

    public static Set<String> aliasesToSolrDocument(Collection<Alias> aliases) {

        Set<String> searchInteractorAliases = new HashSet<>();
        for (Alias alias : aliases) {
            searchInteractorAliases.add(alias.getName() + " (" + alias.getType() + ")");
        }
        return searchInteractorAliases;

    }

    public static Set<String> annotationsToSolrDocument(Collection<Annotation> annotations) {

        Set<String> searchInteractorAliases = new HashSet<>();
        for (Annotation annotation : annotations) {
            searchInteractorAliases.add(annotation.getTopic().getShortName() + " (" + annotation.getValue() + ")");
        }
        return searchInteractorAliases;

    }

    public static Set<String> checksumsToSolrDocument(Collection<Checksum> checksums) {

        Set<String> searchInteractorAliases = new HashSet<>();
        for (Checksum checksum : checksums) {
            searchInteractorAliases.add(checksum.getMethod().getShortName() + " (" + checksum.getValue() + ")");
        }
        return searchInteractorAliases;

    }

    public static Set<String> featuresToSolrDocument(Collection<GraphFeatureEvidence> featureEvidences) {

        Set<String> features = new HashSet<>();
        for (FeatureEvidence featureEvidence : featureEvidences) {
            String ranges = StringUtils.join(featureEvidence.getRanges(), ",");
            features.add(featureEvidence.getType().getShortName()+ ":" +ranges+ (featureEvidence.getShortName()!=null?featureEvidence.getShortName():""));
        }
        return features;

    }

    public static Set<String> cvTermsToSolrDocument(Collection<GraphCvTerm> cvTerms) {

        Set<String> terms = new HashSet<>();
        for (CvTerm cvTerm : cvTerms) {

            terms.add(cvTerm.getShortName());
        }
        return terms;

    }

}
