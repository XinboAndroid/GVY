import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.GroovyEvent;

import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery;
import com.navis.argo.business.api.ArgoUtils;

import com.navis.services.business.rules.Flag;
import com.navis.services.business.rules.Veto;
import com.navis.services.business.rules.FlagType;
import com.navis.services.ServicesField;
import com.navis.argo.business.reference.Chassis;
import com.navis.argo.ArgoField;

class MatRemoveHold{

/*Raghu Iyer : Remove hold by creating new record in Veto entity*/

    public void execute(String unitId, String hold) {
        //public boolean execute(Map params){
        //String unitId = "MATU2550969";
        //def hold = "CG_INSP"
        println("Started calling MatRemoveHold.execute <<>>> "+unitId);
        def inj = new GroovyInjectionBase();
        FlagType ftype = FlagType.findFlagType(hold);
        List flagList = null;

        flagList = getFlagDetails(unitId,ftype);

        Iterator flagIterator = flagList.iterator();
        while(flagIterator.hasNext())
        {
            def flag = flagIterator.next();
            String vetoFound = getVetoDetails(flag);
            println("Is Hold already removed :::"+vetoFound);

            if (vetoFound == "N") {
                try {
                    println("Removing the hold :::");
                    Veto veto = new Veto();
                    veto.setVetoBlockedFlag(flag);
                    veto.setVetoAppliedToClass(flag.flagAppliedToClass);
                    veto.setVetoAppliedToPrimaryKey(flag.flagAppliedToPrimaryKey);
                    veto.setVetoAppliedToNaturalKey(flag.flagAppliedToNaturalKey);
                    veto.setVetoAppliedDate(ArgoUtils.timeNow());
                    veto.setVetoAppliedBy(flag.flagAppliedBy);
                    veto.setVetoCreated(ArgoUtils.timeNow());
                    veto.setVetoCreator(flag.flagAppliedBy);

                    Roastery.getHibernateApi().save(veto);
                }catch (Exception e){
                    println ("Error while inserting veto entity :::"+e);
                }
            }
            else {
                println("CG_INSP has been already removed ::: "+  flag.flagGkey +" Equipment :::"+ flag.flagAppliedToNaturalKey);
            }
        }
    }

    public List getFlagDetails(String unitID, FlagType inFlagType)
    {

        try {
            ArrayList flags = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("Flag").addDqPredicate(PredicateFactory.eq(ServicesField.FLAG_FLAG_TYPE, inFlagType.getFlgtypGkey())).addDqPredicate(PredicateFactory.eq(ServicesField.FLAG_APPLIED_TO_NATURAL_KEY, unitID)).addDqOrdering(Ordering.desc(ServicesField.FLAG_APPLIED_DATE));
            //println("dq:::::::::"+dq)
            def flagsList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            //println("After flagsList:::: "+flagsList.size());
            if(flagsList != null) {
                Iterator iter = flagsList.iterator();
                while(iter.hasNext()) {
                    def flag = iter.next();
                    flags.add(flag);
                    break;
                }
            }
            return flags;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }
    public String getVetoDetails(Flag flag)
    {
        String recFound = "N";
        try {

            DomainQuery dq = QueryUtils.createDomainQuery("Veto").addDqPredicate(PredicateFactory.eq(ServicesField.VETO_BLOCKED_FLAG, flag.getFlagGkey()));
            //println("dq:::::::::"+dq)
            def vetoList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            //println("After vetoList:::: "+vetoList.size());
            if(vetoList != null) {
                Iterator iter = vetoList.iterator();
                while(iter.hasNext()) {
                    def veto = iter.next();
                    recFound = "Y";
                }
            }
            return recFound;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

}