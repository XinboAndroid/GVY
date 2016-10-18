/**
 * Created by psethuraman on 4/26/2016.
 */


import com.navis.argo.ContextHelper
import com.navis.argo.business.model.GeneralReference
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.util.BizViolation
import com.navis.inventory.InventoryPropertyKeys
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.util.RoadBizUtil
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.commons.io.IOUtils


public class MATHazmatHazardRefresh extends AbstractGeneralNoticeCodeExtension

{
    public void execute(GroovyEvent inEvent) {
        this.log("Execution Started MATHazmatHazardRefresh");
        /* get the event */
        Event ThisEvent = inEvent.getEvent();
        if (ThisEvent == null)
            return;
        /* Get the unit and the Booking */
        Unit thisUnit = (Unit) inEvent.getEntity();
        String unitId = thisUnit.getUnitId();
        if (thisUnit != null) {
            String currentFacility = ContextHelper.getThreadFacility().getFcyId();
            UnitFacilityVisit thisUfv = thisUnit.getUnitActiveUfvNowActive();
            if (thisUfv != null) {
                String ufvFacility = thisUfv.getUfvFacility().getFcyId();
                if (currentFacility != null && ufvFacility != null
                        && currentFacility.equalsIgnoreCase(ufvFacility)) {
                    if (UnitVisitStateEnum.ACTIVE.equals(thisUfv.getUfvVisitState())) {
                        String transitState = thisUfv.getUfvTransitState().getKey().substring(4);
                        callTDPHazRefreshService(unitId, ufvFacility, transitState);
                    } else {
                        BizViolation.create(InventoryPropertyKeys.UNIT_IS_NOT_IN_THE_YARD, unitId);
                    }
                } else {
                    BizViolation.create(InventoryPropertyKeys.UNITS_NOT_ACTIVE_IN_FACILITY, unitId);
                }
            } else {
                BizViolation.create(InventoryPropertyKeys.UNITS__NOT_ACTIVE, unitId);
            }
        }
    }

    private boolean callTDPHazRefreshService(String inUnitNbr, String facility, String transitState) throws Exception {
        this.log("Start of WS call");
        InputStream stream = null;
        try {
            //@todo configure the WS URL in General Reference
            GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "RESTHAZTDP", "URL");
            URL url = new URL(genRef.getRefValue1() + "/hazmatinterface/refreshunithaz/?equipmentId=" + inUnitNbr + "&facilityId="+ facility + "&transitState=" + transitState);

            this.log("TDP Haz Service URL : " + url.toString());
            URLConnection connection = url.openConnection();
            this.log("Connection : "+connection);
            stream = connection != null ? connection.getInputStream() : null;
            this.log("Stream : "+stream);
            if (stream != null) {
                String StringFromInputStream = IOUtils.toString(stream, "UTF-8");
                this.log("String format of stream : "+StringFromInputStream);
            } else {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "No hazard record found from webservice for Unit:" + inUnitNbr + " in facility :" + facility));
            }
        } catch (Exception e) {
            this.log(e.getMessage() + e.getCause());
            e.printStackTrace();
            RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__SERVICE_NOT_AVAILABLE, null,
                    " Haz Refresh Service call failed for :" + inUnitNbr + " in facility :" + facility));
            return false;
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        this.log("End of WS call");
        return true;
    }
}
