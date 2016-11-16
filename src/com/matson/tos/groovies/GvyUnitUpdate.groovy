import com.navis.argo.business.api.GroovyApi;
import com.navis.services.business.event.GroovyEvent;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.inventory.InventoryField;
import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import java.util.Iterator;
import java.util.Collection;


public class GvyUnitUpdate {

/** Sets all other units foriegn key null and then updates the foriegn key.
 */
    public void setForeignKey(Object newUnit) {

        def id = newUnit.unitId;
        newUnit.unitForeignHostKey= id;
        println("GvyUnitUpdate.setForeignKey "+id);

        DomainQuery dq = QueryUtils.createDomainQuery("Unit");
        dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_ID,id ));
        dq.addDqPredicate(PredicateFactory.isNotNull(InventoryField.UNIT_FOREIGN_HOST_KEY));

        def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        if(list != null) {
            Iterator iter = list.iterator();
            while(iter.hasNext()) {
                def unit = iter.next();
                if(unit != newUnit) unit.unitForeignHostKey= null;
            }
        }
    }
}