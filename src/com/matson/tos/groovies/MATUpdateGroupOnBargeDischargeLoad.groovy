import com.navis.argo.ContextHelper;
import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.argo.business.reference.Group;
import com.navis.external.services.AbstractGeneralNoticeCodeExtension;
import com.navis.framework.persistence.HibernateApi;
import com.navis.inventory.business.units.Routing;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.services.business.event.Event;
import com.navis.services.business.event.GroovyEvent;
import com.navis.vessel.business.schedule.VesselVisitDetails;

public class MATUpdateGroupOnBargeDischargeLoad extends AbstractGeneralNoticeCodeExtension

{
    public void execute(GroovyEvent inEvent){
        this.log("Execution Started MATUpdateGroupOnBargeDischargeLoad");

        /* get the event */

        Event thisEvent = inEvent.getEvent();
        GroovyApi groovyApi = new GroovyApi();
        if (thisEvent == null)
            return;

        Unit unit = (Unit) inEvent.getEntity();

        if (unit == null)
            return;

        UnitFacilityVisit ufv = unit.getUfvForFacilityLiveOnly(ContextHelper.getThreadFacility());
        if (ufv == null){
            return;
        }
        Routing routing = unit.getUnitRouting();
        CarrierVisit cv = null;
        if ("UNIT_DISCH".equalsIgnoreCase(thisEvent.getEventTypeId())) {
            cv = ufv.getInboundCarrierVisit();
        } else if ("UNIT_LOAD".equalsIgnoreCase(thisEvent.getEventTypeId())){
            cv = ufv.getUfvActualObCv();
        }

        // look only for the Inbound containers on a Vessel
        if (cv == null || !LocTypeEnum.VESSEL.equals(cv.getCvCarrierMode())){
            return;
        }

        // only look for a barge
        VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(cv);
        boolean isUpdated = false;
        if (vvd == null || !vvd.isBarge() ||!cv.getCvId().startsWith("ILB")){
            if (routing!=null && routing.getRtgGroup() != null && "ILB".equalsIgnoreCase(routing.getRtgGroup().getGrpId())) {
                routing.setRtgGroup(null);
                isUpdated = true;
            }
        } else if (cv.getCvId().startsWith("ILB")) {
            if (routing!=null) {
                Group group = Group.findOrCreateGroup("ILB");
                routing.setRtgGroup(group);
                isUpdated = true;
            }
        }
        if (isUpdated) {
            unit.setUnitRouting(routing);
            HibernateApi.getInstance().saveOrUpdate(unit);
        }
    }
}
