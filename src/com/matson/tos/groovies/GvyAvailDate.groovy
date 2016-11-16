/*
**********************************************************************
* Srno    Date        Changer	         Change Description
* A1      02/03/09    Glenn Raposo       EditFlag null check
* A2      02/06/09	  Steven Bauer		 Change conditions to remove avail date
* A3      01/12/11    Glenn Raposo       Substract One day from  Method
                                         addBusinessDate, addCalendarDate
* A4      09/02/12    Glenn Raposo       Changed Code for TOS2.1 Upgrade issue
                                         Replaces AppCalendarUtil to ArgoCalendarUtil since navis team chaged code
* A5      29/02/12    Glenn Raposo       Added Method for NIS Coding Detention
* A6      03/20/12    Glenn Raposo       YB barge recal change
* A7      04/04/12    Glenn Raposo       Gopals Change to fix the Detention Date cal on Weekends
* A8      12/21/12    Lisa Crouch        Replaced the isReefer check with RfrType
* A9      07-24-13    Karthik Rajendran  Remove Line Operator check for setting Avail Date to allow Client Vessels
* A10     01-23-14    Raghu Iyer         Added setAvailDateClient to update avail date for client vessel units
**********************************************************************
*/

import com.navis.argo.business.atoms.EquipRfrTypeEnum;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.inventory.business.api.*;
import com.navis.inventory.business.units.*;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.framework.configuration.calendar.AppCalendarUtil;
import com.navis.framework.configuration.calendar.AppCalendarEventType;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.reference.RoutingPoint;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.argo.business.atoms.BizRoleEnum;

import com.navis.argo.ContextHelper;
import java.util.TimeZone;
import java.util.Calendar;

import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.ArgoRefField;
import com.navis.framework.persistence.HibernateApi;
import com.navis.inventory.InventoryField;
import java.util.List;

import com.navis.argo.business.calendar.ArgoCalendarUtil;
import com.navis.argo.business.calendar.ArgoCalendarEventType;
import com.navis.argo.business.calendar.ArgoCalendar;
import com.navis.argo.business.atoms.CalendarTypeEnum;

/** Change History
 * A1 - SKB 01/21/09 Changed manual update from V to X.
 *                  Also changed logic for updating det and last free
 * A2 - SKB 02/06/09 Changed logic for deletion.
 */
//A4 - 02/09/12 - With the TOS2.1 Upgrade navis team split the Calendar util from Framework package and Argo Package
// - causing our 1.6 code to refer to the old table (AppCalendarEvent) to new table (ArgoCalendarEvent)
public class GvyAvailDate extends GroovyInjectionBase {

/*private static AppCalendarEventType[] exemptTypes = null;

static {
	exemptTypes = new AppCalendarEventType[2];
	exemptTypes[0] = AppCalendarEventType.findOrCreateAppCalendarEventType("EXEMPT_DAY");
    exemptTypes[1] = AppCalendarEventType.findOrCreateAppCalendarEventType("GRATIS_DAY");
}*/

    private static ArgoCalendarEventType[] exemptTypes = null;

    static {
        exemptTypes = new ArgoCalendarEventType[2];
        exemptTypes[0] = ArgoCalendarEventType.findOrCreateArgoCalendarEventType("EXEMPT_DAY");
        exemptTypes[1] = ArgoCalendarEventType.findOrCreateArgoCalendarEventType("GRATIS_DAY");
    }



    public boolean setAvailDate(Object unit, Object event) {
        println(":::::::inside setAvailDate:::::");
        boolean update = false;
        update =  setAvailDateOnUnit(unit, event, null);
        return update
    }

    public boolean setAvailDateClient(Object unit, Object event) {
        println(":::::::inside setAvailDateClient:::::");
        if (event != null) {
            ContextHelper.setThreadExternalUser(event.event.evntAppliedBy);
        }

        def editFlag = unit.getUnitFlexString11();
        boolean update = false;

        def previousDest = null;
        def previousLineOperator = null;
        def previousCategory = null;
        def previousFreightKind = null;

        def editDest = false;
        def editLineOperator = false;
        def editCategory = false;
        def editFreightKind = false;

        if (event != null) {
            def pointId = event.getPreviousPropertyAsString("PODRef");
            def point = findRoutingPoint(pointId);
            if (point != null) {
                previousDest = point.pointId;
            }
            previousLineOperator = event.getPreviousPropertyAsString("LineOperator");
            previousCategory = event.getPreviousPropertyAsString("Category");
            previousFreightKind = event.getPreviousPropertyAsString("FreightKind");

            editDest = event.wasFieldChanged("PODRef");
            editLineOperator = event.wasFieldChanged("LineOperator");
            editCategory = event.wasFieldChanged("Category");
            editFreightKind = event.wasFieldChanged("FreightKind");
        }

        //log("PREV="+previousDest+","+previousLineOperator+","+previousCategory+","+previousFreightKind);



        def dest = null;
        def category = null;
        def freightKind = null;
        def commdityCode = null;
        def zone = unit.getUnitComplex().getTimeZone();
        def MAT;
        EquipRfrTypeEnum ueRfrType = null;

        try {
            dest = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
            category = unit.unitCategory.name;
            freightKind = unit.unitFreightKind.name;
            ueRfrType =  unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypRfrType");
            if (unit.goods != null && unit.goods.gdsCommodity != null) {
                commdityCode = unit.goods.gdsCommodity.cmdyId;
            }

        } catch (Exception e) {
            log("Exception =" + e.getMessage());
        }

        try {
            def bizScope = ScopedBizUnit.findScopedBizUnit("MAT", BizRoleEnum.LINEOP);
            MAT = bizScope.bzuGkey.toString();
        } catch (Exception e) {

        }

        //boolean result = (MAT == previousLineOperator);
        //String rStr = Boolean.toString(result);
        //log("Mat="+MAT+" "+editLineOperator+" "+previousLineOperator+" "+ rStr );

        def lineOper = null;
        def tcn = unit.unitFlexString12;
        println("BLNumber123456-->"+unit.getFieldValue("unitGoods.gdsBlNbr"));
        println("ueRfrType-->"+ueRfrType.toString());
        boolean isReefer = !EquipRfrTypeEnum.NON_RFR.equals(ueRfrType) ? true : false;
        println("isReefer-->"+isReefer);
        String consigneeName = unit.getFieldValue("unitGoods.gdsConsigneeAsString");
        String remarks = unit.getFieldValue("unitRemark");


        try {
            lineOper = unit.unitLineOperator.bzuId;
        } catch (Exception e) {
            // do nothing
        }

        def ufv = unit.unitActiveUfv;

        // Not active UFV, find an advised UFV.
        if (ufv == null) {
            def lookup = getGroovyClassInstance("GvyUnitLookup");
            ufv = lookup.lookupFacility(unit.primaryKey);
        }

        // Can not update without UFV
        if (ufv == null)
            return update;

        def availDate = ufv.getFieldValue("ufvFlexDate02");
        boolean availChange = false;
        boolean manualDate = false;

        //log("Prev dest = "+previousDest+" lineOper="+lineOper+" category="+category+" kind="+freightKind+"comm="+commdityCode);


        if (editFlag != null && editFlag.contains("X")) {
            manualDate = true;
        }

        if (event != null && event.wasFieldChanged("UfvFlexDate02")) {
            availChange = true;
            manualDate = true;
            /*
                 if(editFlag == null) {
                    unit.setFieldValue("unitFlexString11","X");
                 } else if(!editFlag.contains("X")) {
                    unit.setFieldValue("unitFlexString11",editFlag+"X");
                 }
           */

        }

        if (manualDate)
            log("Manual Avail Date " + availDate);

        // println("<<<<<<<<<<<<availDate>>>>>>>>>>>>>"+unit.getFieldValue("unitId")+"::"+availDate+"::"+manualDate+"::"+availChange+"::"+dest+"::"+lineOper+"::"+category+"::"+freightKind+"::"+commdityCode);

        // Manual Avail Date
        if (availChange) {
            if (event == null || !event.wasFieldChanged("UfvFlexDate03")) {
                def dueDate = calcDueDate(lineOper, tcn, commdityCode, isReefer, availDate, zone);

                if (!dueDate.equals(ufv.ufvFlexDate03)) {
                    update = true;
                }
                ufv.setFieldValue("ufvFlexDate03", dueDate);
            } else {
                log("Manual dueDate ");
            }
            if (event == null || !event.wasFieldChanged("LastFreeDay")) {

                def storageDate = calcStorageDate(lineOper, tcn, consigneeName, remarks, isReefer, availDate, zone);
                log("Storgae=" + storageDate);
                ufv.ufvLastFreeDay = storageDate;
            } else {
                log("Manual Last Free ");
            }

            // Avail date from the vessel
            //} else if (!manualDate && "HON".equals(dest) && "MAT".equals(lineOper) && "IMPRT".equals(category)
        } else if (!manualDate && ("ANK".equals(dest) || "DUT".equals(dest) || "KDK".equals(dest))  && "IMPRT".equals(category)
                && "FCL".equals(freightKind) && !"AUTO".equals(commdityCode) && !"AUTOCON".equals(commdityCode)) {
            def id = unit.getFieldValue("unitId");
            // Added Check for AdvanceVV
            def carrier = null;

            def advanceVV = unit.getFieldValue("unitFlexString04");
            if (advanceVV != null) {
                def vesselLookup = getGroovyClassInstance("GvyVesselLookup");
                carrier = vesselLookup.getCarrierVisit(advanceVV);
            } else {

                carrier = ufv.getUfvActualIbCv();
                if (carrier == null) {
                    carrier = unit.getUnitDeclaredIbCv();
                }

            }

            def arriveDate = null;
            if (carrier != null && carrier.getCvCarrierMode().equals(LocTypeEnum.VESSEL)) {
                //A1
                arriveDate = carrier.getCvCvd() != null ? carrier.getCvCvd().cvdTimeFirstAvailability : null;

                //if(arriveDate == null) arriveDate = carrier.getCvATA();
                //if(arriveDate == null) arriveDate = carrier.getCvCvd().getCvdETA();
            }

            if (arriveDate != null) {
                if (!arriveDate.equals(ufv.ufvFlexDate02)) {
                    update = true;
                }
                ufv.setFieldValue("ufvFlexDate02", arriveDate);


                if (event == null || !event.wasFieldChanged("UfvFlexDate03")) {
                    def dueDate = calcDueDate(lineOper, tcn, commdityCode, isReefer, arriveDate, zone);
                    if (!dueDate.equals(ufv.ufvFlexDate03)) {
                        update = true;
                    }
                    ufv.setFieldValue("ufvFlexDate03", dueDate);
                }

                if (event == null || !event.wasFieldChanged("LastFreeDay")) {
                    def storageDate = calcStorageDate(lineOper, tcn, consigneeName, remarks, isReefer, arriveDate, zone);
                    ufv.ufvLastFreeDay = storageDate;

                }

            }

            //} else if ((editDest && "HON".equals(previousDest)) || (editLineOperator && MAT.equals(previousLineOperator)) || (editCategory && "IMPRT".equals(previousCategory)) || (editFreightKind && "FCL".equals(previousFreightKind))) {
        } else if ((editDest && ("ANK".equals(previousDest) || "DUT".equals(previousDest) || "KDK".equals(previousDest))) || (editCategory && "IMPRT".equals(previousCategory)) || (editFreightKind && "FCL".equals(previousFreightKind))) {
            def id = unit.getFieldValue("unitId")

            ufv.setFieldValue("ufvFlexDate02", null);



            log("date=" + availDate);

            if (availDate != null) {
                update = true;
                if (event == null || !event.wasFieldChanged("UfvFlexDate03")) {
                    ufv.setFieldValue("ufvFlexDate03", null);
                }
                if (event == null || !event.wasFieldChanged("LastFreeDay")) {
                    ufv.ufvLastFreeDay = null;
                }
            }

            //A1 Null Check
            if (editFlag != null && editFlag.indexOf("X") != -1) {
                unit.setFieldValue("unitFlexString11", editFlag.replace("X", ""));
            }
        }

        return update;

    }


    public java.util.Date calcDueDate(String lineOper, String tcn, String commodityCode, boolean isReefer, java.util.Date availDate, TimeZone zone) {

        if ("HSD".equals(lineOper) || "HLC".equals(lineOper))
            return null;


        if (tcn != null) {
            if (isReefer) {
                return addBusinessDate(availDate, zone, 8);
            } else {
                return addBusinessDate(availDate, zone, 10);
            }
        }

        if (commodityCode != null && (commodityCode.equals("XMAS40") || commodityCode.equals("XMASTREE"))) {

            return addBusinessDate(availDate, zone, 7);
        }

        if (isReefer) {
            return addBusinessDate(availDate, zone, 6);
        }

        return addBusinessDate(availDate, zone, 10);
    }

    public java.util.Date calcStorageDate(String lineOper, String tcn, String consigneeName, String remarks, boolean isReefer, java.util.Date availDate, TimeZone zone) {
        //log("lineOper="+lineOper+" tcn="+tcn+" isReffer="+isReefer);
        if ("HSD".equals(lineOper) || "HLC".equals(lineOper))
            return null;
        if (tcn != null || isReefer)
            return null;


        if (consigneeName != null && consigneeName.toUpperCase().contains("KILLEBREW") && remarks != null &&
                (remarks.toUpperCase().contains("PLASTERBOARD") || remarks.toUpperCase().contains("PLASTER BOARD") || remarks.toUpperCase().contains("GYPSUM") ||
                        remarks.toUpperCase().contains("WALL BOARD") || remarks.toUpperCase().contains("WALLBOARD")))

        {
            return addCalendarDate(availDate, zone, 15);
        }
        return addCalendarDate(availDate, zone, 10);
    }



    public Date addBusinessDate(Date startDate, TimeZone zone, int addDays) {
        int altdays = addDays; //A5
        //println("addBusinessDate : altdays="+altdays+"   addDays="+addDays)
        //def exemptCalendarEvents = AppCalendarUtil.getEvents(exemptTypes, ContextHelper.getThreadUserContext());
        CalendarTypeEnum calendarTypeEnum = CalendarTypeEnum.getEnum("STORAGE");
        ArgoCalendar argoCal = ArgoCalendar.findDefaultCalendar(calendarTypeEnum);
        def exemptCalendarEvents = ArgoCalendarUtil.getEvents(exemptTypes, argoCal);
        Date endDate = ArgoCalendarUtil.getEndDate(startDate, zone, altdays, exemptCalendarEvents, exemptTypes);
        if (endDate != null) {
            Calendar calendar = Calendar.getInstance(zone);
            calendar.setTimeInMillis(endDate.getTime());
            calendar.add(Calendar.DAY_OF_YEAR, -1);
            endDate = calendar.getTime();
        }
        return endDate;
    }

    public Date addCalendarDate(Date startDate, TimeZone zone, int addDays) {
        //log("Free days="+addDays);
        int altdays = addDays - 1;  //A5
        //println("addCalendarDate : altdays="+altdays+"   addDays="+addDays)
        return ArgoCalendarUtil.getEndDate(startDate, zone, altdays, null, null);
    }


    public RoutingPoint findRoutingPoint(String inPointId) {
        DomainQuery dq = QueryUtils.createDomainQuery("RoutingPoint").addDqPredicate(PredicateFactory.eq(ArgoRefField.POINT_GKEY, inPointId));
        return (RoutingPoint) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }




    public List getAdvancedUnits(String vv) {
        try {
            DomainQuery dq = QueryUtils.createDomainQuery("Unit");

            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_FLEX_STRING04, vv));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            return list;
        } catch (Exception e) {
            println("Exception in GvyAvailDate.getAdvancedUnits " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

//A5 - Kokua NIS Coding for NIS Ports

    public void detentionForNisBarge(Object event, Object ufv, Object beginDate)
    {
        def unit = ufv.getUfvUnit();
        def dest =  null; def category = null;
        def freightKind = null; def commdityCode = null;
        def zone = unit.getUnitComplex().getTimeZone();
        def MAT;  def lineOper = null;  def tcn = null;
        boolean isReefer; String consigneeName  = null; String remarks=null;
        boolean update = false;
        def intendedObCarrId = null;

        try{
            dest =  unit.getFieldValue("unitRouting.rtgPOD1.pointId");
            intendedObCarrId = ufv.getFieldValue("ufvIntendedObCv.cvId");
            intendedObCarrId = intendedObCarrId != null ? intendedObCarrId : ''

            category = unit.unitCategory.name;
            freightKind = unit.unitFreightKind.name;
            if(unit.goods != null && unit.goods.gdsCommodity != null) {
                commdityCode = unit.goods.gdsCommodity.cmdyId;
            }

            def bizScope = ScopedBizUnit.findScopedBizUnit( "MAT", BizRoleEnum.LINEOP);
            MAT = bizScope.bzuGkey.toString();

            tcn = unit.unitFlexString12;
            isReefer = unit.isReefer();
            consigneeName =  unit.getFieldValue("unitGoods.gdsConsigneeAsString");
            remarks = unit.getFieldValue("unitRemark");

            lineOper = unit.unitLineOperator.bzuId;
            //ufv = unit.unitActiveUfv

        }catch(Exception e){
            e.printStackTrace();
        }

        try{
            // Avail date from the vessel
            if("IMPRT".equals(category) && "FCL".equals(freightKind) && !"AUTO".equals(commdityCode) && !"AUTOCON".equals(commdityCode) ) {
                def id = unit.getFieldValue("unitId");
                // Added Check for AdvanceVV
                def carrier = null;
                /*A6 def advanceVV = unit.getFieldValue("unitFlexString04");
                 if(advanceVV != null) {
                             def vesselLookup = getGroovyClassInstance("GvyVesselLookup");
                             carrier = vesselLookup.getCarrierVisit(advanceVV);
                         } else {
                           carrier = intendedObCarrId.startsWith('YB') ? ufv.getUfvIntendedObCv() : ufv.getUfvActualObCv();
                           if(carrier == null) {
                               carrier = unit.getUnitDeclaredObCv();
                           }
                 }*/
                carrier = intendedObCarrId.startsWith('YB') ? ufv.getUfvIntendedObCv() : ufv.getUfvActualObCv();
                if(carrier == null) {
                    carrier = unit.getUnitDeclaredObCv();
                }
                def arriveDate = null;
                if(carrier != null && carrier.getCvCarrierMode().equals(LocTypeEnum.VESSEL) ) {
                    //A1
                    arriveDate = carrier.getCvCvd() != null ? beginDate : null;
                }
                if(arriveDate != null ) {
                    if(!arriveDate.equals(ufv.ufvFlexDate02) ) {
                        update = true;
                    }
                    ufv.setFieldValue("ufvFlexDate02",arriveDate);

                    if (event == null || !event.wasFieldChanged("UfvFlexDate03") ) {
                        def dueDate = calcDueDate(lineOper,tcn, commdityCode,isReefer, arriveDate, zone);

                        if(!dueDate.equals(ufv.ufvFlexDate03) ) {
                            update = true;
                        }
                        ufv.setFieldValue("ufvFlexDate03",dueDate);
                    }

                    if (event == null ||  !event.wasFieldChanged("LastFreeDay") ) {
                        def storageDate = calcStorageDate(lineOper,tcn, consigneeName,  remarks, isReefer, arriveDate, zone);
                        ufv.ufvLastFreeDay = storageDate;
                    }
                }
            }//Outer if Ends
        }catch(Exception e){
            e.printStackTrace();
        }
    }//Method Ends


    public boolean setAvailDateOnUnit(Object unit, Object event,Date beginDate) {
        println(":::::::inside setAvailDateOnUnit:::::");
        if (event != null) {
            ContextHelper.setThreadExternalUser(event.event.evntAppliedBy);
        }

        def editFlag = unit.getUnitFlexString11();
        boolean update = false;

        def previousDest = null;
        def previousLineOperator = null;
        def previousCategory = null;
        def previousFreightKind = null;

        def editDest = false;
        def editLineOperator = false;
        def editCategory = false;
        def editFreightKind = false;

        if (event != null) {
            def pointId = event.getPreviousPropertyAsString("PODRef");
            def point = findRoutingPoint(pointId);
            if (point != null) {
                previousDest = point.pointId;
            }
            previousLineOperator = event.getPreviousPropertyAsString("LineOperator");
            previousCategory = event.getPreviousPropertyAsString("Category");
            previousFreightKind = event.getPreviousPropertyAsString("FreightKind");

            editDest = event.wasFieldChanged("PODRef");
            editLineOperator = event.wasFieldChanged("LineOperator");
            editCategory = event.wasFieldChanged("Category");
            editFreightKind = event.wasFieldChanged("FreightKind");
        }

        //log("PREV="+previousDest+","+previousLineOperator+","+previousCategory+","+previousFreightKind);



        def dest = null;
        def category = null;
        def freightKind = null;
        def commdityCode = null;
        def zone = unit.getUnitComplex().getTimeZone();
        def MAT;
        EquipRfrTypeEnum ueRfrType = null;

        try {
            dest = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
            category = unit.unitCategory.name;
            freightKind = unit.unitFreightKind.name;
            ueRfrType =  unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypRfrType");
            if (unit.goods != null && unit.goods.gdsCommodity != null) {
                commdityCode = unit.goods.gdsCommodity.cmdyId;
            }

        } catch (Exception e) {
            log("Exception =" + e.getMessage());
        }

        try {
            def bizScope = ScopedBizUnit.findScopedBizUnit("MAT", BizRoleEnum.LINEOP);
            MAT = bizScope.bzuGkey.toString();
        } catch (Exception e) {

        }

        //boolean result = (MAT == previousLineOperator);
        //String rStr = Boolean.toString(result);
        //log("Mat="+MAT+" "+editLineOperator+" "+previousLineOperator+" "+ rStr );

        def lineOper = null;
        def tcn = unit.unitFlexString12;
        //println("ueRfrType-->"+ueRfrType.toString());
        boolean isReefer = !EquipRfrTypeEnum.NON_RFR.equals(ueRfrType) ? true : false;
        //println("isReefer-->"+isReefer);
        String consigneeName = unit.getFieldValue("unitGoods.gdsConsigneeAsString");
        String remarks = unit.getFieldValue("unitRemark");


        try {
            lineOper = unit.unitLineOperator.bzuId;
        } catch (Exception e) {
            // do nothing
        }

        def ufv = unit.unitActiveUfv;

        // Not active UFV, find an advised UFV.
        if (ufv == null) {
            def lookup = getGroovyClassInstance("GvyUnitLookup");
            ufv = lookup.lookupFacility(unit.primaryKey);
        }

        // Can not update without UFV
        if (ufv == null)
            return update;

        def availDate = ufv.getFieldValue("ufvFlexDate02");
        boolean availChange = false;
        boolean manualDate = false;

        //log("Prev dest = "+previousDest+" lineOper="+lineOper+" category="+category+" kind="+freightKind+"comm="+commdityCode);


        if (editFlag != null && editFlag.contains("X")) {
            manualDate = true;

        }

        if (event != null && event.wasFieldChanged("UfvFlexDate02")) {
            availChange = true;
            manualDate = true;
            /*
                 if(editFlag == null) {
                    unit.setFieldValue("unitFlexString11","X");
                 } else if(!editFlag.contains("X")) {
                    unit.setFieldValue("unitFlexString11",editFlag+"X");
                 }
           */

        }

        if (manualDate)
            log("Manual Avail Date " + availDate);

        // Manual Avail Date
        if (availChange) {
            if (event == null || !event.wasFieldChanged("UfvFlexDate03")) {
                def dueDate = calcDueDate(lineOper, tcn, commdityCode, isReefer, availDate, zone);

                if (!dueDate.equals(ufv.ufvFlexDate03)) {
                    update = true;
                }
                ufv.setFieldValue("ufvFlexDate03", dueDate);
            } else {
                log("Manual dueDate ");
            }
            if (event == null || !event.wasFieldChanged("LastFreeDay")) {

                def storageDate = calcStorageDate(lineOper, tcn, consigneeName, remarks, isReefer, availDate, zone);
                log("Storgae=" + storageDate);
                ufv.ufvLastFreeDay = storageDate;
            } else {
                log("Manual Last Free ");
            }

            // Avail date from the vessel
            //} else if (!manualDate && "HON".equals(dest) && "MAT".equals(lineOper) && "IMPRT".equals(category)
        } else if (!manualDate && ("ANK".equals(dest) ||"DUT".equals(dest) || "KDK".equals(dest)) && "IMPRT".equals(category)
                && "FCL".equals(freightKind) && !"AUTO".equals(commdityCode) && !"AUTOCON".equals(commdityCode)) {
            def id = unit.getFieldValue("unitId");
            // Added Check for AdvanceVV
            def carrier = null;

            def advanceVV = unit.getFieldValue("unitFlexString04");
            if (advanceVV != null) {
                def vesselLookup = getGroovyClassInstance("GvyVesselLookup");
                carrier = vesselLookup.getCarrierVisit(advanceVV);
            } else {

                //carrier = ufv.getUfvActualIbCv(); [KRajendran-03-04-14]Do not use IB actual, bcz it will return here GEN_TRUCK, Use IB Declrd
                if (carrier == null) {
                    carrier = unit.getUnitDeclaredIbCv();
                }

            }

            def arriveDate = null;
            if (carrier != null && carrier.getCvCarrierMode().equals(LocTypeEnum.VESSEL)) {

                if (beginDate !=null)
                {
                    arriveDate = beginDate;
                } else {
                    //A1
                    arriveDate = carrier.getCvCvd() != null ? carrier.getCvCvd().cvdTimeFirstAvailability : null;
                }


                //if(arriveDate == null) arriveDate = carrier.getCvATA();
                //if(arriveDate == null) arriveDate = carrier.getCvCvd().getCvdETA();
            }


            if (arriveDate != null) {
                if (!arriveDate.equals(ufv.ufvFlexDate02)) {
                    update = true;
                }
                ufv.setFieldValue("ufvFlexDate02", arriveDate);


                if (event == null || !event.wasFieldChanged("UfvFlexDate03")) {
                    def dueDate = calcDueDate(lineOper, tcn, commdityCode, isReefer, arriveDate, zone);
                    if (!dueDate.equals(ufv.ufvFlexDate03)) {
                        update = true;
                    }
                    ufv.setFieldValue("ufvFlexDate03", dueDate);
                }

                if (event == null || !event.wasFieldChanged("LastFreeDay")) {
                    def storageDate = calcStorageDate(lineOper, tcn, consigneeName, remarks, isReefer, arriveDate, zone);
                    ufv.ufvLastFreeDay = storageDate;

                }

            }

        } else if ((editDest && ("ANK".equals(previousDest) || "DUT".equals(previousDest) || "KDT".equals(previousDest))) || (editLineOperator && MAT.equals(previousLineOperator)) || (editCategory && "IMPRT".equals(previousCategory)) || (editFreightKind && "FCL".equals(previousFreightKind))) {

            def id = unit.getFieldValue("unitId")

            ufv.setFieldValue("ufvFlexDate02", null);



            log("date=" + availDate);

            if (availDate != null) {
                update = true;
                if (event == null || !event.wasFieldChanged("UfvFlexDate03")) {
                    ufv.setFieldValue("ufvFlexDate03", null);
                }
                if (event == null || !event.wasFieldChanged("LastFreeDay")) {
                    ufv.ufvLastFreeDay = null;
                }
            }

            //A1 Null Check
            if (editFlag != null && editFlag.indexOf("X") != -1) {
                unit.setFieldValue("unitFlexString11", editFlag.replace("X", ""));
            }
        }

        return update;

    }

}