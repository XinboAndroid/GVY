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

import com.navis.inventory.business.api.UnitField
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.units.*
import com.navis.inventory.business.api.UnitFinder
import com.navis.argo.business.reference.Container
import com.navis.services.business.rules.Flag;
import com.navis.services.business.rules.Veto;
import com.navis.services.business.rules.FlagType;
import com.navis.services.ServicesField;
import com.navis.argo.business.reference.Chassis;
import com.navis.argo.ArgoField;

class MatCancelPermission{

/*Cancel the permission by creating new record in Veto entity for the granted permission*/

    public void execute(String unitId) {
        println("Started calling MatCancelPermission.execute <<>>> "+unitId);
        def inj = new GroovyInjectionBase();
        FlagType ftype = FlagType.findFlagType("STOP - MTY CONTAINER TOO HEAVY- CALL GATE SUPPORT");

        List unitList = null;
        List flagList = null;
        unitList = getActiveUnits(unitId);
        println("unitList.size() "+unitList.size());
        Iterator unitIterator = unitList.iterator();
        String ueEquipment = null;

        while(unitIterator.hasNext())
        {
            def unit = unitIterator.next();
            ueEquipment = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqIdFull");
        }

        flagList = getFlagDetails(ueEquipment,ftype);

        Iterator flagIterator = flagList.iterator();
        while(flagIterator.hasNext())
        {
            def flag = flagIterator.next();
            println("Granter:::"+flag.flagAppliedBy)

            String vetoFound = getVetoDetails(flag);
            println("Is Permission Cancled :::"+vetoFound);

            if (vetoFound == "N") {
                try {
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
                    println("STOP - MTY CONTAINER TOO HEAVY- CALL GATE SUPPORT permission calcled for ::::"+veto.vetoGkey + " Equipment :::"+flag.flagAppliedToNaturalKey + " after UNIT INGATE");
                }catch (Exception e){
                    println ("Error while inserting veto entity :::"+e);
                }
            }
            else {
                println("STOP - MTY CONTAINER TOO HEAVY- CALL GATE SUPPORT Permission is already granted for Flag Gkey ::: "+  flag.flagGkey +" Equipment :::"+ flag.flagAppliedToNaturalKey);
            }
        }
    }

    public List getFlagDetails(String unitID, FlagType inFlagType)
    {

        try {
            ArrayList flags = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("Flag").addDqPredicate(PredicateFactory.eq(ServicesField.FLAG_FLAG_TYPE, inFlagType.getFlgtypGkey())).addDqPredicate(PredicateFactory.eq(ServicesField.FLAG_APPLIED_TO_NATURAL_KEY, unitID)).addDqOrdering(Ordering.desc(ServicesField.FLAG_APPLIED_DATE));
            println("dq:::::::::"+dq)
            def flagsList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After flagsList:::: "+flagsList.size());
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
            println("dq:::::::::"+dq)
            def vetoList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After vetoList:::: "+vetoList.size());
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

    public List getActiveUnits(String unitId)
    {

        try {
            ArrayList units = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, unitId)).addDqOrdering(Ordering.desc(InventoryField.UFV_TIME_OF_LAST_MOVE));

            println("dq:::::::::"+dq)
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    units.add(unit);
                    break;
                }
            }
            println("unitsSize" + units.size);
            return units;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

/* Get truck tare weight by passing UnitId */

    public Double getTareWt(String ctrId) {

        Double tareWt = 0;
        Container container = Container.findContainer(ctrId);
        println("<<<<<<<<<<container>>>>>>>>>>>>"+container);
        if(container != null) {
            Iterator iter = container.iterator();
            while(iter.hasNext()) {
                def ctr = iter.next();
                println("<<<<<<<<ctr>>>>>>>>>"+ctr);
                tareWt = ctr.eqTareWeightKg;
                println("<<<<<<<<eqTareWeightKg>>>>>>>>>"+ctr.eqTareWeightKg);
            }
        }
        return tareWt;
    }
}