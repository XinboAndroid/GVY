package com.matson.tos.groovies

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.IServiceEventFieldChange
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.ValueObject
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.orders.business.eqorders.Booking
import com.navis.services.business.event.Event
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Logger

/**
 * Created by kramachandran on 11/14/2016.
 */
class GvyCmisSendOGSCLS {
    public static final String CLS_EVENT_TYPE = 'UNIT_LOAD';
    private static final Logger LOGGER = Logger.getLogger(GvyCmisSendOGSCLS.class);


    /**
     * This method triggers CLS Message for UNIT_LOAD to Client Vessels, here MAE Vessels
     * @param initialGvyXml Unit_deliver
     * @param event event
     * @param api gvyBaseClass
     * @return CLS xml
     */
    public Boolean isProcessCLSOGSMessage(String initialGvyXml, Object event, Object api) {
        api.log("START -- processing GvyCmisSendOGSCLS.isProcessCLSOGSMessage");
        def unit = event.getEntity()
        def gvyEventObj = event.getEvent()
        String eventType = gvyEventObj.getEventTypeId();

        def GvyCmisSrvMsgProcessor = api.getGroovyClassInstance("GvyCmisSrvMsgProcessor");
        Booking booking = null;
        VesselVisitDetails vvd = null;
        def vvdOperatorId = null;
        def bookingEqFlexString01 = null;
        Boolean inUseSuppliedCvId = Boolean.FALSE;
        def unitLineOperator = null;
        def equipmentOwner = null;
        if (unit != null) {
            UnitEquipment unitEquipment = unit.getUnitPrimaryUe();
            EquipmentState ueEquipmentState = unitEquipment.getUeEquipmentState()
            def equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : null;
            unitLineOperator = ueEquipmentState != null ? ueEquipmentState.getEqsEqOperator().getBzuId() : null;
            equipmentOwner = ueEquipmentState != null ? ueEquipmentState.getEqsEqOwner().getBzuId() : null;
            bookingEqFlexString01 = equipFlex01;
        }

        def freightkind = unit.getFieldValue("unitFreightKind");
        def category = unit.getFieldValue("unitCategory");
        category = category != null ? category.getKey() : null;
        Boolean isCLS = Boolean.FALSE;
        /**
         * Only for out by Client Vessels, Bookings are Available, so pull all the information from Event
         *
         */
        api.log("eventType " + eventType);
        api.log("category  " + category);
        if (eventType != null && category != null && CLS_EVENT_TYPE.equalsIgnoreCase(eventType)
                && ('EXPRT'.equals(category))) {
            api.log("Inside if loop ");
            Boolean isProcessFurther = Boolean.FALSE;
            try {
                def outboundCarrierOperator = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvOperator.bzuId");
                api.log("outboundCarrierOperator    "+outboundCarrierOperator);
                def cvdGkey = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvdGkey");
                api.log("cvdGkey    "+cvdGkey);
                CarrierVisit cv = CarrierVisit.loadByGkey(cvdGkey);
                api.log("cv "+cv);
                //vvd = unit.getOutboundCv().getCvCvd();
                //api.log("VVD   " + vvd);
                vvdOperatorId = outboundCarrierOperator;
                //vvdOperatorId = vvd != null ? vvd.getCarrierOperator().getBzuId() : null;
                //api.log("VVD Operator ID   " + vvdOperatorId);
                def eqboNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");//unit.getUnitPrimaryUe().getUeDepartureOrderItem().getEqboiOrder().getEqboNbr();
                api.log("eqboNbr   " + eqboNbr);
                ScopedBizUnit sb = ScopedBizUnit.findScopedBizUnit(outboundCarrierOperator,BizRoleEnum.LINEOP);
                booking = Booking.findBooking(eqboNbr, sb, cv);
                api.log("Booking   " + booking);
                if (booking == null) {
                    try {
                        booking = Booking.findBookingWithoutLine(eqboNbr, cv);
                    } catch (Exception e) {
                        api.log("Couldnt find the booking with eqboNbr and Visit"+e.getMessage());
                    }
                }
                if (booking == null) {
                    try {
                        List<Booking> bookingList = Booking.findBookingsByNbr(eqboNbr);
                        booking = bookingList.get(0);
                    } catch (Exception e) {
                        api.log("Couldnt find the booking only with eqboNbr"+e.getMessage());
                    }
                }

                //booking = unit.GvyCmisSrvMsgProcessor.findBookingFromEventChanges(event.getEvent(), unit);
                api.log("Booking from Unit Object : " + booking);
                if (booking != null) {
                    bookingEqFlexString01 = booking.getEqoVesselVisit().getCarrierOperator().getBzuId() != null ? ('MAT'.equals(booking.getEqoVesselVisit().getCarrierOperator().getBzuId()) ? 'MAT' : 'CLI') : 'CLI';
                    api.log("bookingEqFlexString01 "+bookingEqFlexString01);
                    vvd = VesselVisitDetails.resolveVvdFromCv(booking.getEqoVesselVisit());
                    if (vvd != null) {
                        vvdOperatorId = vvd.getCarrierOperator().getBzuId();
                        isProcessFurther = Boolean.TRUE;
                        api.log("isProcessFurther  "+isProcessFurther);
                    }
                }
            } catch (Exception e) {
                api.log("Error in Processing Data for isProcessCLSOGSMessage    "+e.getMessage())
            }

            if (isProcessFurther) {
                api.log("bookingEqFlexString01  "+bookingEqFlexString01);
                api.log("vvdOperatorId  "+vvdOperatorId);
                api.log("equipmentOwner "+equipmentOwner);
                api.log("unitLineOperator   "+unitLineOperator);
                if (bookingEqFlexString01 != null && 'CLI'.equals(bookingEqFlexString01)
                        && vvdOperatorId != null && !'MAT'.equals(vvdOperatorId)) {
                    if ('MAE'.equals(vvdOperatorId)
                            && equipmentOwner != null && ('MAE'.equals(equipmentOwner) || ('MAEU'.equals(equipmentOwner)))
                            && unitLineOperator != null && 'MAE'.equals(unitLineOperator)) {
                        def facilityId = unit.getFieldValue("unitActiveUfv.ufvFacility.fcyId")
                        facilityId = facilityId != null ? facilityId : ''
                        if (facilityId.equalsIgnoreCase('ANK') || facilityId.equalsIgnoreCase('DUT') || facilityId.equalsIgnoreCase('KDK')) {
                            isCLS = Boolean.TRUE;
                        }
                    }
                }
            }
        }
        //isCLS = Boolean.TRUE;
        if (isCLS) {
            api.sendXml(getOGSMessage(initialGvyXml, event, api));
            Thread.sleep(30000L);
            api.sendXml(getCLSMessage(initialGvyXml, event, api));
        }
        return isCLS;
    }
    /**
     * <p>Send CLS Message</p>
     * @param initialGvyXml
     * @param event
     * @param api
     * @return
     */
    public String getCLSMessage(String xmlGvyString, Object event, Object api) {
        def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "msgType=", "RETURN_TO_CUSTOMER_ASSIGN");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "action=", "CLS");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "lastAction=", "CLS");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "srv=", "MAT");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "A");
        api.log("XML Message 	" + xmlGvyString);
        return xmlGvyString;
    }
    /**
     * <p>Send 'OGS' Message</p>
     * @param initialGvyXml
     * @param event
     * @param api
     * @return
     */
    public String getOGSMessage(String xmlGvyString, Object event, Object api) {
        def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "msgType=", "RETURN_TO_CUSTOMER_ASSIGN");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "action=", "OGS");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "lastAction=", "OGS");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "srv=", "MAT");
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "A");
        api.log("XML Message 	" + xmlGvyString);
        return xmlGvyString;
    }

    public LocTypeEnum getActualObCvMode(Event event, Unit ThisUnit) {
        Booking booking = null;
        Iterator fcIt = event.getFieldChanges().iterator();
        String eqboNbr = null;
        String eqboObActualVisit = unit.getFieldValue("unitActiveUfv.ufvActualObCv");
        String eqboObIntendedVisit = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv");
        String actualObVisitMode = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCarrierMode");
        String intentedObVisitMode = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCarrierMode");
        while (fcIt.hasNext()) {
            IServiceEventFieldChange fc = (IServiceEventFieldChange) fcIt.next();
            ValueObject fcVao = new ValueObject("IServiceEventFieldChange");
            MetafieldId metafieldId = MetafieldIdFactory.valueOf(fc.getMetafieldId());
            /*fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_METAFIELD_ID, metafieldId);
            fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_PREV_VALUE, ThisEvent.getFieldChangeValue(metafieldId, fc.getPrevVal()));
            fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_NEW_VALUE, ThisEvent.getFieldChangeValue(metafieldId, fc.getNewVal()));*/
            this.log("Field : " + metafieldId.toString());
            this.log("Prev Value : " + event.getFieldChangeValue(metafieldId, fc.getPrevVal()).toString());
            this.log("New Value  : " + event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString());

            /*if (InventoryField.EQBO_NBR.equals(metafieldId)) {
                eqboNbr = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }*/
            if (InventoryField.UFV_ACTUAL_OB_CV.equals(metafieldId)) {
                eqboObActualVisit = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }
            if (InventoryField.UFV_INTENDED_OB_CV.equals(metafieldId)) {
                eqboObIntendedVisit = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }
        }
        if (actualObVisitMode == null) {
            actualObVisitMode = intentedObVisitMode;
        }

        if (eqboObActualVisit == null) {
            eqboObActualVisit = eqboObIntendedVisit;
        }
        if (actualObVisitMode == null) {
            return LocTypeEnum.getEnum(actualObVisitMode);
        }

        if (eqboObActualVisit != null) {
            // By Truck
            CarrierVisit cv = null;
            Boolean isTryByBarge = Boolean.FALSE;
            Boolean isByTruck = Boolean.FALSE;
            Boolean isByBarge = Boolean.FALSE;
            try {
                cv = CarrierVisit.findCarrierVisit(ContextHelper.getThreadFacility(), LocTypeEnum.TRUCK, eqboObActualVisit);
                if (cv == null) {
                    //try Barge
                    isTryByBarge = Boolean.TRUE;
                } else
                    isByTruck = Boolean.TRUE;
            } catch (Exception ex) {

            }
            // if try brage flag
            if (isTryByBarge) {
                try {
                    cv = CarrierVisit.findCarrierVisit(ContextHelper.getThreadFacility(), LocTypeEnum.VESSEL, eqboObActualVisit);
                    if (cv != null) {
                        isByBarge = Boolean.TRUE;
                    }
                } catch (Exception ex) {

                }
            }

            if (isByBarge) {
                return LocTypeEnum.VESSEL;
            } else if (isByTruck) {
                return LocTypeEnum.TRUCK;
            } else
                return LocTypeEnum.UNKNOWN;
        }
        /*if (eqboNbr != null && eqboObActualVisit != null) {
            this.log("eqboNBR : " + eqboNbr + " // eqboVisit : " + eqboObActualVisit);

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
        return booking;*/
    }
}
