import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.api.GroovyApi
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.framework.util.DateUtil;
import com.navis.argo.business.atoms.EquipClassEnum;
import com.navis.orders.business.eqorders.EquipmentDeliveryOrder
import com.navis.inventory.InventoryField


public class GvyCmisEquipmentDeliveryOrder {

//Method Returns Equipment Delivey Order Object
    public EquipmentDeliveryOrder findEquipmentDeliveryOrder(String inEdoNbr)
    {
        EquipmentDeliveryOrder edo = null;
        try
        {
            //LineOperator line = LineOperator.resolveLineOprFromScopedBizUnit(inLine);
            DomainQuery dq = QueryUtils.createDomainQuery("EquipmentDeliveryOrder").addDqPredicate(PredicateFactory.eq(InventoryField.EQBO_NBR, inEdoNbr));
            List lst = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            if (lst != null) {
                if (lst.size() == 1) {
                    edo = (EquipmentDeliveryOrder)lst.get(0);
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return edo;
    }

    //Method returns Line Operator for EDO (Object EDO extends EquipmentOrderHbr )
    public String getEDOLineOperator(EquipmentDeliveryOrder edo)
    {
        def edoLine = ''
        try{
            if(edo != null){
                edoLine = edo.getEqoLine().getBzuId()
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return edoLine
    }
}