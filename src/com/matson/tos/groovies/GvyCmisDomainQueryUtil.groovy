import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.inventory.business.units.EquipmentState
import com.navis.services.business.event.*

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
import com.navis.services.business.rules.Flag;
import com.navis.services.business.rules.FlagType
import com.navis.services.ServicesField;
import com.navis.argo.business.api.ArgoUtils
import com.navis.framework.persistence.HibernateApi;

/*
* Class Fetches Field Values for Fields/Methods that return field GKey
* A1 - Added Change for Query Oracle 10.2.0.5 Upgrade issue
*/
public class GvyCmisDomainQueryUtil
{

    //Method : Fetches POD value based on GKEY
    public String lookupRtgPOD(String id) {
        def prevPod = ''
        try{
            if(id == null){
                return prevPod;
            }
            DomainQuery dq = QueryUtils.createDomainQuery("RoutingPoint").addDqPredicate(PredicateFactory.eq(com.navis.argo.ArgoRefField.POINT_GKEY, id));
            RoutingPoint r = (RoutingPoint)HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
            if(r == null)  {
                return prevPod;
            }
            //prevPod = r.getRtgPOD1();
            prevPod = r.getPointActualPOD() != null ? r.getPointActualPOD().getPointId() : ''
        }catch(Exception e){
            e.printStackTrace()
        }
        return prevPod
    }//Method Ends


    //Method : Fetches Commodity Short name based on GKEY
    public String lookupCommodity(String id) {
        DomainQuery dq = QueryUtils.createDomainQuery("Commodity").addDqPredicate(PredicateFactory.eq(com.navis.argo.ArgoRefField.CMDY_GKEY, id));
        Commodity c = (Commodity)HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
        if(c == null)  {
            return "";
        }
        return c.getCmdyShortName();

    }

    //Method to Delete Hold/Flag
    public void deleteFlagType(String inFlagId, Object inLogicalEntity)
    {
        FlagType flagType = FlagType.findFlagType(inFlagId);
        DomainQuery dq = QueryUtils.createDomainQuery("Flag").addDqPredicate(PredicateFactory.eq(ServicesField.FLAG_FLAG_TYPE, flagType.getFlgtypGkey())).addDqPredicate(PredicateFactory.eq(ServicesField.FLAG_APPLIED_TO_PRIMARY_KEY, inLogicalEntity.getPrimaryKey()))
        List flags = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        for( i in flags) {
            Flag flag = (Flag) i;
            ArgoUtils.carefulDelete(flag);
        }

    }

}//Class Ends



