package utilities;

import psidev.psi.mi.jami.model.Alias;
import psidev.psi.mi.jami.model.Organism;
import psidev.psi.mi.jami.model.Xref;
import uk.ac.ebi.intact.search.interactions.utils.as.converters.TextFieldConverter;
import uk.ac.ebi.intact.search.interactions.utils.as.converters.XrefFieldConverter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SolrASDocumentConverterUtils {

    public static Set<String> xrefsToASSolrDocument(Collection<? extends Xref> xrefs) {
        Set<String> searchInteractorXrefs = new HashSet<>();
        for (Xref xref : xrefs) {
            searchInteractorXrefs.addAll((XrefFieldConverter.indexFieldValues(xref.getDatabase().getShortName(), xref.getId())));
        }
        return searchInteractorXrefs;
    }

    public static Set<String> xrefToASSolrDocument(Xref xref) {
        return XrefFieldConverter.indexFieldValues(xref.getDatabase().getShortName(), xref.getId());
    }

    public static Set<String> aliasesWithTypesToASSolrDocument(Collection<? extends Alias> aliases) {
        Set<String> searchInteractorAliases = new HashSet<>();
        for (Alias alias : aliases) {
            searchInteractorAliases.addAll((XrefFieldConverter.indexFieldValues(null, alias.getName())));
        }
        return searchInteractorAliases;
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

}
