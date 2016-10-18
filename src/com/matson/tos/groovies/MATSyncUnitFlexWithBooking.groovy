/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/


import com.navis.argo.ArgoBizMetafield
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.IServiceEventFieldChange
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.util.ValueObject
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.orders.OrdersField
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * This groovy applies the booking stow block to the unit's priority stow (UnitFlexSting08) when the unit is rolled to a new booking
 * This is triggered with a UNIT_ROLL event
 *
 * Author: Peter Seiler
 * Date: 08/05/15
 * JIRA: ARGO-76865
 * SFDC: 142550
 *
 */

public class MATSyncUnitFlexWithBooking extends AbstractGeneralNoticeCodeExtension

{
    public void execute(GroovyEvent inEvent)

    {
        this.log("Execution Started MATSyncUnitFlexWithBooking");

        /* get the event */

        Event ThisEvent = inEvent.getEvent();

        if (ThisEvent == null)
            return;

        /* Get the unit and the Booking */

        Unit ThisUnit = (Unit) inEvent.getEntity();

        this.log("Unit is " + ThisUnit)

        EqBaseOrder ThisBaseOrder = ThisUnit.getDepartureOrder();

        this.log("Depart order " + ThisBaseOrder)

        EquipmentOrder ThisEqOrd = EquipmentOrder.resolveEqoFromEqbo(ThisBaseOrder);

        this.log("Equipmnet Order " + ThisEqOrd)
        Booking ThisBooking = findBookingFromEventChanges(ThisEvent, ThisUnit);

        this.log("Booking from new value is " + ThisBooking)
        if (ThisBooking == null) {
            ThisBooking = Booking.resolveBkgFromEqo(ThisEqOrd);
        }

        this.log("Booking is " + ThisBooking);

        /* set the unit's priority stow value */

        ThisUnit.setUnitFlexString08(ThisBooking.getEqoStowBlock());


        //INCORPORATES THE CODE FROM GvyCmisEquiDetail GROOVY, AS CODE IS NOT WORKING THERE
        this.log("Updating srv company : 989898");
        update(inEvent, ThisBooking);
    }

    public Booking findBookingFromEventChanges(Event event, Unit ThisUnit) {
        Booking  booking = null;
        Iterator fcIt = event.getFieldChanges().iterator();
        String eqboNbr = null;
        String eqboVisit = null;
        String eqboDclrdVisit = null;
        while(fcIt.hasNext()) {
            IServiceEventFieldChange fc = (IServiceEventFieldChange)fcIt.next();
            ValueObject fcVao = new ValueObject("IServiceEventFieldChange");
            MetafieldId metafieldId = MetafieldIdFactory.valueOf(fc.getMetafieldId());
            /*fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_METAFIELD_ID, metafieldId);
            fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_PREV_VALUE, ThisEvent.getFieldChangeValue(metafieldId, fc.getPrevVal()));
            fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_NEW_VALUE, ThisEvent.getFieldChangeValue(metafieldId, fc.getNewVal()));*/
            this.log("Field : "+metafieldId.toString());
            this.log("Prev Value : "+event.getFieldChangeValue(metafieldId, fc.getPrevVal()).toString());
            this.log("New Value  : "+event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString());

            if (InventoryField.EQBO_NBR.equals(metafieldId)) {
                eqboNbr = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }
            if (InventoryField.UFV_INTENDED_OB_CV.equals(metafieldId)) {
                eqboVisit = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }
            if (InventoryField.RTG_DECLARED_CV.equals(metafieldId)) {
                eqboDclrdVisit = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }
        }
        if (eqboVisit == null) {
            eqboVisit = eqboDclrdVisit;
        }
        if (eqboNbr != null && eqboVisit != null) {
            this.log("eqboNBR : "+eqboNbr + " // eqboVisit : "+eqboVisit);
            CarrierVisit cv = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), eqboVisit);
            booking = Booking.findBooking(eqboNbr, ThisUnit.getUnitLineOperator(), cv);
            if (booking == null) {
                try {
                    booking = Booking.findBookingWithoutLine(eqboNbr, cv);
                } catch (Exception e) {
                    this.log("Couldnt find the booking with eqboNbr and Visit")
                }
            }
            if (booking == null) {
                try {
                    booking = Booking.findBookingsByNbr(eqboNbr);
                } catch (Exception e) {
                    this.log("Couldnt find the booking only with eqboNbr")
                }
            }
        }
        return booking;
    }

    public void update(GroovyEvent inEvent, Booking thisBooking) {

        GroovyApi api = new GroovyApi();
        Event  thisEvent = inEvent.getEvent();
        this.log("inside update srv company : "+thisBooking.getEqboNbr());
        if (thisEvent == null) {
            return;
        }

        Unit unit = (Unit) inEvent.getEntity();

        if (unit == null) {
            return;
        }
        this.log("Start Event ---:"+thisEvent.getEventTypeId()+ " on Unit :"+unit.getUnitId()+" ---:")

        def doer = thisEvent.getEvntAppliedBy()
        boolean isAlwaysSendIGT = false;
        try {
            if (unit != null) {

                this.log("56565656Loaded unit from DB : "+unit.getUnitId());
                UnitEquipment unitEquipment = unit.getUnitPrimaryUe();
                EquipmentState ueEquipmentState = unitEquipment.getUeEquipmentState()
                def equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : ''
                this.log("ueEquipmentState :equipFlex01(getEqsFlexString01) Value---:"+equipFlex01)
                String serviceId = setEqCntrSvr(inEvent, thisBooking);
                this.log("ueEquipmentState :equipFlex01(getEqsFlexString01) Value1---:"+equipFlex01)
                //unitEquipment = unit.getUnitPrimaryUe();
                if (equipFlex01 != null) {
                    //ueEquipmentState = unitEquipment.getUeEquipmentState();
                    //ueEquipmentState.setEqsFlexString01(serviceId);
                    def newEquipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : '';
                    this.log("ueEquipmentState :newEquipFlex01(getEqsFlexString01) Value-11--:"+newEquipFlex01);
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
            gvyPropUpdtObj.processUnitPropertyUpdate(inEvent,api,  isAlwaysSendIGT)
        }
    }

    public String setEqCntrSvr(GroovyEvent event, Booking thisBooking) {
        String srvId = "MAT";
        LOGGER.setLevel(Level.DEBUG);
        LOGGER.info("Inside GvyCmisEquiDetail.setEqCntrSvr")
        Unit unit = event.getEntity();

        String eventId = event.getEvent().getEventTypeId()
        LOGGER.debug("Event ID  "+eventId);
        try {
            //Get Equi SRV
            UnitEquipment unitEquipment = unit.getUnitPrimaryUe()
            LOGGER.debug("Unit Equipment    "+unitEquipment);
            EquipmentState ueEquipmentState = unitEquipment.getUeEquipmentState()
            String equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : null
            LOGGER.info("getEqsFlexString01/ Eq Srv Company : "+equipFlex01);
            if (equipFlex01 != null && !(eventId.equals('UNIT_DISCH_COMPLETE') || eventId.equals('UNIT_IN_GATE') ||
                    eventId.equals('UNIT_ROLL'))) {
                return;
            }
            String unitLineOperator = unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId")
            LOGGER.info("Unit Line Operator "+unitLineOperator);
            unitLineOperator = unitLineOperator != null ? unitLineOperator : ''
            String equiSrv = "";
            String vesLineOptr = "";
            //Thread.sleep(1000);
            if (eventId.equals('UNIT_ROLL')) {
                //Ingate Bkg Line Operator
//                vesLineOptr = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdBizu.bzuId");
                vesLineOptr = thisBooking.getEqoVesselVisit().getCarrierOperator().getBzuId();
                LOGGER.debug(unit.getUnitId() + " ->Value of UNIT OB vesLineOptr from Object is "+vesLineOptr);
            }
            LOGGER.info("vesLineOptr    "+vesLineOptr+" : unitLineOperator "+unitLineOperator);
            //verify and set EqSrv Cntr
            vesLineOptr = vesLineOptr != null ? vesLineOptr : (unitLineOperator.equals('MAT') ? 'MAT' : '')
            vesLineOptr = !vesLineOptr.equals('MAT') ? 'CLI' : 'MAT'
            LOGGER.debug("Value of vesLineOptr after manipulation is    "+vesLineOptr);
            ueEquipmentState.setEqsFlexString01(vesLineOptr)
            LOGGER.info("setEqsFlexString01 / New Eq Container Service   "+vesLineOptr);
            LOGGER.info("eventId ::" + eventId + " Eq SRV :" + equipFlex01 + " vesLineOptr :" + vesLineOptr + "    unitLineOperator::" + unitLineOperator)
            srvId = vesLineOptr;
        } catch (Exception e) {
            e.printStackTrace()
        }
        return srvId;
    }
    private static final Logger LOGGER = Logger.getLogger(MATSyncUnitFlexWithBooking.class);
}