package utilities;

import psidev.psi.mi.jami.model.Xref;
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

}
