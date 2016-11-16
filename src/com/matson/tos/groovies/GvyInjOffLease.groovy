/*
**********************************************************************
* Srno   Date	             Changer	 Change Description
* A1     02/16/09       Glenn Raposo	 Added Method for OffLease unAssign
**********************************************************************
*/

import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.api.ServicesManager;
import com.navis.framework.business.Roastery;
import com.navis.services.business.rules.MockGuardian;
import com.navis.argo.business.api.Guardian;
import com.navis.argo.business.api.ArgoUtils
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.ArgoRefField;
import com.navis.services.ServicesField;
import com.navis.services.business.rules.Flag;
import com.navis.services.business.rules.FlagType
import com.navis.framework.persistence.HibernateApi;

class GvyInjOffLease {

    public String applyHold( Object unit, String note) {
        def servicesMgr = (ServicesManager)Roastery.getBean("servicesManager");

        //applyHold(String s, LogicalEntity logicalentity, String s1, String s2, boolean flag)
        servicesMgr.applyHold( "INGATE", unit, note, "null", true);
    }

    public String releaseHold( Object unit, String note) {
        def servicesMgr = (ServicesManager)Roastery.getBean("servicesManager");
        // servicesMgr.applyGuardedPermission( "INGATE", unit, null, null, note);
        deleteFlagType("INGATE",unit)
    }


    public void deleteFlagType(String inFlagId, Object inLogicalEntity)
    {
        FlagType flagType = FlagType.findFlagType(inFlagId);
        DomainQuery dq = QueryUtils.createDomainQuery("Flag").addDqPredicate(PredicateFactory.eq(ServicesField.FLAG_FLAG_TYPE, flagType.getFlgtypGkey())).addDqPredicate(PredicateFactory.eq(ServicesField.FLAG_APPLIED_TO_PRIMARY_KEY, inLogicalEntity.getPrimaryKey()))
        List flags = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        for( i in flags) {
            Flag flag = (Flag) i;
            println("FLAG ----------------"+flag)
            ArgoUtils.carefulDelete(flag);
        }

    }


}




