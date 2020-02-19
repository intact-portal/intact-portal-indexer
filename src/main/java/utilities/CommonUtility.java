package utilities;

import uk.ac.ebi.intact.search.interactions.model.SearchChildInteractor;
import uk.ac.ebi.intact.search.interactions.model.SearchInteraction;

import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by anjali on 27/07/18.
 */
public class CommonUtility {

    public static Set<String> getGraphObjectsUniqueKeys(Collection<? extends Object> graphObjects) {

        Set<String> uniqueKeys = new HashSet<String>();
        for (Object obj : graphObjects) {
            Class clazz = obj.getClass();
            try {
                Method method = clazz.getMethod("getUniqueKey");
                String uniqueKey = (String) method.invoke(clazz.cast(obj));
                uniqueKeys.add(uniqueKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return uniqueKeys;
    }

    public static int getYearOutOfDate(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    public static Set<String> populateDefaultChildInteractors(SearchChildInteractor searchChildInteractor) {
        SearchChildInteractor myClass = new SearchChildInteractor();
        Class objClass = myClass.getClass();
        Set<String> childInteractorDefaultCollection = new HashSet<>();
        try {
            Method[] methods = objClass.getMethods();
            for (Method method : methods) {
                if (method.getName().startsWith("get")) {
                    Object object = method.invoke(searchChildInteractor);
                    if (object instanceof String) {
                        childInteractorDefaultCollection.add((String) object);
                    } else if (object instanceof Set) {
                        childInteractorDefaultCollection.addAll(((HashSet<String>) object));
                    } else if (object instanceof Integer) {
                        childInteractorDefaultCollection.add(((Integer) object) + "");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return childInteractorDefaultCollection;
    }

    //only for testing
    public static void saveInteractionInDisc(Collection<SearchInteraction> interactions) {
        XMLEncoder encoder = null;
        try {
            encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream("./src/test/resources/Interactions.xml")));
        } catch (FileNotFoundException fileNotFound) {
            System.out.println("ERROR: While Creating or Opening the File dvd.xml");
        }
        encoder.writeObject(interactions);
        encoder.close();
    }
}
