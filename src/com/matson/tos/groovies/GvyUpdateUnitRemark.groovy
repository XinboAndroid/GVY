import com.navis.argo.business.api.GroovyApi
import com.navis.framework.portal.UserContext
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitFinder
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.inventory.business.units.Unit
import org.apache.log4j.Logger
import com.navis.framework.business.Roastery
import com.navis.inventory.InventoryPropertyKeys

/*
* Copyright (c) 2014 Navis LLC. All Rights Reserved.
*
*/


public class GvyUpdateUnitRemark extends GroovyApi {

    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

    public void execute(GroovyEvent event, Object api)
    {
        LOGGER.info(" GvyUpdateUnitRemark started " + timeNow);

        def eq = event.getEntity();
        LOGGER.info("eq: " + eq);

        Event gvyEventObj = event.getEvent();
        String eventType =  gvyEventObj.getEventTypeId();
        def evntNotes = gvyEventObj.getEventNote();

        LOGGER.info(" Event Type : " + eventType);
        LOGGER.info(" Event Notes: " + evntNotes);

        Unit unit;

        if(eventType == "SHOP_HOLD"){
            def gvyDataProcObj = api.getGroovyClassInstance("GvyCmisDataProcessor");
            //Getting UNIT_OBJ from EQUIP_OBJ
            unit = gvyDataProcObj.getUnitFromEquipment(eq)
        }

        if (eventType == "RD_HOLD"){
            unit = event.getEntity()
        }

        LOGGER.info("Unit: " + unit);

        if (unit == null){
            LOGGER.error(" Could not find active unit");
            LOGGER.error("GvyUpdateUnitRemark ended" + timeNow);
            return;
        }

        if (unit != null){
            String unitRemark = unit.getFieldValue(UnitField.UNIT_REMARK);
            LOGGER.info (" Unit Remarks: " + unitRemark);
            if (unitRemark == null){
                LOGGER.info(" Unit Remarks is null");
                unit.setFieldValue(UnitField.UNIT_REMARK,evntNotes);
            }
            if (unitRemark != null){
                LOGGER.info(" Unit Remarks not null");
                String strRmk = unitRemark;
                if (evntNotes != null){
                    strRmk = unitRemark + ' ' + evntNotes ;
                }
                LOGGER.info(" Concat Value" + strRmk);
                unit.setFieldValue(UnitField.UNIT_REMARK,strRmk);
            }
        }
        LOGGER.info("GvyUpdateUnitRemark ended" + timeNow);
    }
    private static final Logger LOGGER = Logger.getLogger(GvyUpdateUnitRemark.class);
}