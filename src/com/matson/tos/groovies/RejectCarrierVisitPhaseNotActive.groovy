import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.LineOperator
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitReroutePoster
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.apex.business.model.GroovyInjectionBase;


public class RejectCarrierVisitPhaseNotActive extends GroovyInjectionBase  {

    public static String BEAN_ID = "rejectCarrierVisitPhaseNotActive"
    // {0} vessel visit id
    public static PropertyKey VESSEL_VISIT_NOT_ACTIVE = PropertyKeyFactory.valueOf("gate.vessel_visit_not_active")
    // {0} service id
    public static PropertyKey NEXT_VESSEL_VISIT_NOT_FOUND = PropertyKeyFactory.valueOf("gate.next_vessel_visit_not_found")




    public void execute(TransactionAndVisitHolder dao, api) {
        TruckTransaction tran = dao.tran
        Unit unit = tran.tranUnit

        CarrierVisit obcv = getOutboundCarrierVisit(unit)
        if (!obcv) {
            api.log("RejectCarrierVisitPhaseNotActive: No outbound carrier visit for unit ${unit.unitId}.")
            return
        }

        CarrierVisitPhaseEnum phase = obcv.cvVisitPhase
        if (ACTIVE_VESSEL_VISIT_PHASES.contains(phase))
            return

        // Vessel visit phase not active, check the roll late container (auto-roll) flag.
        // If the flag is set, then in the service find the next vessel visit whose phase is active.
        // If next vessel visit can be found, reroute the unit to the next vessel visit.
        // If no next vessel visit can be found, display an error message and prevent the container to go through the gate.

        LineOperator lineOp = tran.tranLine
        if (lineOp.getLineopRollLateCtr()) {
            CarrierVisit nextCv = findNextCarrierVisit(obcv)
            if (nextCv) {
                UnitFacilityVisit ufv = unit.getUfvForFacilityNewest(ContextHelper.getThreadFacility())

                FieldChanges fcs = new FieldChanges()
                fcs.setFieldChange(InventoryField.UFV_INTENDED_OB_CV, nextCv.getPrimaryKey())

                UnitReroutePoster unitReroutePoster = (UnitReroutePoster) Roastery.getBean(UnitReroutePoster.BEAN_ID);
                unitReroutePoster.updateRouting([ufv.getPrimaryKey()] as Serializable[], fcs)
            } else {
                def srvcId = obcv.cvCvd?.cvdService?.srvcId
                RoadBizUtil.appendMessage(MessageLevel.SEVERE, NEXT_VESSEL_VISIT_NOT_FOUND, srvcId)
            }
        } else {
            def cvId = obcv.cvId
            // Roll late container flag not set, display an error message.
            RoadBizUtil.appendMessage(MessageLevel.SEVERE, VESSEL_VISIT_NOT_ACTIVE, cvId)
        }
    }

    def CarrierVisit getOutboundCarrierVisit(Unit unit) {
        UnitFacilityVisit ufv = unit.getUfvForFacilityNewest(ContextHelper.getThreadFacility());

        ufv ? (ufv.ufvActualObCv ? ufv.ufvActualObCv : ufv.ufvIntendedObCv) : null
    }

    def CarrierVisit findNextCarrierVisit(CarrierVisit currentCarrierVisit) {
        def cvd = currentCarrierVisit.cvCvd
        def currentVvd = (VesselVisitDetails) HibernateApi.getInstance().downcast(cvd, VesselVisitDetails.class)
        def nextVvds = currentVvd.findNextVisitsForService()
        // Find the next active vessel visit for the service.
        def nextVvd = nextVvds.find {vvd -> ACTIVE_VESSEL_VISIT_PHASES.contains(vvd?.cvdCv?.cvVisitPhase) }
        nextVvd.cvdCv
    }

    def ACTIVE_VESSEL_VISIT_PHASES = [
            CarrierVisitPhaseEnum.CREATED,
            CarrierVisitPhaseEnum.INBOUND,
            CarrierVisitPhaseEnum.ARRIVED,
            CarrierVisitPhaseEnum.WORKING
    ]







    // Added by SKB, Version to invoke from LNK

    public void executeForLnk(Unit unit, LineOperator lineOp) {
        try {
            CarrierVisit obcv = getOutboundCarrierVisit(unit)
            if (!obcv) {
                api.log("RejectCarrierVisitPhaseNotActive: No outbound carrier visit for unit ${unit.unitId}.")
                return
            }

            CarrierVisitPhaseEnum phase = obcv.cvVisitPhase
            if (ACTIVE_VESSEL_VISIT_PHASES.contains(phase))
                return

            // Vessel visit phase not active, check the roll late container (auto-roll) flag.
            // If the flag is set, then in the service find the next vessel visit whose phase is active.
            // If next vessel visit can be found, reroute the unit to the next vessel visit.
            // If no next vessel visit can be found, display an error message and prevent the container to go through the gate.

            if (lineOp.getLineopRollLateCtr()) {
                CarrierVisit nextCv = findNextCarrierVisit(obcv)
                if (nextCv) {
                    UnitFacilityVisit ufv = unit.getUfvForFacilityNewest(ContextHelper.getThreadFacility())

                    FieldChanges fcs = new FieldChanges()
                    fcs.setFieldChange(InventoryField.UFV_INTENDED_OB_CV, nextCv.getPrimaryKey())

                    UnitReroutePoster unitReroutePoster = (UnitReroutePoster) Roastery.getBean(UnitReroutePoster.BEAN_ID);
                    unitReroutePoster.updateRouting([ufv.getPrimaryKey()] as Serializable[], fcs)
                } else {
                    def srvcId = obcv.cvCvd?.cvdService?.srvcId
                    RoadBizUtil.appendMessage(MessageLevel.SEVERE, NEXT_VESSEL_VISIT_NOT_FOUND, srvcId)
                }
            } else {
                def cvId = obcv.cvId
                // Roll late container flag not set, display an error message.
                RoadBizUtil.appendMessage(MessageLevel.SEVERE, VESSEL_VISIT_NOT_ACTIVE, cvId)
            }
        } catch (Exception e) {
            log("Could not update vv for "+unit.unitId);
        }
    }
}
