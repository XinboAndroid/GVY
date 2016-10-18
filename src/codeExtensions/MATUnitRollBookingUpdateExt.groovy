package codeExtensions

import com.navis.argo.business.api.GroovyApi
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.BizRequest
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.CrudOperation
import com.navis.framework.portal.FieldChange
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.InventoryFacade
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.inventory.business.units.UnitEquipment


/**
 * Created by VNatesan on 8/1/2016.
 */
class MATUnitRollBookingUpdateExt extends AbstractGeneralNoticeCodeExtension{


    /**
     * execute
     * @param inEvent
     */
    public void execute(GroovyEvent inEvent) {

        GroovyApi api = new GroovyApi();
        Event  thisEvent = inEvent.getEvent();

        if (thisEvent == null) {
            return;
        }

        Unit unit = (Unit) inEvent.getEntity();

        if (unit == null) {
            return;
        }
        this.log("Start Event ---:"+thisEvent.getEventTypeId()+ " on Unit :"+unit.getUnitId()+" ---:")
        /**
         Removing duplicate code ,this is exist in interceptors
         def bookingNbr;
         UnitEquipment  ue = unit.getUnitPrimaryUe();
         if (ue.getUeDepartureOrderItem() != null) {
         bookingNbr = ue.getUeDepartureOrderItem().getEqboiOrder().getEqboNbr();

         }
         this.log("Booking Nbr for Event  +++987+++---:"+thisEvent.getEventTypeId()+ " Booking Nbr :"+bookingNbr)
         //unit.getUnitGoods().setGdsBlNbr(bookingNbr);
         **/
        def doer = thisEvent.getEvntAppliedBy()
        boolean isAlwaysSendIGT = false;



        try {
            if (unit != null) {
                UnitEquipment unitEquipment = unit.getUnitPrimaryUe();
                EquipmentState ueEquipmentState = unitEquipment.getUeEquipmentState()
                def equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : ''
                this.log("ueEquipmentState :equipFlex01(getEqsFlexString01) Value---:"+equipFlex01)
                def gvyEquiObj = api.getGroovyClassInstance("GvyCmisEquiDetail");
                //gvyEquiObj.setEqCntrSvr(inEvent);
                this.log("ueEquipmentState :equipFlex01(getEqsFlexString01) Value1---:");
                this.log(equipFlex01)
                if (equipFlex01 != null) {
                    ueEquipmentState = unitEquipment.getUeEquipmentState();
                    def newEquipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : '';
                    this.log("ueEquipmentState :newEquipFlex01(getEqsFlexString01) Value-11--:");
                    this.log(newEquipFlex01);
                    if (newEquipFlex01!= null && "MAT".equalsIgnoreCase(newEquipFlex01) && !"MAT".equalsIgnoreCase(equipFlex01)) {
                        isAlwaysSendIGT = true;
                        this.log("isAlwaysSendIGT true")
                    }
                }

            }
        } catch(Exception e) {
            this.log("exception thrown : "+e.getMessage());
        }
        if(!doer.contains('FRUCTOSE_MTY')){
            this.log(" ! FRUCTOSE_MTY")
            def gvyPropUpdtObj = api.getGroovyClassInstance("GvyCmisEventUnitPropertyUpdate")
            //gvyPropUpdtObj.processUnitPropertyUpdate(inEvent,api,  isAlwaysSendIGT)
        }
    }
}
