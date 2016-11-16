import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import java.text.SimpleDateFormat
import java.text.DateFormat

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

/*
* Author : Raghu Iyer
* Date Written : 12/28/2012
* Description: This groovy is used to generate the Reefer report after Stowplan and Manifest process
*/

public class MatReportReefers extends GroovyInjectionBase
{

    private final String  emailFrom = '1aktosdevteam@matson.com'
    //private final String emailTo = "riyer@matson.com";
    private final String emailTo = "1aktosdevteam@matson.com";

    private String outBoundCarrierId = null
    def inj = null;
    String VesVoy = null;


// Retrieves DS Field-Based on freightkindKey,commodityId
    public String getDs(String freightkindKey,String cmdtyId)
    {
        def ds = ''
        if(cmdtyId.equals('AUTOCON')){
            ds = 'CON'
        }else if(cmdtyId.equals('AUTO')){
            ds = 'AUT'
        }else if (freightkindKey.equals('FCL') || cmdtyId.equals('AUTOCY')){
            ds = 'CY'
        }else{
            ds = '%'
        }
        return ds
    }

    public double celsiusToFahrenheit(Double celsius)
    {
        double fahr = (celsius * 9/5) + 32;
        double result = new BigDecimal(""+fahr).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }

    // Retrieves DIR Field-Based on category & transitState
    public  String getDir(String category,String transitState,String freightkindKey, String expGateBkgNbr,String lkpLocType,String gdsBlNbr)
    {
        def dir = ''
        if(category.equals('EXPRT') && !freightkindKey.equals('MTY') ){
            dir='OUT'
        } else if (category.equals('EXPRT') && expGateBkgNbr != null){
            dir='OUT'
        }else if (category.equals('IMPRT') && lkpLocType.equals('VESSEL') && freightkindKey.equals('MTY') && gdsBlNbr == null){
            dir = 'MTY'
        }else if (category.equals('IMPRT')){
            dir='IN'
        }else if (category.equals('TRSHP') && transitState.equals('S20_INBOUND')){
            dir = 'IN'
        }else if (category.equals('TRSHP') && transitState.trim().length() > 0){
            dir = 'OUT'
        }else if (category.equals('THRGH') && !freightkindKey.equals('MTY')){
            dir = 'OUT'
        }else{
            dir = 'MTY'
        }
        return dir;
    }

    public String getSrv(Object unit)
    {
        def srv = ''
        def vesselLineOptr = ''
        try
        {
            inj = new GroovyInjectionBase();
            def unitLineOperator=unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId")
            unitLineOperator = unitLineOperator != null ? unitLineOperator : ''

            vesselLineOptr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoVesselVisit.cvCvd.vvdBizu.bzuId")
            vesselLineOptr = vesselLineOptr != null ? vesselLineOptr : ''

            def intObCarrierMode=unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCarrierMode")
            intObCarrierMode = intObCarrierMode != null ? intObCarrierMode.getKey() : ''

            def dObCarreirmode = unit.getFieldValue("unitRouting.rtgDeclaredCv.cvCarrierMode")
            dObCarreirmode = dObCarreirmode != null ? dObCarreirmode.getKey() : ''

            //Get Equi SRV
            def unitEquipment = unit.getUnitPrimaryUe()
            def ueEquipmentState = unitEquipment.getUeEquipmentState()
            def equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : ''

            def ObDeclaredVesClassType = unit.getFieldValue("unitRouting.rtgDeclaredCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            ObDeclaredVesClassType = ObDeclaredVesClassType != null ? ObDeclaredVesClassType.getKey() : ""

            def intObCarVesType = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType")
            intObCarVesType = intObCarVesType != null ? intObCarVesType.getKey() : ""

            def bookingNumber = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");

            //Equipment Deliver Order Object

            def gvyEdoObj = inj.getGroovyClassInstance("GvyCmisEquipmentDeliveryOrder");
            def edo = bookingNumber != null ? gvyEdoObj.findEquipmentDeliveryOrder(bookingNumber) : null

            def isVessel =  (intObCarrierMode.equals('VESSEL') || dObCarreirmode.equals('VESSEL')) ? true : false
            def isBarge = (ObDeclaredVesClassType.equals('BARGE') || intObCarVesType.equals('BARGE')) ? true : false
            def isLongHaul = (ObDeclaredVesClassType.equals('CELL') || intObCarVesType.equals('CELL')) ? true : false
            def isVesLineOperatorMat = vesselLineOptr.equals('MAT') ? true : false

            //println("EqFlex01 :"+equipFlex01+" isVessel:"+isVessel+"  isBarge:"+isBarge+"  isLongHaul:"+isLongHaul+"   isVesLineOperatorMat:"+isVesLineOperatorMat)

            if(equipFlex01.equals('MAT')){
                srv = 'MAT'
            }else if(edo != null){
                srv = gvyEdoObj.getEDOLineOperator(edo)
            }else if(equipFlex01.equals('CLIENT') && isVessel && isBarge){
                srv = 'MAT'
            }else if (equipFlex01.equals('CLIENT') && isVessel && isLongHaul && isVesLineOperatorMat){
                srv = 'MAT'
            }else if(equipFlex01.equals('CLIENT') && bookingNumber && isVesLineOperatorMat){
                srv =  'MAT'
            }else if(equipFlex01.equals('CLIENT') && isVessel && isLongHaul && !isVesLineOperatorMat){
                srv =  unitLineOperator
            }else if(equipFlex01.equals('CLIENT') && !isVessel){
                srv =  unitLineOperator
            }else{
                srv =  unitLineOperator
            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return srv
    }


    public String processTrade(Object unit, Object srv){
        def trade=''
        def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId")
        def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
        def destination = unit.getFieldValue("unitGoods.gdsDestination")
        try{
            if('MAT'.equals(srv) || 'CRX'.equals(srv)){

                if('GUM'.equals(dischargePort)){
                    trade = ports.get(destination)
                }else {
                    trade =  ports.get(dischargePort)
                }

                if(trade == null){
                    trade = 'H'
                }
                def loadPortTrade = ports.get(loadPort)
                if('M'.equals(loadPortTrade)){
                    trade = loadPortTrade
                }
            }else{
                trade = 'C'
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return trade;
    }

    public static Map ports = new HashMap();
    static {

        ports.put('AUC','C')
        ports.put('BRI','C')
        ports.put('MEL','C')
        ports.put('NUK','C')
        ports.put('PAP','C')
        ports.put('SFO','C')
        ports.put('SUV','C')
        ports.put('SYD','C')
        ports.put('SYD','C')
        ports.put('WEL','C')
        ports.put('WLG','C')
        ports.put('HAK','F')
        ports.put('KAO','F')
        ports.put('KEEL','F')
        ports.put('KOB','F')
        ports.put('portsH','F')
        ports.put('MOJ','F')
        ports.put('NAH','F')
        ports.put('NGB','F')
        ports.put('NGO','F')
        ports.put('OSA','F')
        ports.put('PUS','F')
        ports.put('SHA','F')
        ports.put('TSI','F')
        ports.put('XMN','F')
        ports.put('YOK','F')
        ports.put('YTN','F')
        ports.put('API','G')
        ports.put('APW','G')
        ports.put('GUM','G')
        ports.put('KMI','G')
        ports.put('PAG','G')
        ports.put('PNP','G')
        ports.put('PPT','G')
        ports.put('PUX','G')
        ports.put('RTA','G')
        ports.put('SPN','G')
        ports.put('TIN','G')
        ports.put('TMGU','G')
        ports.put('UUK','G')
        ports.put('YAP','G')
        ports.put('HIL','H')
        ports.put('HNC','H')
        ports.put('HON','H')
        ports.put('HUHI','H')
        ports.put('KAH','H')
        ports.put('KAHI','H')
        ports.put('KHI','H')
        ports.put('KKHI','H')
        ports.put('LAX','H')
        ports.put('LNI','H')
        ports.put('MIX','H')
        ports.put('MOL','H')
        ports.put('NAW','H')
        ports.put('NAX','H')
        ports.put('OAC','H')
        ports.put('OAK','H')
        ports.put('PCHI','H')
        ports.put('PDX','H')
        ports.put('PRL','H')
        ports.put('RCH','H')
        ports.put('SEA','H')
        ports.put('UEHI','H')
        ports.put('EBY','M')
        ports.put('JIS','M')
        ports.put('KWJ','M')
        ports.put('MAJ','M')
        ports.put('WAK','M')
    }

    public boolean execute(Map params)
    {
        try
        {
            inj = new GroovyInjectionBase();

            ArrayList reportUnitList =  new ArrayList();
            List unitList = null;
            //println("unitList.size()"+unitList.size());
            unitList = getUnits()
            Iterator unitIterator = unitList.iterator();
            while(unitIterator.hasNext())
            {
                def unit = unitIterator.next();
                HashMap unitsDataMap = populateUnitData(unit);
                if(unitsDataMap != null) {
                    reportUnitList.add(unitsDataMap);
                }
            }

            println("reportUnitList -------------------- :"+ (reportUnitList != null ? reportUnitList.size() : "EMPTY"))
            //Set Report Parameter
            if (reportUnitList.size() > 0) {
                HashMap parameters = new HashMap();

                //Create and Mail Report
                JRDataSource ds = new JRMapCollectionDataSource(reportUnitList);

                HashMap reportDesignsmap = new HashMap();
                reportDesignsmap.put("REEFER REPORT",ds);

                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReports(reportDesignsmap,parameters, emailTo, vesVoy+" Reefer Report" ,"Reefer Report Attached");
                println("reportUnitList ------- Success")
            }
            else {
                def emailSender = inj.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailFrom,emailTo, vesVoy+" Reefer Report","No reefer units reported");
                println("reportUnitList ------- No data to print")
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public boolean generateReeferReport(List unitList, String vesVoy)
    {
        try
        {
            inj = new GroovyInjectionBase();

            ArrayList reportUnitList =  new ArrayList();
            println("unitList.size()"+unitList.size());
            Iterator unitIterator = unitList.iterator();
            while(unitIterator.hasNext())
            {
                def unit = unitIterator.next();
                HashMap unitsDataMap = populateUnitData(unit);
                if(unitsDataMap != null) {
                    reportUnitList.add(unitsDataMap);
                }
            }

            println("reportUnitList -------------------- :"+ (reportUnitList != null ? reportUnitList.size() : "EMPTY"))
            //Set Report Parameter
            if (reportUnitList.size() > 0) {
                HashMap parameters = new HashMap();

                //Create and Mail Report
                JRDataSource ds = new JRMapCollectionDataSource(reportUnitList);

                HashMap reportDesignsmap = new HashMap();
                reportDesignsmap.put("REEFER REPORT",ds);

                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReports(reportDesignsmap,parameters, emailTo, vesVoy+" Reefer Report" ,"Reefer Report Attached");
                println("reportUnitList ------- Success")
            }
            else {
                def emailSender = inj.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailFrom,emailTo, vesVoy+" Reefer Report","No reefer units reported");
                println("reportUnitList ------- No data to print")
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }


//1.Maps unit Data to report file attribute
    public HashMap populateUnitData(Object unit)
    {
        HashMap map = null;
        try
        {


            String equipType =  unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");

            if (equipType.startsWith("R"))
            {

                Double tempRequiredC=null;
                if (unit.getUnitGoods().getGdsReeferRqmnts() != null)
                {
                    println("unit.getUnitGoods().getGdsReeferRqmnts():"+unit.getUnitGoods().getGdsReeferRqmnts())
                    tempRequiredC = unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC();
                }


                def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
                transitState = transitState != null ? transitState.getKey() : ''
                def tState = transitState.split("_")
                transitState = tState[1]

                Date ADate = unit.unitCreateTime;
                Date currDate = new Date();

                DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                formatter.setTimeZone(TimeZone.getTimeZone("HST"));

                String date = formatter.format(ADate).substring(0,10);
                String time = formatter.format(ADate).substring(11,19);

                String currHour = formatter.format(currDate).substring(11,19);

                String cmdtyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                String freightkind = unit.unitFreightKind.getKey();
                def ds = getDs(freightkind,cmdtyId);

                def _freightkind=unit.getFieldValue("unitFreightKind")
                def freightkindKey = _freightkind != null ? _freightkind.getKey() : ''

                def _category=unit.getFieldValue("unitCategory")
                def categoryKey = _category != null ? _category.getKey() : ''

                def _transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
                def transitStatekey = _transitState != null ? _transitState.getKey() : ''

                def lkpSlot=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
                lkpSlot = lkpSlot != null ? lkpSlot : ''

                def expGateBkgNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")

                def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
                lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''

                def gdsBlNbr = unit.getFieldValue("unitGoods.gdsBlNbr")

                def dir =  getDir(categoryKey,transitStatekey,freightkindKey,expGateBkgNbr,lkpLocType,gdsBlNbr)

                def Srv = getSrv(unit)
                def trade = processTrade(unit,Srv)

                map = new HashMap();

                map.put("UnitNbr", unit.getFieldValue("unitId"));
                map.put("TempRequiredInF", tempRequiredC); // TempRequiredInF
                map.put("LineOperator",unit.getFieldValue("unitLineOperator.bzuId"));//LineOperator
                map.put("UfvFlexString01", dir); // DIR
                map.put("TransitState", transitState); // Transit State
                map.put("PositionSlot", unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));//Position Slot
                map.put("POD",unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
                map.put("InboundCarrierId",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));//Inbound Vessel
                map.put("GoodsConsigneeName",unit.getFieldValue("unitGoods.gdsConsigneeAsString"))//Consignee
                map.put("UnitRemark",unit.getFieldValue("unitRemark"))//Unit Remarks
                map.put("UfvFlexString02",ds) // DS
                map.put("UfvFlexString03",unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId"))//Trucking Co
                map.put("UfvFlexString04",trade)//Trade
                map.put("EquipmentType",equipType)//EquipmentType
                map.put("EquipmentOwner",unit.unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId)//EquipmentOwner
                map.put("UfvFlexString05",unit.getFieldValue("unitRouting.rtgReturnToLocation"))//RET Port
                map.put("OutboundCarrierId", unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")); //Outbound Vessel
                map.put("GoodsShipperName",unit.getFieldValue("unitGoods.gdsShipperBzu.bzuName"))//Shipper Name
                map.put("UfvFlexString06","ADD")//ACTION
                map.put("UfvFlexString07",date)//
                map.put("UfvFlexString08",time)//
                map.put("UfvFlexString09",currHour)//

            }

        }catch(Exception e){
            println("Error iin the report")
            e.printStackTrace();
        }
        return map;
    }


    public List getReportUnits(String vesVoy)
    {

        try {
            println("Inside getReportUnits");
            def id = vesVoy;//"HUG294" // This will be removed when it call automatically after Manifest/Stow process
            //def id = "HUG294" // This will be removed when it call automatically after Manifest/Stow process
            VesVoy = id;
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID,id)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD_ID,"HON"));
            println(dq);
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    if(unit.getFieldValue("unitVisitState").equals(
                            com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE)) {
                        units.add(unit);
                    }
                }
            }
            println("unitsSize" + units.size);
            return units;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public List getUnits()
    {

        try {
            println("Inside getUnits");
            def id = "HZB323"//"HUG294" // This will be removed when it call automatically after Manifest/Stow process
            VesVoy = id;
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, id)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD_ID,"HON"));
            println(dq);
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    if(unit.getFieldValue("unitVisitState").equals(
                            com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE)) {
                        units.add(unit);
                    }
                }
            }
            println("unitsSize" + units.size);
            return units;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

}