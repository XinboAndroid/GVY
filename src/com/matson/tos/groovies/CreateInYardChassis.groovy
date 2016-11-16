import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Chassis
import com.navis.argo.business.model.LocPosition;
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.api.UnitManager;
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.EqUnitRoleEnum
/**
 * detaches the chassis from any active units and creates a new in yard chassis unit ready for delivery
 */

public class CreateInYardChassis {


    public static String BEAN_ID = "createInYardChassis";

    public void execute(TransactionAndVisitHolder dao, api) {
        TruckTransaction tran = dao.tran;
        String chsNbr = tran.tranChsNbrAssigned;
        Chassis ch = Chassis.findChassis(chsNbr);

        if (!ch)
            return;

        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
        UnitEquipment unitEq = unitFinder.findActiveUeUsingEqInAnyRole(null, ContextHelper.getThreadComplex(), ch);
        UnitManager unitMgr = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);

        Unit unit;
        UnitFacilityVisit ufv;

        if (unitEq != null) {
            // find the unit for bare chassis or the container with this chassis
            unit = unitEq.ueUnit
            ufv = unit.getUnitActiveUfvNowActive();
            if (ufv == null)
                return;

            if (UfvTransitStateEnum.S40_YARD.equals(ufv.getUfvTransitState()) )
                return; // the bare chassis unit or the container unit with this chassis is already in the yard

            // retire the unit if it is a bare chassis unit by itself
            if (EqUnitRoleEnum.PRIMARY.equals(unitEq.getUeEqRole())) {
                unit.makeRetired();
                HibernateApi.getInstance().flush();
                return;
            } else {
                // otherewise swipe with OWN chassis
                unit.swipeChsByOwnersChs();
                HibernateApi.getInstance().flush();
            }
        }

        // create unit for the Chassis in the Yard
        UnitFacilityVisit newUfv = unitMgr.createYardBornUnit(ContextHelper.getThreadFacility(), ch, null, "groovy");
        newUfv.updateArrivePosition(LocPosition.createLocPosition(dao.getTv().getCvdCv(), null, null));
        newUfv.updateObCv(dao.getTv().getCvdCv());

        Unit newUnit = newUfv.getUfvUnit();
        HibernateApi.getInstance().flush();
    }
}