package codeExtensions

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.reference.RoutingPoint
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent

/**
 * Created by VNatesan on 8/23/2016.
 */
class MatUpdateUnitPOL extends AbstractGeneralNoticeCodeExtension{

    public void execute(GroovyEvent inEvent) {

        GroovyApi api = new GroovyApi();
        String POL=null;
        Event thisEvent = inEvent.getEvent();
        if (thisEvent == null) {
            return;
        }

        Unit unit = (Unit) inEvent.getEntity();

        if (unit == null) {
            return;
        }
        this.log("MatUpdateUnitPOL Event POL"+thisEvent.getEventFacilityId());
        RoutingPoint routingPoint= unit.getUnitRouting().getRtgPOL();
        this.log("RoutingPoint --- MatUpdateUnitPOL:"+routingPoint.toString())
        if(routingPoint==null){
            this.log("RoutingPoint ---In  MatUpdateUnitPOL is null so updating POL")
            POL =thisEvent.getEventFacilityId();
            if(POL==null) {
                POL = ContextHelper.getThreadFacility().getFcyId();
            }
            unit.getUnitRouting().setRtgPOL(com.navis.argo.business.reference.RoutingPoint.findRoutingPoint(POL));
            HibernateApi.getInstance().flush();
        }

    }
}
