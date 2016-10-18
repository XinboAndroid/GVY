import com.navis.argo.ContextHelper
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
public class MATUpdateTempSettingOnUnitRoll extends AbstractGeneralNoticeCodeExtension {
    public void execute(GroovyEvent inEvent) {
        this.log("Execution Started MATUpdateTempSettingOnUnitRoll");
        /* get the event */
        Event thisEvent = inEvent.getEvent();
        if (thisEvent == null)
            return;
        Unit unit = (Unit) inEvent.getEntity();
        if (unit == null)
            return;
        UnitFacilityVisit ufv = unit.getUfvForFacilityLiveOnly(ContextHelper.getThreadFacility());
        if (ufv == null) {
            return;
        }
        if ("UNIT_ROLL".equalsIgnoreCase(thisEvent.getEventTypeId())) {
            EqBaseOrderItem eqboi = unit.getUnitPrimaryUe().getUeDepartureOrderItem();
            if (eqboi != null) {
                // got the booking item, now get the remarks from it and copy to ufvFlexString07
                String strAux = eqboi.getEqoiRemarks();
                if (strAux != null) {
                    ufv.setUfvFlexString07(strAux);
                } else {
                    ufv.setUfvFlexString07("");
                }
            } else {
                ufv.setUfvFlexString07("");
            }
            HibernateApi.getInstance().saveOrUpdate(ufv);
        }
    }
}