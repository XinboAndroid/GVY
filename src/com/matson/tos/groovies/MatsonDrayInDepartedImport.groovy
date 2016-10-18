/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ContextHelper
import com.navis.argo.business.api.Serviceable
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.portal.BizRequest
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.CrudOperation
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.InventoryFacade
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 bgopal 11/13/15When user record “Dray In” event on a departed unit, the unit should be resurrected and an inbound ufv should be created
 */
class MatsonDrayInDepartedImport extends AbstractGeneralNoticeCodeExtension {
    public void execute(GroovyEvent inEvent) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonDrayInDepartedImport execute Stared.");
        Event event = inEvent.getEvent();
        Serviceable serviceable = inEvent.getEntity();
        MessageCollector collector = getMessageCollector();
        try {

            //validation
            if (serviceable == null) {
                LOGGER.error("MatsonDrayInDepartedImport, couldn't execute the groovy MatsonAncMultiStopImportDelivery since the event:" + event +
                        " has null value for serviceable entity.");
                return;
            }
            if (!serviceable instanceof Unit) {
                LOGGER.error("MatsonDrayInDepartedImport, couldn't execute the groovy MatsonAncMultiStopImportDelivery since the event:" + event +
                        " is not applicable to Unit Entity.");
                return;
            }

            Unit unit = serviceable as Unit;
            GoodsBase goodsBase = unit.getUnitGoods();
            if (drayIn.equals(event.getEvntEventType().getEvnttypeId())) {
                //resurrect delivered
                UnitFacilityVisit ufv = unit.getUfvForFacilityNewest(ContextHelper.getThreadFacility());
                if (ufv != null && UfvTransitStateEnum.S70_DEPARTED.equals(ufv.getUfvTransitState())) {
                    BizRequest request = new BizRequest(getUserContext());
                    Serializable[] ufvGkeys = new Serializable[1];
                    ufvGkeys[0] = ufv.getUfvGkey();
                    CrudOperation crud = new CrudOperation(null, CrudOperation.TASK_UPDATE, InventoryEntity.UNIT_FACILITY_VISIT, null, ufvGkeys);
                    request.addCrudOperation(crud);
                    BizResponse response = new BizResponse();
                    INVENTORY_FACADE.resurrectUnit(request, response);
                    collector.getMessages().addAll(response.getMessages());
                    LOGGER.warn("Inbound import unit created for "+ unit.getUnitId());
                }
            }
        } catch (Exception e){
            // null
        }
    }
    private Logger LOGGER = Logger.getLogger(MatsonAncMultiStopImportDelivery.class);
    private static InventoryFacade INVENTORY_FACADE = (InventoryFacade) Roastery.getBean(InventoryFacade.BEAN_ID);
    private static String drayIn = "DRAY_IN";
}