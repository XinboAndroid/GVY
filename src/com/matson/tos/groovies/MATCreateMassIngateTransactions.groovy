import com.navis.argo.business.api.GroovyApi
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.metafields.entity.EntityId

public class MATCreateMassIngateTransactions extends AbstractTableViewCommand {
    void execute(EntityId entityId, List<Serializable> gkeys, Map<String, Object> params) {
/*GroovyApi groovyApi = new GroovyApi();
    groovyApi.getGroovyClassInstance("MATGvyMassGateProcess").execute();*/
        Map inParam = new HashMap();
        Map outParam = new HashMap();
        this.executeInTransaction("MATGvyMassGateProcess", inParam, outParam );
    }
}

