/*
**********************************************************************
* Srno Doer  Date      Change
* A1   GR    07/21/09
* A2   GR    07/08/10  Get DeclaredIBcarrier for SIT_UNASSIGN
* A3   GR    01/12/11  Substract One day from  Method
                       addBusinessDate, addCalendarDate
* A4      09/02/12    Glenn Raposo       Changed Code for TOS2.1 Upgrade issue
                                         Replaces AppCalendarUtil to ArgoCalendarUtil since navis team chaged code
* A5   LC    10/15/12  Remove detention calculation methods calcDueDate() and calcStorageDate() instead reusing the
                       calling the GvyAvailDate methods.
**********************************************************************
*/
import com.navis.argo.business.api.GroovyApi;
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
import java.util.Calendar;
import java.util.TimeZone;

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
 *A1 - SKB 01/21/09 Changed manual update from V to X.
 *                  Also changed logic for updating det and last free
 *A2 - SKB 02/06/09 Changed logic for deletion.
 */
//A4 - 02/09/12 - With the TOS2.1 Upgrade navis team split the Calendar util from Framework package and Argo Package
// - causing our 1.6 code to refer to the old table (AppCalendarEvent) to new table (ArgoCalendarEvent)

public class GvyCargoEditAvailDate extends GroovyInjectionBase {

/*private static AppCalendarEventType[] exemptTypes = null;

static {
	exemptTypes = new AppCalendarEventType[2];
	exemptTypes[0] = AppCalendarEventType.findOrCreateAppCalendarEventType("EXEMPT_DAY");
    exemptTypes[1] = AppCalendarEventType.findOrCreateAppCalendarEventType("GRATIS_DAY");
}*/

    private static  ArgoCalendarEventType[] exemptTypes = null;

    static {
        exemptTypes = new ArgoCalendarEventType[2];
        exemptTypes[0] = ArgoCalendarEventType.findOrCreateArgoCalendarEventType("EXEMPT_DAY");
        exemptTypes[1] = ArgoCalendarEventType.findOrCreateArgoCalendarEventType("GRATIS_DAY");
    }

    def gvyAvailDate = null;
    def gvyBaseClass = new GroovyInjectionBase();

    public boolean setAvailDate(Object unit, Object event, String previousDischPort) {
        if(event != null) {
            ContextHelper.setThreadExternalUser(event.event.evntAppliedBy);
        }
        def editFlag = unit.getUnitFlexString11();
        boolean update = false;

        def previousDest = null;
        def previousLineOperator = null;
        def previousCategory = null;
        def previousFreightKind =null;

        def editDest = false;
        def editLineOperator = false;
        def editCategory = false;
        def editFreightKind = false;

        if(event != null) {
            def pointId = previousDischPort;
            //def point = findRoutingPoint(pointId);
            if(pointId != null) {
                previousDest =  pointId  //point.pointId;
            }
            previousLineOperator = event.getPreviousPropertyAsString("LineOperator");
            previousCategory = event.getPreviousPropertyAsString("Category");
            previousFreightKind = event.getPreviousPropertyAsString("FreightKind");

            def currDischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            currDischPort = currDischPort != null ? currDischPort  : ""
            if(!currDischPort.equals(previousDest)){
                editDest = true;
            }
            editLineOperator = event.wasFieldChanged("LineOperator");
            editCategory = event.wasFieldChanged("Category");
            editFreightKind = event.wasFieldChanged("FreightKind");
        }

        //log("PREV="+previousDest+","+previousLineOperator+","+previousCategory+","+previousFreightKind);



        def dest =  null;
        def category = null;
        def freightKind = null;
        def commdityCode = null;
        def zone = unit.getUnitComplex().getTimeZone();
        def MAT;

        try {
            dest =  unit.getFieldValue("unitRouting.rtgPOD1.pointId");
            category = unit.unitCategory.name;
            freightKind = unit.unitFreightKind.name;
            if(unit.goods != null && unit.goods.gdsCommodity != null) {
                commdityCode = unit.goods.gdsCommodity.cmdyId;
            }


        } catch (Exception e) {
            log("Exception ="+e.getMessage());
        }

        try {
            def bizScope = ScopedBizUnit.findScopedBizUnit( "MAT", BizRoleEnum.LINEOP);
            MAT = bizScope.bzuGkey.toString();
        } catch (Exception e) {

        }

        //boolean result = (MAT == previousLineOperator);
        //String rStr = Boolean.toString(result);
        //log("Mat="+MAT+" "+editLineOperator+" "+previousLineOperator+" "+ rStr );

        def lineOper = null;
        def tcn = unit.unitFlexString12;
        boolean isReefer = unit.isReefer();
        String consigneeName =  unit.getFieldValue("unitGoods.gdsConsigneeAsString");
        String remarks = unit.getFieldValue("unitRemark");


        try {
            lineOper = unit.unitLineOperator.bzuId;
        } catch (Exception e) {
            // do nothing
        }

        def ufv = unit.unitActiveUfv;

        // Not active UFV, find an advised UFV.
        if(ufv == null) {
            def lookup =  getGroovyClassInstance("GvyUnitLookup");
            ufv = lookup.lookupFacility(unit.primaryKey);
        }

        // Can not update without UFV
        if(ufv == null) return update;

        def availDate = ufv.getFieldValue("ufvFlexDate02");
        boolean availChange = false;
        boolean manualDate =  false;

        //log("Prev dest = "+previousDest+" lineOper="+lineOper+" category="+category+" kind="+freightKind+"comm="+commdityCode);


        if(editFlag != null && editFlag.contains("X") ) {
            manualDate = true;

        }

        if(event != null &&  event.wasFieldChanged("UfvFlexDate02")) {
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

        if(manualDate) log("Manual Avail Date "+availDate);

        //A5 Using the GvyAvailDate methods to calculate the detention dates
        try {
            gvyAvailDate = gvyBaseClass.getGroovyClassInstance("GvyAvailDate");
        } catch (Exception e) {
            GroovyApi.log("Exception in GvyCargoEditAvailDate.setAvailDate()" + e);
        }


        // Manual Avail Date
        if(availChange) {
            if (event == null || !event.wasFieldChanged("UfvFlexDate03") ) {
                def dueDate = gvyAvailDate.calcDueDate(lineOper,tcn, commdityCode,isReefer, availDate, zone);

                if(!dueDate.equals(ufv.ufvFlexDate03) ) {
                    update = true;
                }
                ufv.setFieldValue("ufvFlexDate03",dueDate);
            } else {
                log("Manual dueDate ");
            }
            if (event == null ||  !event.wasFieldChanged("LastFreeDay") ) {

                def storageDate = gvyAvailDate.calcStorageDate(lineOper,tcn, consigneeName, remarks,isReefer, availDate, zone);
                log("Storgae="+storageDate);
                ufv.ufvLastFreeDay = storageDate;
            } else {
                log("Manual Last Free ");
            }

            // Avail date from the vessel
        } else if(!manualDate && ("ANK".equals(dest) || "DUT".equals(dest) || "KDK".equals(dest)) && "MAT".equals(lineOper) && "IMPRT".equals(category)
                && "FCL".equals(freightKind) && !"AUTO".equals(commdityCode) && !"AUTOCON".equals(commdityCode) ) {
            def id = unit.getFieldValue("unitId");
            // Added Check for AdvanceVV
            def carrier = null;

            def advanceVV = unit.getFieldValue("unitFlexString04");
            if(advanceVV != null) {
                def vesselLookup = getGroovyClassInstance("GvyVesselLookup");
                carrier = vesselLookup.getCarrierVisit(advanceVV);
            } else {
                carrier = ufv.getUfvActualIbCv();
                if(carrier == null || !carrier.getCvCarrierMode().equals(LocTypeEnum.VESSEL)) { //A2
                    carrier = unit.getUnitDeclaredIbCv();
                }
            }


            def arriveDate = null;
            if(carrier != null && carrier.getCvCarrierMode().equals(LocTypeEnum.VESSEL) ) {
                //A1
                arriveDate = carrier.getCvCvd() != null ? carrier.getCvCvd().cvdTimeFirstAvailability : null;
                //if(arriveDate == null) arriveDate = carrier.getCvATA();
                //if(arriveDate == null) arriveDate = carrier.getCvCvd().getCvdETA();
            }

            if(arriveDate != null) {

                if(!arriveDate.equals(ufv.ufvFlexDate02) ) {
                    update = true;
                }
                ufv.setFieldValue("ufvFlexDate02",arriveDate);

                if (event == null || !event.wasFieldChanged("UfvFlexDate03") ) {
                    def dueDate = gvyAvailDate.calcDueDate(lineOper,tcn, commdityCode,isReefer, arriveDate, zone);
                    if(!dueDate.equals(ufv.ufvFlexDate03) ) {
                        update = true;
                    }
                    ufv.setFieldValue("ufvFlexDate03",dueDate);
                }

                if (event == null ||  !event.wasFieldChanged("LastFreeDay") ) {
                    def storageDate = gvyAvailDate.calcStorageDate(lineOper,tcn, consigneeName,  remarks, isReefer, arriveDate, zone);
                    ufv.ufvLastFreeDay = storageDate;

                }

            }


        } else if( (editDest && ("ANK".equals(previousDest) || "DUT".equals(previousDest) || "KDK".equals(previousDest)) ) || (editLineOperator && MAT.equals(previousLineOperator) ) || (editCategory && "IMPRT".equals(previousCategory) ) || (editFreightKind && "FCL".equals(previousFreightKind) ) ) {

            def id = unit.getFieldValue("unitId")

            ufv.setFieldValue("ufvFlexDate02",null);


            log("date="+availDate);

            if(availDate != null) {

                update = true;
                if (event == null ||  !event.wasFieldChanged("UfvFlexDate03") ) {
                    ufv.setFieldValue("ufvFlexDate03",null);
                }
                if (event == null ||  !event.wasFieldChanged("LastFreeDay") ) {
                    ufv.ufvLastFreeDay = null;
                }
            }

            //A1 Null Check
            if(editFlag != null && editFlag.indexOf("X") != -1) {
                unit.setFieldValue("unitFlexString11",editFlag.replace("X",""));
            }
        }

        return update;



    }

    public  RoutingPoint findRoutingPoint(String inPointId)
    {
        DomainQuery dq = QueryUtils.createDomainQuery("RoutingPoint").addDqPredicate(PredicateFactory.eq(ArgoRefField.POINT_GKEY, inPointId));
        return (RoutingPoint)HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }




    public List getAdvancedUnits(String vv) {
        try {
            DomainQuery dq = QueryUtils.createDomainQuery("Unit");

            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_FLEX_STRING04,vv));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            return list;
        } catch (Exception e) {
            println("Exception in GvyAvailDate.getAdvancedUnits "+e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

}