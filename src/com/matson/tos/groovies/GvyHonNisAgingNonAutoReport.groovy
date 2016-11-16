import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.Date

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit

import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery
import com.navis.argo.ArgoField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.road.business.model.*;
import java.util.Calendar;

import com.navis.argo.business.model.Facility;
import com.navis.services.business.event.Event;
import com.navis.argo.business.reference.Equipment
import com.navis.services.business.rules.EventType;
import com.navis.services.business.api.EventManager;
import com.navis.inventory.InventoryField;
import com.navis.services.business.event.EventFieldChange;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.services.business.event.GroovyEvent;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import com.navis.inventory.business.units.GoodsBase;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.model.LocPosition;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.navis.argo.business.atoms.LocTypeEnum;

public class GvyHonNisAgingNonAutoReport extends GroovyInjectionBase
{
    private static Logger LOGGER = Logger.getLogger(GvyHonNisAgingNonAutoReport.class);

    private final String  emailFrom = '1aktosdevteam@matson.com';
    private final String emailTo = "1aktosdevteam@matson.com";
    //private final String emailTo = "1aktosdevteam@matson.com";

    def inj = null;

    public boolean execute(Map params)
    {
        LOGGER.info("Inside GvyHonNisAgingNonAutoReport");
        try{
            inj = new GroovyInjectionBase();
            ArrayList unitList = null;
            unitList = getUnits()
            println("unitListMap size()"+unitList.size());
            HashMap parameters = new HashMap();
            Boolean isDayLightSaving = false;
            int offset = 0;
            Calendar pstCal = Calendar.getInstance();
            Date pstDate = pstCal.getTime();
            Date hstDate = null;
            isDayLightSaving = pstCal.getTimeZone().inDaylightTime(pstDate);
            offset = isDayLightSaving?-3:-2;
            pstCal.add(Calendar.HOUR, offset);
            hstDate = pstCal.getTime();

            parameters.put("Date",hstDate);
            //Create and Mail Report
            //LOGGER.info("BEFORE CREATING JRDATASOURCE");
            JRDataSource ds = new JRMapCollectionDataSource(unitList);

            HashMap reportDesignsmap = new HashMap();
            reportDesignsmap.put("HON/NIS AGING NON AUTO CONTAINERS DETAIL REPORT",ds);
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            reportRunner.emailReports(reportDesignsmap,parameters, emailTo, "HON/NIS AGING NON AUTO CONTAINERS DETAIL REPORT" ,"Attached report for HON/NIS AGING NONAUTO CONTAINERS DETAIL");
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public List getUnits()
    {

        Date inTime =new Date()-10;
        MetafieldId UFV_CMDTY_ID = MetafieldIdFactory.getCompoundMetafieldId(UnitField.UFV_UNIT,UnitField.UNIT_CMDY_ID);

        MetafieldId UFV_GDS_CONSIGNEE_NAME = MetafieldIdFactory.getCompoundMetafieldId(UnitField.UFV_GOODS, UnitField.GDS_CONSIGNEE_NAME);

        try {
            ArrayList units = new ArrayList();
            ArrayList groundedUnits = new ArrayList();
            ArrayList wheeledUnits = new ArrayList();
            ArrayList combinedList = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
            // .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, "MATU2276713"))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY,"IMPRT"))
                    .addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE,"S40_YARD","S30_ECIN"))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FREIGHT_KIND,"FCL"))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_GDS_DESTINATION,"OPT"))
            //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_CMDTY,"AUTO"))
            //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_CMDTY,"AUTOCON"))
            //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_CMDTY,"AUTOCY"))
                    .addDqPredicate(PredicateFactory.le(UnitField.UFV_TIME_IN,inTime))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_PRIMARY_EQTYPE_ID))
                    .addDqOrdering(Ordering.asc(UFV_GDS_CONSIGNEE_NAME))
                    .addDqOrdering(Ordering.desc(UnitField.UFV_TIME_IN));


            LOGGER.info("DomainQuery ::::"+dq.toHqlSelectString("alias"));


            //println("DomainQuery :::: "+ dq);
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (ufvList !=null)
            {
                LOGGER.info("After unitList size is "+ufvList.size());
            }else {

                LOGGER.info("0 records returned from domain query");
            }

            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    LocPosition localLocPosition = unit.findCurrentPosition();
                    def slot = localLocPosition.getPosSlot();
                    def locType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType");

                    def cmdyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    println(unit.getFieldValue("unitId")+"===="+unit.getFieldValue("unitActiveUfv.ufvTransitState")+"===="+unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"));
                    String PositionLocType = "";
                    if(LocTypeEnum.YARD.equals(locType)){
                        PositionLocType = "Y"
                    }else if(LocTypeEnum.VESSEL.equals(locType)){
                        PositionLocType = "V"
                    }

                    if ('AUTO'.equalsIgnoreCase(cmdyId) || 'AUTOCON'.equalsIgnoreCase(cmdyId) || 'AUTOCY'.equalsIgnoreCase(cmdyId))
                    {
                        continue;
                    }

                    String eqType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                    //println("eqType before trimmimg ----"+eqType);
                    if (eqType!=null & eqType.size()>=3)
                    {
                        eqType = eqType.substring(0,3);
                        //println("eqType-----"+eqType);
                    }
                    if (slot!=null && ( slot.startsWith('V') || slot.startsWith('S') || slot.startsWith('O') || slot == 'GRD' ) ){
                        Map unitMap = new HashMap();
                        //println("Grounded Unit Id  : "+unit);
                        //println("Position :" +localLocPosition);
                        //println("Position Slot :" +slot);

                        unitMap.put("DwellDays",unit.getFieldValue("unitActiveUfv.ufvDwellDays"));
                        unitMap.put("UnitNbr",unit.getFieldValue("unitId"));
                        unitMap.put("PositionLocType",PositionLocType);
                        unitMap.put("PositionSlot",unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));
                        unitMap.put("InTime",unit.getFieldValue("unitActiveUfv.ufvTimeIn"));
                        unitMap.put("EquipmentType",unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId"));
                        unitMap.put("GoodsConsigneeName",unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName"));
                        unitMap.put("UnitFlexString01","Grounded");
                        unitMap.put("typeUpto3Chars",eqType);
                        groundedUnits.add(unitMap);
                    }else{
                        Map unitMap = new HashMap();
                        //println("Wheeled Unit Id  : "+unit);
                        //println("Position :" +localLocPosition);
                        //println("Position SLot :" +slot);

                        unitMap.put("DwellDays",unit.getFieldValue("unitActiveUfv.ufvDwellDays"));
                        unitMap.put("UnitNbr",unit.getFieldValue("unitId"));
                        unitMap.put("PositionLocType",PositionLocType);
                        unitMap.put("PositionSlot",unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));
                        unitMap.put("InTime",unit.getFieldValue("unitActiveUfv.ufvTimeIn"));
                        unitMap.put("EquipmentType",unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId"));
                        unitMap.put("GoodsConsigneeName",unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName"));
                        unitMap.put("UnitFlexString01","Wheeled");
                        unitMap.put("typeUpto3Chars",eqType);
                        wheeledUnits.add(unitMap);
                    }
                }
            }
            //	println("Grounded Size :" + groundedUnits.size);
            //	println("Wheeled Size :" + wheeledUnits.size);

            //adding to one single list

            for (int i=0;i<groundedUnits.size();i++)
            {
                //	println("Inside grounded");
                Map extractMap = (HashMap)groundedUnits.get(i);
                //println("extractMap----"+extractMap.toString());
                combinedList.add(extractMap);
            }
            //println("combinedList size -1  :" + combinedList.size);
            for (int j=0;j<wheeledUnits.size();j++  )
            {
                //	println("Inside wheeled");
                Map extractMap = (HashMap)wheeledUnits.get(j);
                //println("extractMap----"+extractMap.toString());
                combinedList.add(extractMap);
            }
            //	println("combinedList size - 2  :" + combinedList.size);
            //println("combinedList Size :" + combinedList.size);
            return combinedList;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }


    }

}
