import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.InventoryField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent

/**
 * Created by psethuraman on 10/4/2016.
 */
class MatGvyHideAdvisedContainer extends AbstractGeneralNoticeCodeExtension {

    /**
     * execute
     * @param inEvent
     */
    public void execute(GroovyEvent inEvent) {

        GroovyApi api = new GroovyApi();
        Event thisEvent = inEvent.getEvent();
        if (thisEvent == null) {
            return;
        }
        this.log("Start Event ---:" + thisEvent.getEventTypeId());
        if (!"ANK".equals(ContextHelper.getThreadFacility().getFcyRoutingPoint().getPointId())) {
            return
        }

        Unit unit = (Unit) inEvent.getEntity();

        if (unit == null) {
            return;
        }

         this.log(" on Unit :" + unit.getUnitId() + " ---:")

        if (!(UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory())
                || UnitCategoryEnum.STORAGE.equals(unit.getUnitCategory()))) {
            return
        }
        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive();
        if (ufv == null) {
            return
        }

        if (!UfvTransitStateEnum.S20_INBOUND.equals(ufv.getUfvTransitState())) {
            return
        }

        try {
            this.log("Updating visibility for : "+unit.getUnitId());
            ufv.setFieldValue(InventoryField.UFV_VISIBLE_IN_SPARCS, Boolean.FALSE);
            HibernateApi.getInstance().flush();
            this.log("Updated visibility for : "+unit.getUnitId());
        } catch (Exception e) {
            this.registerError(e.getMessage());
        }
        this.log("END Event ---:" + thisEvent.getEventTypeId() + " on Unit :" + unit.getUnitId() + " ---:")
    }

}


