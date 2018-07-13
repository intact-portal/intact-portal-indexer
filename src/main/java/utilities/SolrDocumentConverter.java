package utilities;

import psidev.psi.mi.jami.model.Alias;
import psidev.psi.mi.jami.model.Annotation;
import psidev.psi.mi.jami.model.Checksum;
import psidev.psi.mi.jami.model.Xref;

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

}
