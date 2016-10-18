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

import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.api.UnitFinder;


/*
**********************************************************************
* Srno   Date	        Changer	 	 Change Description
* A0     05/22/09	Steven Bauer	 Added getUfvActiveInComplex
* A1     06/08/09	Steven Bauer	 Added setTimeIn
* A2     11/01/11                GR		Removed Prints
**********************************************************************
*/
public class GvyUnitLookup {

/** Assumes there is only one advised ufv per unit */
    public Object lookupFacility(Object id) {

        try {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UFV_UNIT,id ));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if(list != null) {
                Iterator iter = list.iterator();
                while(iter.hasNext()) {
                    def visit = iter.next();
                    if(visit.getFieldValue("ufvTransitState").equals(com.navis.inventory.business.atoms.UfvTransitStateEnum.S10_ADVISED)) {
                        return visit;
                    }
                }}
            return null;
        } catch (Exception e) {
            println("Exception in GvyUnitLookup "+e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public Object lookupActiveUnit(Object id) {

        try {
            DomainQuery dq = QueryUtils.createDomainQuery("Unit");

            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_ID,id ));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if(list != null) {
                Iterator iter = list.iterator();
                while(iter.hasNext()) {
                    def unit = iter.next();
                    if(unit.getFieldValue("unitVisitState").equals(
                            com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE)) {
                        return unit;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            println("Exception in GvyUnitLookup "+e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public void copyFeild(String key,Object oldUnit,Object newUnit) {
        def value = oldUnit.getFieldValue(key);
        println("Key="+key+" value="+value);
        if(value != null)  newUnit.setFieldValue(key,value);
        println("Key="+key+" value="+value+" new val="+newUnit.getFieldValue(key));
    }

    public void copyCVFeild(String key,Object oldUnit,Object newUnit) {
        def value = oldUnit.getFieldValue(key+".cvId");
        println("Key="+key+" value="+value);
        if(value != null)  newUnit.setFieldValue(key,value);
    }

    public void copyUnitValues(Object oldUnit,Object newUnit) {
        copyFeild("unitFreightKind",oldUnit,newUnit);
        copyFeild("unitSealNbr1",oldUnit,newUnit);
        copyFeild("unitSealNbr2",oldUnit,newUnit);
        copyFeild("unitSealNbr3",oldUnit,newUnit);
        copyFeild("unitSealNbr4",oldUnit,newUnit);
        copyFeild("unitCategory",oldUnit,newUnit);
        copyFeild("unitDrayStatus",oldUnit,newUnit);
        copyFeild("unitLineOperator",oldUnit,newUnit);

        copyFeild("unitDeclaredIbCv",oldUnit,newUnit);
        copyFeild("unitIntendedObCv",oldUnit,newUnit);
        copyFeild("unitActiveUfv.ufvActualIbCv",oldUnit,newUnit);
        copyFeild("unitActiveUfv.ufvActualObCv",oldUnit,newUnit);
        copyFeild("unitActiveUfv.ufvIntendedObCv",oldUnit,newUnit);
        copyFeild("unitActiveUfv.ufvArrivePosition",oldUnit,newUnit);

        copyFeild("unitActiveUfv.ufvLastFreeDay",oldUnit,newUnit);

        copyFeild("unitGoods.gdsConsigneeBzu",oldUnit,newUnit);
        copyFeild("unitGoods.gdsShipperBzu",oldUnit,newUnit);
        copyFeild("unitGoods.gdsBlNbr",oldUnit,newUnit);
        copyFeild("unitGoods.gdsHazards",oldUnit,newUnit);
        copyFeild("unitGoods.gdsOrigin",oldUnit,newUnit);
        copyFeild("unitGoods.gdsDestination",oldUnit,newUnit);
        copyFeild("unitGoods.gdsCommodity",oldUnit,newUnit);

        copyFeild("unitGoods.gdsHazards",oldUnit,newUnit);

        copyFeild("unitGoods.gdsReeferRqmnts.rfreqTempRequiredC",oldUnit,newUnit);
        copyFeild("unitGoods.gdsReeferRqmnts.rfreqTempLimitMaxC",oldUnit,newUnit);
        copyFeild("unitGoods.gdsReeferRqmnts.rfreqTempLimitMinC",oldUnit,newUnit);
        copyFeild("unitGoods.gdsReeferRqmnts.rfreqTempShowFarenheit",oldUnit,newUnit);
        copyFeild("unitGoods.gdsReeferRqmnts.rfreqVentRequired",oldUnit,newUnit);
        copyFeild("unitGoods.gdsReeferRqmnts.rfreqVentUnit",oldUnit,newUnit);

        copyFeild("rtgGroup",oldUnit,newUnit);
        copyFeild("rtgPOL",oldUnit,newUnit);
        copyFeild("rtgPOD1",oldUnit,newUnit);
        copyFeild("rtgPOD2",oldUnit,newUnit);
        copyFeild("rtgOPL",oldUnit,newUnit);
        copyFeild("rtgTruckingCompany",oldUnit,newUnit);
        copyFeild("rtgCarrierService",oldUnit,newUnit);

        copyFeild("unitFlexString01",oldUnit,newUnit);  // Consignee PO #
        copyFeild("unitFlexString02",oldUnit,newUnit);  // Release To
        copyFeild("unitFlexString03",oldUnit,newUnit);  // CSR ID
        copyFeild("unitFlexString04",oldUnit,newUnit);  // Advanced VVD
        copyFeild("unitFlexString05",oldUnit,newUnit);  // Det Code
        copyFeild("unitFlexString06",oldUnit,newUnit);  // Stif Shpr
        copyFeild("unitFlexString08",oldUnit,newUnit);  // Priority Stow

        copyFeild("unitSpecialStow",oldUnit,newUnit);
        copyFeild("unitSpecialStow2",oldUnit,newUnit);
        copyFeild("unitSpecialStow3",oldUnit,newUnit);
        copyFeild("unitRemark",oldUnit,newUnit);

        // Holds
        try {
            ServicesManager sm = (ServicesManager)Roastery.getBean("servicesManager");
            sm.copyActiveFlags (oldUnit, newUnit);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /* Not working
         Collection collect =  com.navis.services.business.rules.Flag.findAllFlagsForEntity(oldUnit);
         println("Flags="+collect);
         Iterator iter = collect.iterator();
          println("Flags="+collect);

         while(iter.hasMore()) {
            Object o = iter.next();
            println("Hold="+o);
            com.navis.services.business.rules.Flag.createFlag(o.getFlagFlagType(), newUnit, null, o.getFlagNote() );
          }
         */



    }

    public String setOwner() {
        try {
            DomainQuery dq = QueryUtils.createDomainQuery("Unit");
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if(list != null) {
                Iterator iter = list.iterator();

                while(iter.hasNext()) {
                    def unit = iter.next();
                    def ownerCode =unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId");
                    if(ownerCode == null) ownerCode = "";
                    if(ownerCode.equals('MATU') || ownerCode.equals('ANLC') || ownerCode.equals('ANZU') || ownerCode.equals('APLU') ||
                            ownerCode.equals('CCLU') || ownerCode.equals('CSXU') || ownerCode.equals('DOLU') || ownerCode.equals('FSCU') ||
                            ownerCode.equals('HLCU') || ownerCode.equals('MAEU') || ownerCode.equals('MSGU') || ownerCode.equals('MSLU') ||
                            ownerCode.equals('NYKU') || ownerCode.equals('POLU') ||  ownerCode.equals('PONU') || ownerCode.equals('SHOW') ||
                            ownerCode.equals('ZCSU') || ownerCode.equals('HSDU') || ownerCode.equals('FHSU') || ownerCode.equals('CPSU') )
                    {
                        unit.setUnitFlexString13(ownerCode);
                        println("unit="+unit+" "+ownerCode);
                    }

                    else{
                        unit.setUnitFlexString13('LEAS');
                        println("unit="+unit+" "+ownerCode);
                    }


                }
            }
            return "Done";
        } catch (Exception e) {
            println("Exception in GvyUnitLookup "+e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

/**
 *  Find the current unit by the foriegn key
 */
    public Object  findCurrentUnit(String id) {
        try {
            DomainQuery dq = QueryUtils.createDomainQuery("Unit");

            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_FOREIGN_HOST_KEY,id ));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if(list != null) {
                Iterator iter = list.iterator();
                while(iter.hasNext()) {
                    def unit = iter.next();
                    return unit;
                }
            }

            return null;
        } catch (Exception e) {
            println("Exception in GvyUnitLookup "+e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /*
     *  Find active ufv by Complex
     */
    public Object getUfvActiveInComplex(String unitId)
    {
        def ufvActiveInComplex = null;
        try
        {
            def ufvSet = findVisitStateActiveUnit(unitId)
            //Check for Multiple ufv in Active state: Depart the Active unit First
            for(aUfvActive in ufvSet){
                if (UnitVisitStateEnum.ACTIVE.equals(aUfvActive.getUfvVisitState())) {
                    ufvActiveInComplex = aUfvActive
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return ufvActiveInComplex
    }


    // Method returns a Set of Complex Level Master State Active Units
    public Set findVisitStateActiveUnit(String unitId)
    {
        Set unitUfvSet = null
        try{

            def injBase = new GroovyInjectionBase()
            def unitFinder = injBase.getUnitFinder()
            def complex = ContextHelper.getThreadComplex();

            def inEquipment = Equipment.loadEquipment(unitId);
            def inUnit = unitFinder.findActiveUnit(complex,inEquipment)
            unitUfvSet = inUnit != null ? inUnit.getUnitUfvSet() : null;
        }catch(Exception e){
            e.printStackTrace()
        }
        return unitUfvSet
    }

    public void setTimeIn(unit) {
        def oldUnit = findCurrentUnit(unit.unitId);
        if(oldUnit != null && oldUnit.unitActiveUfv != null) {
            unit.unitActiveUfv.ufvTimeIn = oldUnit.unitActiveUfv.ufvTimeIn;
        }
    }
}
