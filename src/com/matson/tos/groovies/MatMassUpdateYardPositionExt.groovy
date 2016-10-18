import com.navis.argo.ContextHelper;
import com.navis.argo.business.api.GroovyApi
import com.navis.external.services.AbstractGeneralNoticeCodeExtension;
import com.navis.framework.business.Roastery
import com.navis.framework.portal.BizRequest
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.CrudOperation
import com.navis.framework.portal.FieldChange
import com.navis.framework.portal.FieldChanges;
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.InventoryFacade;
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.UnitFacilityVisit;

import com.navis.security.business.user.BaseUser
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent;
import org.apache.log4j.Logger;

public class MatMassUpdateYardPositionExt extends AbstractGeneralNoticeCodeExtension {
    public void execute(GroovyEvent inEvent) {
        GroovyApi api = new GroovyApi();
        Event thisEvent = inEvent.getEvent();
        MessageCollector collector = getMessageCollector();

        if (thisEvent == null)
            return;

        Unit inUnit = (Unit) inEvent.getEntity();

        if (inUnit == null)
            return;

        this.log("UPDATE POSITION executed for "+inUnit.getUnitId());
        String inSlot = thisEvent.getEvntNote();
        this.log("Updating Position for : "+inUnit.getUnitId() +" with postion <<"+thisEvent.getEvntNote()+">>");
        if (inUnit != null && inSlot != null) {
            UnitFacilityVisit ufv = inUnit.getUfvForFacilityLiveOnly(ContextHelper.getThreadFacility());
            if (ufv!= null ) {
                UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
                try {
                    BizRequest request = new BizRequest(getUserContext());
                    Serializable[] ufvGkeys = new Serializable[1];
                    ufvGkeys[0] = ufv.getUfvGkey();
                    FieldChanges fieldChanges = new FieldChanges();

                    FieldChange change = new FieldChange(InventoryBizMetafield.UNIT_DIGITS, inUnit.getUnitId());
                    fieldChanges.setFieldChange(change);

                    change = new FieldChange(InventoryBizMetafield.UNIT_YARD_SLOT, thisEvent.getEvntNote());
                    fieldChanges.setFieldChange(change);
                    CrudOperation crud = new CrudOperation(null, CrudOperation.TASK_UPDATE, InventoryEntity.UNIT_FACILITY_VISIT, fieldChanges, ufvGkeys);
                    request.addCrudOperation(crud);
                    BizResponse response = new BizResponse();
                    INVENTORY_FACADE.recordYardMove(request, response);
                    collector.getMessages().addAll(response.getMessages());
                    this.log("Position updated for "+ inUnit.getUnitId());

                    //unitManager.recordUnitYardMove(ufv, inSlot, (String) null);
                } catch (BizViolation bizViolation) {
                    this.log("Cannot record yard move "+bizViolation.toString());
                    String userId = ContextHelper.getThreadUserId();
                    String emailTo = "1aktosdevteam@matson.com";
                    if (userId!= null) {
                        BaseUser baseUser = BaseUser.findBaseUser(userId);
                        if (baseUser!= null && baseUser.getBuserEMail()!=null) {
                            emailTo = baseUser.getBuserEMail();
                        }
                    }
                    api.sendEmail(emailTo, emailTo,"Test Update Position Failed", " Update of position failed for unit "+ufv.getUfvUnit().getUnitId() +" with error"+bizViolation.toString());
                }
            }
        }
    }
    private static InventoryFacade INVENTORY_FACADE = (InventoryFacade) Roastery.getBean(InventoryFacade.BEAN_ID);

}
