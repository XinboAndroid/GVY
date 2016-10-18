import com.navis.argo.business.reference.EquipGrade
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.road.business.model.RoadInspection
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent

/**
 * Created by psethuraman on 5/2/2016.
 */
class MATGvyUpdateEquipGradeForImportUnits extends AbstractGeneralNoticeCodeExtension{
    public void execute(GroovyEvent inEvent) {
        this.log("Execution Started MATGvyUpdateEquipGradeForImportUnits");
        Event ThisEvent = inEvent.getEvent();
        if (ThisEvent == null)
            return;
        /* Get the unit and the Booking */
        Unit thisUnit = (Unit) inEvent.getEntity();
        String unitId = thisUnit.getUnitId();
        EquipGrade grade = EquipGrade.findEquipGrade("XX");
        if (grade != null) {
            UnitEquipment unitEq = thisUnit.getUnitPrimaryUe();
            if (unitEq != null) {
                EquipmentState eqState = unitEq.getUeEquipmentState();
                if (eqState != null) {
                    this.log("Setting eqGrade XX for Unit : "+unitId);
                    eqState.setEqsGradeID(grade);
                    unitEq.setUeEquipmentState(eqState);
                    if (UnitVisitStateEnum.DEPARTED.equals(thisUnit.getUnitVisitState())
                            || UnitVisitStateEnum.RETIRED.equals(thisUnit.getUnitVisitState())) {
                        RoadInspection inspection = RoadInspection.findRecentInspection(unitId, new Long("129600"));
                        if (inspection != null) {
                            this.log("Setting eqGrade XX in Delivered Inspection for Unit : "+unitId);
                            inspection.setInspEqGrade(grade);
                        }
                    }
                }
            }
        }
        this.log("Execution Ended MATGvyUpdateEquipGradeForImportUnits");
    }
}
