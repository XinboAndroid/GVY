import com.navis.apex.business.model.GroovyInjectionBase

import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery;
import com.navis.apex.business.model.GroovyInjectionBase;

import com.navis.argo.business.model.GeneralReference;
import com.navis.argo.ArgoField;

class MatUpdateTagId{

    public void execute(String unitId) {
        try{
            List refList = null;
            refList = getGenRef(unitId);
            println ("refList.size() ::: " + refList.size())

            if (refList.size() > 0)
            {
                Iterator iterRefList = refList.iterator()
                while (iterRefList.hasNext())
                {
                    def genRef = iterRefList.next();
                    def tagId = genRef.getRefValue2();
                    println("tagId :::: "+ tagId);
                    genRef.setRefValue2("");
                    tagId = genRef.getRefValue2();
                    println("After update -- tagId :::: "+ tagId);
                }
            }
        }catch (Exception e){
            println("Error while updating the tagId for ::::: "+unitId);
        }

    }

    public List getGenRef(String unitId)
    {
        try {
            DomainQuery dq = QueryUtils.createDomainQuery("GeneralReference").addDqPredicate(PredicateFactory.eq(ArgoField.REF_TYPE, "DRAYMAN")).addDqPredicate(PredicateFactory.eq(ArgoField.REF_ID1, "SNXMSG")).addDqPredicate(PredicateFactory.eq(ArgoField.REF_ID2, "LANE")).addDqPredicate(PredicateFactory.eq(ArgoField.REF_VALUE4, unitId));
            println(dq);
            List genRef = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println ("genRef.size() ::: " + genRef.size())
            return (genRef);
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }
}