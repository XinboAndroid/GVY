import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.api.GroovyApi;
/**
 * Lookup a implementation by version number.
 * And Example:
 *         com.navis.apex.business.model.GroovyInjectionBase api = new  com.navis.apex.business.model.GroovyInjectionBase();
 *         api.getGroovyClassInstance("N4EnvClassLoader");
 *         api.getGroovyClassInstance("N4EnvClassLoader").getEnvClass("AcetsConfig");
 * @author Steven Bauer
 * A1  GR   10/25/11  Removed Weblogic API
 * A2  GR    11/10/11  TOS2.1 Get Environment Variable
 */
public class N4EnvClassLoader extends GroovyInjectionBase{
    /**
     * Configure the version you want deployed for each environment.
     */
    private static int devVersion = 1;
    private static int qaVersion  = 0;
    private static int preVersion = 0;
    private static int prodVersion = 0;

    private static String envType;
    private static N4EnvClassLoader loader;

    private Map<String, LinkedHashMap> map;

    static {
        GroovyApi groovyApi = new GroovyApi();

        HashMap map = new HashMap();
// Versions muist be in order from largest to smallest!
        map.put("AcetsConfig",[4 : 'AcetsConfig4',3 : 'AcetsConfig3', 1 : 'AcetsConfig1']);

        loader =  new N4EnvClassLoader(map);
    }

    public String getEnvVersion()  {
        String envType = groovyApi.getReferenceValue("ENV", "ENVIRONMENT", null, null, 1)
        if("PRODUCTION".equals(envType)){
            return "";
        }
        return envType+" ";
    }

    public Object getGroovyClassInstance(String clazz) {
        int version = getEnvVersion();
        LinkedHashMap clazzMap = map.get(clazz);
        if(clazzMap == null ) return null;
        def entry = null;
        clazzMap.each{ if(it.key <= version) entry = it.value; }
        if(entry != null) return super.getGroovyClassInstance(entry);
        else return null;
    }

    public N4EnvClassLoader() {
        this.map = loader.map;
    }
    /**
     * Constructor for a mapped class lookup.
     */
    public N4EnvClassLoader(Map<String, LinkedHashMap> map) {
        this.map = map;
    }

    /**
     * Add entries to List implementations for classes you want to
     * load using the
     */
    public static Object getEnvClass(String clazz) {
        if(loader != null)  return loader.getGroovyClassInstance(clazz);
        return null;
    }


}