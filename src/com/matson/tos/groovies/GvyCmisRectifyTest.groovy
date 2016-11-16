import com.navis.inventory.business.api.UnitManager;
import com.navis.inventory.business.api.RectifyParms;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.reference.Equipment
import com.navis.argo.ContextHelper
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.framework.business.Roastery;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;

public class GvyCmisRectifyTest extends GroovyInjectionBase
{
    def deptUnit = null
    def inUnit = null
    //def injBase = new GroovyInjectionBase();

    public UnitVisitStateEnum getUnitVistMasterState(String unitId)
    {
        UnitVisitStateEnum visitStateEnum = null;
        try
        {
            def facility = injBase.getFacility();
            def unitFinder = injBase.getUnitFinder();
            def eq = Equipment.loadEquipment(unitId);
            def complex = unit.getFieldValue("unitComplex.cpxGkey")
            def deptUnit = null
            com.navis.framework.portal.query.DomainQuery dq = com.navis.framework.portal.QueryUtils.createDomainQuery("Unit").addDqPredicate(com.navis.framework.portal.query.PredicateFactory.eq(com.navis.inventory.business.api.UnitField.UNIT_COMPLEX, complex)).addDqPredicate(com.navis.framework.portal.query.PredicateFactory.eq(com.navis.inventory.business.api.UnitField.UNIT_VISIT_STATE, com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE)).addDqPredicate(com.navis.framework.portal.query.PredicateFactory.eq(com.navis.inventory.business.api.UnitField.UNIT_PRIMARY_EQ, eq.getPrimaryKey())).addDqOrdering(com.navis.framework.portal.Ordering.desc(com.navis.inventory.business.api.UnitField.UNIT_CREATE_TIME));
            List unitList =  com.navis.framework.persistence.HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
            if (unitList.size() > 0) {
                deptUnit = (com.navis.inventory.business.units.Unit)unitList.get(0);
            }

            visitStateEnum = deptUnit.getFieldValue("ufvVisitState")
        }catch(Exception e){
            e.printStackTrace()
        }
        return visitStateEnum
    }

    public UnitFinder getUnitFinder()
    {
        // Object bean = Roastery.getBean("unitManager");
        // (UnitManager)bean;
        def uf = (UnitFinder)Roastery.getBean("unitFinder");
        return uf
    }

    public Set findVisitStateActiveUnit(String unitId)
    {
        Set unitUfvSet = null
        try{
            def unitFinder = getUnitFinder()
            def complex = ContextHelper.getThreadComplex();
            def inEquipment = Equipment.loadEquipment(unitId);
            inUnit = unitFinder.findActiveUnit(complex,inEquipment)
            unitUfvSet = inUnit.getUnitUfvSet();
        }catch(Exception e){
            e.printStackTrace()
        }
        return unitUfvSet
    }

    public UnitFacilityVisit findActiveUnit(String unitId)
    {

        def ufv = null
        try
        {
            println("Before UnitFinder")
            Set unitUfvSet = findVisitStateActiveUnit(unitId)
            for(aUfv in unitUfvSet)
            {
                println("INSIDE THE FOR LOOP :: "+aUfv.getUfvVisitState())
                if(UnitVisitStateEnum.ACTIVE.equals(aUfv.getUfvVisitState()))
                {
                    println("INSIDE THE IF LOOP")
                    def unit1 = aUfv.getUfvUnit();
                    println("getUfvUnit UNIT ::"+unit1)
                    return aUfv
                }
            }
            println("After UnitFinder")

        }catch(Exception e){
            e.printStackTrace()
        }
        return ufv
    }

    public void TestRectifyParams(String unitId)
    {
        try
        {
            println("Before RECTIFY PARAMS")
            //UnitFacilityVisit ufv = deptUnit.getUfvForFacilityLiveOnly(facility);
            // UnitFacilityVisit ufv = findActiveUnit(unitId)
            Set ufvSet = findVisitStateActiveUnit(unitId)
            for(aUfv in ufvSet){
                println("FOR LOOP RECTFIY_PARAM")
                if (!UnitVisitStateEnum.DEPARTED.equals(aUfv.getUfvVisitState())) {
                    println("IF LOOP RECTFIY_PARAM")
                    RectifyParms rparms = new RectifyParms();
                    rparms.setUfvTransitState(UfvTransitStateEnum.S70_DEPARTED);
                    rparms.setUnitVisitState(UnitVisitStateEnum.DEPARTED)
                    aUfv.rectify(rparms);
                    println("AFTER RECTIFY PARAMS")
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method Ends

    public String execute(Map inParameters)
    {
        try
        {
            def ctrId = (String) inParameters.get("equipment-id");

            def ufv = findActiveUfv(ctrId);
            if(ufv != null){
                def unit = ufv.getUfvUnit();
                println("getUfvUnit UNIT ::"+unit)
            }else{
                println("Active getUfvUnit UNIT ::")
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public void retireUfvs(String unitId){
        try
        {
            println("VISIT STATE CHECK -1")
            Set ufvSet = findVisitStateActiveUnit(unitId)
            for(aUfv in ufvSet){
                if (!UnitVisitStateEnum.DEPARTED.equals(aUfv.getUfvVisitState())) {
                    println("VISIT STATE CHECK -2")
                    // ufv.makeRetired();
                    def unit1 = (Unit)aUfv
                    unit1.setUnitVisitState(UnitVisitStateEnum.DEPARTED);
                    unit1.setUnitTimeLastStateChange(ArgoUtils.timeNow());
                }
            }
            println("VISIT STATE CHECK -3")
        }catch(Exception e){
            e.printStackTrace()
        }
    }//Method Ends

}