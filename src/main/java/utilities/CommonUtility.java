package utilities;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by anjali on 27/07/18.
 */
public class CommonUtility {

    public static Set<String> getGraphObjectsUniqueKeys(Collection<? extends Object>  graphObjects){

        Set<String> uniqueKeys=new HashSet<String>();
        for(Object obj:graphObjects){
            Class clazz = obj.getClass();
            try {
                Method method = clazz.getMethod("getUniqueKey");
                String uniqueKey = (String) method.invoke(clazz.cast(obj));
                uniqueKeys.add(uniqueKey);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
         return uniqueKeys;
    }
}
