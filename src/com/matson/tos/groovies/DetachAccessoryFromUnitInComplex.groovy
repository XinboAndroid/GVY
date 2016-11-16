/*
* Srno  Doer  Date       comment
* A1    GR    05/05/11   Added Obsolete Chassis Check
* A2    GR    05/26/11   For RM trans depart Active SitUnit
*/
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.reference.Chassis
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.Equipment;
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.inventory.business.atoms.EqUnitRoleEnum
import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;

import java.util.List;
import java.util.Set;

/**
 * Detaches a chassis from a unit facility visit that has departed the facility but remains in the complex.
 * This task is required so that chassis attached to a dray-off unit can be returned back into the gate.
 */
public class DetachChassisFromUnitInComplex {


    public static String BEAN_ID = "detachChassisFromUnitInComplex"


    public void departActiveSitUnit(TransactionAndVisitHolder dao, api) {
        TruckTransaction tran = dao.tran
        tran.setTranChsPosition(null);
        Container cntr = tran.tranContainer
        if (!cntr)
            return
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
        UnitEquipment unitEq = unitFinder.findActiveUeUsingEqInAnyRole(null, ContextHelper.getThreadComplex(), cntr)
        if (!unitEq)
            return

        Unit unit = unitEq.ueUnit
        if (!unit)
            return
        if("SIT".equals(unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")) && UnitVisitStateEnum.ACTIVE.equals(unit.unitActiveUfv.getUfvVisitState())){
            unit.makeDeparted();
            //Set Sparcs Visibility to false
            Set<UnitFacilityVisit> ufvSet = unit.getUnitUfvSet();
            for(UnitFacilityVisit ufv :ufvSet) {
                if(!ufv.isActive()) {
                    ufv.setUfvVisibleInSparcs(Boolean.FALSE);
                }
            }
            HibernateApi.getInstance().flush()
            return;
        }
    }

    public void execute(TransactionAndVisitHolder dao, api) {
        TruckTransaction tran = dao.tran
        tran.setTranChsPosition(null);
        Chassis ch = tran.tranChassis
        if (!ch)
            return
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
        UnitEquipment unitEq = unitFinder.findActiveUeUsingEqInAnyRole(null, ContextHelper.getThreadComplex(), ch)
        if (!unitEq)
            return

        Unit unit = unitEq.ueUnit
        if (!unit)
            return
        if (EqUnitRoleEnum.PRIMARY.equals(unitEq.getUeEqRole())) {
            unit.makeRetired();
            HibernateApi.getInstance().flush()
            return;
        }
        UnitFacilityVisit ufv = unit.getUfvForFacilityNewest(ContextHelper.getThreadFacility())
        if (!ufv)
            return

        // uncomment the if condition below when chassis inventory is managed properly
        //if (!ufv.isInFacility()) {
        unit.swipeChsByOwnersChs()
        //}
        // The flush is required to synchronize memory state of the unit with database state of the unit
        // so that the next findActiveUe will not result in an active chassis unit equipment.
        HibernateApi.getInstance().flush()
    }

    //A1
    public void obsoleteChasCheck(inDao)
    {
        def chassis = inDao.tran.tranChassis
        if(chassis != null){
            LifeCycleStateEnum chasLifeCycle = chassis.eqLifeCycleState
            if(LifeCycleStateEnum.OBSOLETE.equals(chasLifeCycle)){
                throw com.navis.framework.util.BizFailure.create("CHASSIS IS OBSOLETE. -- CALL GATE SUPPORT -- ");
            }
        }
    }
    //Method to detach the chassis from any unit that is departed in the facility but active in complex

    public void detachChassisFromDepartedUnit(Unit inUnit, GroovyApi inApi) {
        if (inUnit != null) {
            UnitEquipment unitEquipment = inUnit.getUnitCarriageUe();
            if (unitEquipment != null) {
                UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
                Equipment eq = unitEquipment.getUeEquipment();
                List<UnitEquipment> unitEqList = unitFinder.findNotDetachedUnitEquipment(null, ContextHelper.getThreadComplex(), eq, false);
                if (unitEqList != null && !unitEqList.isEmpty()) {
                    for (UnitEquipment unitEq : unitEqList) {
                        if (unitEquipment != null) {
                            try {
                                Unit unit = unitEq.getUeUnit();
                                if (unit != null && unit.getUnitVisitState() != null && UnitVisitStateEnum.ACTIVE.equals(unit.getUnitVisitState())
                                        && !unit.equals(inUnit)) {
                                    unit.swipeChsByOwnersChs();
                                    inApi.logWarn("Completed dismountChassis() during Discharge");
                                }
                            } catch (Exception e) {
                                //
                            }
                        }
                    }
                }
            }
        }
    }
}
