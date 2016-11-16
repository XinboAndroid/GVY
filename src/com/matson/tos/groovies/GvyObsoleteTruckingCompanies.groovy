import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.road.business.model.TruckingCompany;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.ArgoRefField;
import com.navis.security.business.user.BaseUser
import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;


public class GvyObsoleteTruckingCompanies extends GroovyInjectionBase{

    public String execute(Map inParameters) {

        try{

            DomainQuery dq = QueryUtils.createDomainQuery("TruckingCompany");
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ROLE, BizRoleEnum.HAULIER));
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE));
            dq.addDqPredicate(PredicateFactory.isNull(ArgoRefField.BZU_PER_UNIT_GUARANTEE_LIMIT));

            def truckList =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("NonHonTuckerList="+(truckList != null ? truckList.size() : "0"))
            for(atruck in truckList) {
                println("ObsoleteTruckCmpy="+atruck.getBzuId());
                atruck.setFieldValue(ArgoRefField.BZU_LIFE_CYCLE_STATE, LifeCycleStateEnum.OBSOLETE);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}