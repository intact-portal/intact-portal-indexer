package utilities;

import psidev.psi.mi.jami.model.*;
import psidev.psi.mi.jami.utils.XrefUtils;
import uk.ac.ebi.intact.graphdb.model.nodes.GraphFeature;
import uk.ac.ebi.intact.search.interactions.utils.as.converters.TextFieldConverter;
import uk.ac.ebi.intact.search.interactions.utils.as.converters.XrefFieldConverter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SolrASDocumentConverterUtils {

    public static Set<String> xrefDBAndIdToASSolrDocument(String db, String id) {
        String shortName = "unknown";
        if (db != null) {
            shortName = db;
        }
        return XrefFieldConverter.indexFieldValues(shortName, id);
    }

    public static Set<String> xrefsToASSolrDocument(Collection<? extends Xref> xrefs) {
        Set<String> searchXrefs = new HashSet<>();
        for (Xref xref : xrefs) {
            searchXrefs.addAll(xrefToASSolrDocument(xref));
        }
        return searchXrefs;
    }

    public static Set<String> xrefToASSolrDocument(Xref xref) {
        String shortName = "unknown";
        if (xref.getDatabase() != null) {
            shortName = xref.getDatabase().getShortName();
        }
        return XrefFieldConverter.indexFieldValues(shortName, xref.getId());
    }

    public static Set<String> primaryXrefsToASSolrDocument(Collection<? extends Xref> xrefs) {
        Set<String> primaryXrefs = new HashSet<>();
        if (xrefs != null) {
            primaryXrefs.addAll(xrefsToASSolrDocument(XrefUtils.collectAllXrefsHavingQualifier(xrefs, Xref.PRIMARY_MI, Xref.PRIMARY)));
        }
        return primaryXrefs;
    }

    public static Set<String> aliasesWithTypesToASSolrDocument(Collection<? extends Alias> aliases) {
        Set<String> searchAliases = new HashSet<>();
        for (Alias alias : aliases) {
            searchAliases.addAll((XrefFieldConverter.indexFieldValues(null, alias.getName())));
        }
        return searchAliases;
    }

    public static Set<String> organismToASSolrDocument(Organism organism) {
        if (organism != null) {
            Set<String> organismIndexValues = new HashSet<>();
            organismIndexValues.addAll((TextFieldConverter.indexFieldValues("taxid", organism.getTaxId() + "", organism.getCommonName())));
            organismIndexValues.addAll((TextFieldConverter.indexFieldValues(null, null, organism.getScientificName())));// null because we don't need to store taxid again
            return organismIndexValues;
        } else {
            return null;
        }
    }

    public static Set<String> authorsToASSolrDocument(Collection<String> authors) {
        Set<String> publicationAuthors = new HashSet<>();
        for (String author : authors) {
            publicationAuthors.addAll((TextFieldConverter.indexFieldValues(null, null, author + " et al.")));
        }
        return publicationAuthors;
    }

    public static Set<String> authorToASSolrDocument(String author) {
        if (author != null) {
            Set<String> publicationAuthors = new HashSet<>();
            publicationAuthors.addAll((TextFieldConverter.indexFieldValues(null, null, author)));
            return publicationAuthors;
        } else {
            return null;
        }
    }

    public static Set<String> cvToASSolrDocument(CvTerm cv) {

        if (cv != null) {
            Set<String> cvIdentifier = new HashSet<>();
            if (cv.getMIIdentifier() != null) {
                cvIdentifier.addAll((TextFieldConverter.indexFieldValues(CvTerm.PSI_MI, cv.getMIIdentifier(), cv.getShortName())));
            } else if (cv.getMODIdentifier() != null) {
                cvIdentifier.addAll((TextFieldConverter.indexFieldValues(CvTerm.PSI_MOD, cv.getMODIdentifier(), cv.getShortName())));
            } else if (cv.getPARIdentifier() != null) {
                cvIdentifier.addAll((TextFieldConverter.indexFieldValues(CvTerm.PSI_PAR, cv.getPARIdentifier(), cv.getShortName())));
            } else if (cv.getIdentifiers() != null && !cv.getIdentifiers().isEmpty()) {
                Xref idXref = cv.getIdentifiers().iterator().next();
                cvIdentifier.addAll((TextFieldConverter.indexFieldValues(idXref.getDatabase().getShortName(), idXref.getId(), cv.getShortName())));
            } else {
                cvIdentifier.addAll((TextFieldConverter.indexFieldValues("unknown", null, cv.getShortName())));
            }
            if (cv.getFullName() != null) {
                cvIdentifier.addAll((TextFieldConverter.indexFieldValues(null, null, cv.getFullName())));
            }
            return cvIdentifier;
        } else {
            return null;
        }


    }

    public static Set<String> cvTermsToASSolrDocument(Collection<? extends CvTerm> cvTerms) {
        Set<String> terms = new HashSet<>();
        for (CvTerm cvTerm : cvTerms) {
            terms.addAll(cvToASSolrDocument(cvTerm));
        }
        return terms;

    }

    public static Set<String> featuresTypeToASSolrDocument(Collection<? extends GraphFeature> featureEvidences) {
        Set<String> featureTypes = new HashSet<>();
        for (Feature featureEvidence : featureEvidences) {
            featureTypes.addAll(cvToASSolrDocument(featureEvidence.getType()));
        }
        return featureTypes;

    }

    public static Set<String> annotationsToASSolrDocument(Collection<? extends Annotation> annotations) {

        Set<String> annotationSet = new HashSet<>();
        for (Annotation annotation : annotations) {
            annotationSet.addAll(XrefFieldConverter.indexFieldValues(annotation.getTopic().getShortName(), annotation.getValue()));
        }
        return annotationSet;

    }

}
