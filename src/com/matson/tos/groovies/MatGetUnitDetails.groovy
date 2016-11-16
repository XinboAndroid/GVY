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
import com.navis.argo.ArgoRefField;
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

import com.navis.road.RoadField;
import com.navis.argo.business.reference.LineOperator;

import com.navis.argo.ArgoConfig;
import com.navis.argo.ArgoPropertyKeys;
import com.navis.argo.ArgoRefField;
import com.navis.argo.business.reference.AgentRepresentation;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.business.reference.Agent;
import com.navis.road.business.model.TruckingCompany
import java.lang.*;
import com.navis.argo.business.model.GeneralReference;
//import com.navis.framework.ulc.server.application.controller.form.ShowDeleteFormCommand;
import com.navis.argo.business.reference.Chassis;
import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.inventory.business.atoms.EqDamageSeverityEnum;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.argo.business.reference.Container;
import com.navis.argo.business.atoms.EquipMaterialEnum;


import com.navis.argo.business.reference.RoutingPoint;


import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.argo.business.model.Facility;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.Equipment
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import com.navis.argo.business.reports.DigitalAsset;
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.HashMap

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.LineOperator;

import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery
import com.navis.argo.ArgoField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.argo.business.atoms.FreightKindEnum;
import com.navis.services.business.rules.EventType;
import com.navis.services.business.event.Event;
import com.navis.services.business.api.EventManager
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.units.UnitEquipment

import com.navis.apex.business.model.GroovyInjectionBase;
import java.sql.ResultSet;
import java.sql.Connection;

import com.navis.argo.UserArgoField;
import com.navis.argo.business.security.ArgoUser;
import com.navis.security.SecurityField;

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.model.Yard
import com.navis.xpscache.yardmodel.api.*;
import com.navis.xpscache.yardmodel.impl.*;

import com.navis.yard.business.model.*;
import com.navis.spatial.business.api.IBinModel;
import com.navis.spatial.business.model.AbstractBin;
import com.navis.spatial.business.model.block.AbstractBlock;
import com.navis.spatial.business.model.block.BinModelHelper;

/*
* Author : Raghu Iyer
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatGetUnitDetails extends GroovyInjectionBase
{
    private final String emailTo = "1aktosdevteam@matson.com";
    private String outBoundCarrierId = null
    def inj = new GroovyInjectionBase();
    String VesVoy = null;
    private static final String XML_OVERRIDE = "\"";
    private static final String XML_END_ELEMENT = "/>";

    String editUser = "snx:-snx-";
    String notes = "FRUITS OR VEGETABLES, FRESH HON to HIL CNC CUS S-11/01/13";
    String holds = "";
    String updateHolds = "";
    String updatedNotes = ""
    Integer start = 0;
    def vesselForDischId = "MLI101";


    public boolean execute(Map params)
    {

        try
        {
            def binName = "A1111";
            println (binName.substring(0,3)+' '+binName.substring(3));
            println (binName.substring(0,2)+' '+binName.substring(2));

            /*XpsYardModel yardModel = new XpsYardModel("SI","Sand Island");
            println (" yardModel is ::::"+yardModel + " :: " +yardModel.getYardName());

            IXpsYardBin bin = yardModel.getBinFromSlot("A1211");
            println (" bin is ::::"+bin);*/

            def inFacility = com.navis.argo.ContextHelper.getThreadFacility()

            Yard inYard =  Yard.findYard("SI", inFacility)
            YardBinModel yardModel =  com.navis.yard.business.model.YardBinModel.findYardBinModelFromYardCodeAndOwner("SI", inYard)
            println (" yardModel is ::::"+yardModel);
            AbstractBin bin = yardModel.findDescendantBinFromInternalSlotString(binName, null);
            println (" bin is ::::"+bin);
            //println (" yardModel is ::::"+yardModel + " :: " +yardModel.getYardName());
            /*

            IYardModel yardModel1 = inYard.getYardModel();

            println (" yardModel1 is ::::"+yardModel1 + " :: " +yardModel1.getYardName());

            IYardBin bin1 = yardModel1.getBin(binName);
            println (" bin1 is ::::"+bin1);


            IYardModel yardModel = new YardModel("SI","Sand Island");
            println (" yardModel is ::::"+yardModel + " :: " +yardModel.getYardName());

            IYardBin bin = yardModel.getBin(binName);
            println (" bin is ::::"+bin);

            */

            //println (" bin is ::::"+block + " :: "+block.getBlockType());
            //IXpsYardBlock block = IXpsYardModel.getYardBlock("D12");

            /*def crsStatus = "SCRAP_METAL";

               def holdsApp = ''
               if(crsStatus.length() > 10){

                   int commaIndex = crsStatus.substring(0,11).lastIndexOf(' ');
                   if (commaIndex < 0) {
                       commaIndex = crsStatus.substring(0,11).lastIndexOf('_');
                   }
                   println("commaIndex :::  "+commaIndex);
                   holdsApp = crsStatus.substring(commaIndex+1,crsStatus.length());
                   crsStatus = crsStatus.substring(0,commaIndex);
                   println("holdsApp :::  "+holdsApp + " :: "+ crsStatus);
                    }*/

            //def temUnit = getActiveUnits1("TCLU8001357");
            //println("temUnit>>>>>>>>>>>"+temUnit);

            /*def userId = "riyer";
            println("UserId :: " + userId);
            def usrEmail = null;
            DomainQuery dq = QueryUtils.createDomainQuery("ArgoUser").addDqPredicate(PredicateFactory.eq(SecurityField.BUSER_UID, userId));
            println(dq);
            def user =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (user.size() > 0) {
                Iterator userIter = user.iterator();
                while ( userIter.hasNext()){
                    def usr = userIter.next();
                    usrEmail = usr.buserEMail;
                    println("usrEmail :::"+usrEmail);
                }
            }*/

            //getUnitForId("CAXU6972024");
            //Facility facility = Facility.findFacility("HON");
            //println("facility :::::::::::::"+facility.fcyJmsConnection.jmsProviderUrl);
            //println("Calling GvyRefDataLookup to get the JMS_URL from topology");
            //String tdpUrl = getGroovyClassInstance("GvyRefDataLookup").getN4TopicAddress(); //A6


            //Connection conn;
            //ResultSet rs = null;
            //HashMap reportMap = null;
            //def GvyRefDataLookup = inj.getGroovyClassInstance("GvyRefDataLookup");
            //conn = GvyRefDataLookup.connect();
            //rs = GvyRefDataLookup.lookupNv("MAU021",conn);

            /*println("Calling unitsByArrivalPosition");
            ArrayList<String> ctnr = new ArrayList<String>();
                ctnr.add(0,"MATU5514754");
            for (String ctr: ctnr)
            {
            def container = Container.findContainer(ctr);

            println(" container :::::::::::::::; "+container.eqMaterial);
            container.setEqMaterial(EquipMaterialEnum.STEEL);
            }*/
            //unitsByArrivalPosition();
            //def url = getGroovyClassInstance("GvyRefDataLookup").getN4TopicAddress();
            //println("<<<<<<<<<<<url>>>>>>>>>>"+url);
            //String wsdlurl = getGroovyClassInstance("GvyRefDataLookup").getChasRfidUrl(); //A6
            //println("<<<<<<<<<<<<<<< wsdlurl >>>>>>>>>>>>>>>"+wsdlurl);
            //getUnitForId();
            /*List ufvList = getActiveUnitsTest();
            //if(ufvList != null) {
                // Iterator iter = ufvList.iterator();
                // while(iter.hasNext()) {
                    //def ufv = iter.next();
                    //def unit = ufv.ufvUnit;
                    //def vesselService  =unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.cvdService.srvcId")
                    //println("<<<<vesselService>>>>>"+vesselService);
                    //if (vesselService.equals("KAH")){
                        //def damageNotes = unit.getUnitEquipDmgsItmNote()
                        //println("<<<<vesselService>>>>>"+unit.unitId+"<<>>"+vesselService+"<<>>"+damageNotes);
                    //}
                //}
            //  }		*/

            //getChassis();
/*				try
				{
				String inType = "CVDR";
				String inId1 = "PES318";
				String inId2 = "SUDU123456";
				String inId3 = "TEMP";
				String inValue1 = "10";
				String inValue2 = "20";
				String inValue3 = null;
				String inValue4 = null;
				String inValue5 = null;
				String inValue6 = null;

				GeneralReference generalReference = GeneralReference.findOrCreate(inType, inId1, inId2, inId3, inValue1, inValue2, inValue3, inValue4, inValue5, inValue6);

				inType = "CVDR";
				inId1 = "PES318";
				inId2 = "SUDU123456";
				inId3 = "GROSS WEIGHT";
				inValue1 = "2600";
				inValue2 = "2800";
				inValue3 = "2600";
				inValue4 = null;
				inValue5 = null;
				inValue6 = null;
				generalReference = GeneralReference.findOrCreate(inType, inId1, inId2, inId3, inValue1, inValue2, inValue3, inValue4, inValue5, inValue6);

            			List refList = null;
            			refList = getGenRef(inType, inId1);
            			println ("refList.size() ::: " + refList.size())

            			if (refList.size() > 0)
				{
					Iterator iterRefList = refList.iterator()
					while (iterRefList.hasNext())
					{
						def genRef = iterRefList.next();
						println(" :: genRef data :: "+ genRef.refGkey +":"+ genRef.refType +":"+ genRef.refId1 +":"+ genRef.refId2 +":"+ genRef.refId3 +":"+ genRef.refValue1 +":"+ genRef.refValue2 +":"+ genRef.refValue3);
					}
				}
*/

            /*	println("MatGetUnitDetails")

                List unitList = null;
                List shipperList = null;
                List agentList = null;

            start = notes.indexOf("CNC") + 4;
            updatedNotes = notes.substring(start)
            println("updatedNotes  "+updatedNotes)
            println("Index of CNc in str   "+ notes.indexOf("CNC"));
            start =  updatedNotes.indexOf(" ");
            println("Index of Space in str   "+ updatedNotes.indexOf(" "));



            if (editUser.equalsIgnoreCase("snx:-snx-")){
                if (notes.contains("ADD")){
                println("In ADD");
                    //updateHolds = "";
                }
                if (notes.contains("CNC")){
                println("In CNC");
                    updateHolds = updatedNotes.substring(0,start);
                    println("updateHolds :"+updateHolds);
                }
                }*/

            //getUnitsForUpdate()
            //getUnitListForBl();
            //getUnit();

            //def emailSender = inj.getGroovyClassInstance("EmailSender")
            //emailSender.custSendEmail("1aktosdevteam@matson.com","1aktosdevteam@matson.com",": Client Vessel Notification","Clent vessel completed, xml posted to MNS application");


            println("reportUnitList ------- Success")
        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }

    public void unitsByArrivalPosition(){
        String Id = "MLI101";
        println("<<<<TESTING DISCHARGE REPORT COUNTS ID>>>>"+Id);
        HashMap outputMap = null;
        List resultAcctList = new ArrayList();
        HashSet positionSet = new HashSet();
        List acctList = null;
        DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                .addDqPredicate(PredicateFactory.like(UnitField.UFV_ARRIVE_POS_LOC_TYPE, "VESSEL"))
                .addDqPredicate(PredicateFactory.like(UnitField.UFV_ARRIVE_POS_LOC_GKEY, "144267770"))
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S10_ADVISED))
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S99_RETIRED))
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S20_INBOUND))
                .addDqOrdering(Ordering.asc(UnitField.UFV_UNIT_ID));

        acctList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        println("Domain Query:::"+dq);
        println("<<<<ACCTLIST SIZE IS >>>>"+acctList.size());
        def checkUnit = "0000000000";
        if (acctList.size() > 0)
        {
            Iterator iterUnitList = acctList.iterator()
            while (iterUnitList.hasNext())
            {
                def ufv = iterUnitList.next();
                def unit = ufv.ufvUnit;
                def unitId = unit.unitId;
                if (unitId != checkUnit){
                    checkUnit = unitId;
                    //println("<<<unitId >>>"+unitId);
                    def EquipmentTypeClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass").getKey();
                    def transitState = ufv.ufvTransitState;
                    def transitStateKey = null;
                    def equipType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");

                    if (transitState != null) {
                        transitStateKey = transitState != null ? transitState.getKey() : ''
                        def tState = transitStateKey.split("_")
                        transitStateKey = tState[1]
                    }
                    if ((transitStateKey.equalsIgnoreCase("ECOUT") && EquipmentTypeClass.equalsIgnoreCase("CHASSIS")) || equipType.startsWith("MG")){
                        log(unit.unitId+"::"+transitStateKey+"::::"+EquipmentTypeClass +"::"+equipType);
                        null;
                    }
                    else {
                        outputMap = populateAcctListByType(ufv)
                        resultAcctList.add(outputMap)
                    }
                }

            }
            println ("resultAcctList.size  "+resultAcctList.size);
            JRDataSource ds = new JRMapCollectionDataSource(resultAcctList);
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");

            //Set report parameters
            HashMap parameters = new HashMap();
            parameters.put("outboundVesVoy","MLI101");
            parameters.put("Date",new Date());
            // call report design of rehandle containers not loaded back to vessel report.
            def reportDesignName = "ACCT AUDIT DISCH REPORT";

            // Emailing report
            reportRunner.emailExcelReport(ds, parameters,reportDesignName ,"1aktosdevteam@matson.com","ACCT AUDIT DISCH REPORT - After","ACCT AUDIT DISCH REPORT");

        }


    }


    public HashMap populateAcctListByType(UnitFacilityVisit ufv) {

        HashMap resMap = new HashMap();
        def unit = ufv.ufvUnit
        UnitEquipment chasEquip = unit.getUnitCarriageUe();
        def freightkindout = unit.getFieldValue("unitFreightKind").getKey();
        def attachedUnit = unit.getFieldValue("unitAttachedEquipIds")
        //println("chasEquip   "+chasEquip)
        if ("FCL".equals(freightkindout))
        {
            freightkindout = "F";
        } else if ("MTY".equals(freightkindout))
        {
            freightkindout = "E";
        }

        resMap.put("Commodity",unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"));
        resMap.put("EquipmentTypeClass",unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass").getKey());
        resMap.put("EquipmentType",unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId"))
        resMap.put("FreightKind",freightkindout)
        resMap.put("UnitNbr",unit.getFieldValue("unitId"))
        if (attachedUnit != null && chasEquip !=null)
        {
            resMap.put("AttachedUnits",unit.getFieldValue("unitAttachedEquipIds"))
        } else if (attachedUnit != null && chasEquip == null)
        {
            resMap.put("AttachedUnits",null)
        }

        resMap.put("InboundCarrierATA",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvATA"))
        resMap.put("InboundCarrierATD",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvATD"))
        resMap.put("OPL",unit.getFieldValue("unitRouting.rtgOPL.pointId"))
        //resMap.put("InboundCarrierId",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"))
        resMap.put("InboundCarrierId",vesselForDischId)
        resMap.put("UfvFlexString06",unit.getFieldValue("unitActiveUfv.ufvFlexString06"))

        //println("Result map is "+resMap);
        return resMap;
    }

    public void getUnitForId(String containerId)
    {

        try {
            println("Inside getUnit");
            inj = new GroovyInjectionBase();
            ArrayList units = new ArrayList();
            //String containerId = "MATU2496256";
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,containerId));
            dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S40_YARD))
            println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());
            if (unitList.size() > 0)
            {
                def trucker = null;
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    def ufv = iterUnitList.next();
                    def unit = ufv.ufvUnit;
                    def consignee = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");
                    def shipper = unit.getFieldValue("unitGoods.gdsShipperBzu.bzuName");
                    String gdsWeight = new BigDecimal(unit.getUnitGoodsAndCtrWtKg()).setScale(2, BigDecimal.ROUND_HALF_UP).toString();
                    String gdsWeight1 = unit.getUnitGoodsAndCtrWtKg().toString();
                    println("<<<consignee >>>"+consignee + " <<<shipper>>>>"+shipper +" gdsWeight "+ gdsWeight + "::"+gdsWeight1);
                }
            }
        }catch (Exception e){
            println("Error :" + e);
        }
    }

    public List getActiveUnitsTest()
    {

        Date startDate = new Date() - 302;
        String trimDate = startDate.format('yyyy-MM-dd')
        startDate = startDate.parse('yyyy-MM-dd', trimDate);
        Date endDate = startDate +  301;
        println("startDate "+ startDate);
        println("endDate "+ endDate);

        try {
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq = dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_PRIMARY_EQ_DAMAGE_SEVERITY,EqDamageSeverityEnum.MAJOR,EqDamageSeverityEnum.MINOR))
                    .addDqPredicate(PredicateFactory.in(UnitField.UFV_VISIT_STATE,"3DEPARTED"))
                    .addDqPredicate(PredicateFactory.ge(UnitField.UFV_TIME_OUT,startDate))
                    .addDqPredicate(PredicateFactory.le(UnitField.UFV_TIME_OUT,endDate));
            println(dq);
            List ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            /* if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                   def vesselService  =unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.cvdService.srvcId")
                   println("<<<<vesselService>>>>>"+vesselService);
                   if (vesselService.equals("GCS")){
                       units.add(unit);
                       println("<<<<vesselService>>>>>"+unit.unitId+"<<>>"+vesselService);
                       try{
                       def damageNotes = unit.getUnitEquipDmgsItmNote()
                       println("<<<<damageNotes>>>>>"+unit.unitId+"<<>>"+damageNotes);
                       }catch (Exception e){
                           println("error while getting damage description");
                       }
                   }
               }
             }
             println("unitsSize" + units.size);*/
            return ufvList;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public  void getChassis()
    {
        try {
            println("Inside getUnit");
            List ufvGateUnits = null;
            DomainQuery dq = QueryUtils.createDomainQuery("Chassis");
            dq.addDqPredicate(PredicateFactory.le(ArgoRefField.EQ_TARE_WEIGHT_KG, 0))
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.EQ_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE))
            println("getUnit "+dq);
            def chList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+chList.size());
            if (chList.size() > 0)
            {
                Iterator iterChList = chList.iterator()
                while (iterChList.hasNext())
                {
                    def chassis = iterChList.next();
                    println(chassis.eqIdFull+"|"+chassis.eqEquipType.eqtypId+"|"+chassis.eqTareWeightKg)
                }
            }
            println("After unitList"+chList.size());
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }


    public  void getUnitCntForTagId()
    {
        try {
            println("Inside getUnit");
            List ufvGateUnits = null;
            String tagId = "Start";
            DomainQuery dq = QueryUtils.createDomainQuery("Unit");
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE, "1ACTIVE"))
                    .addDqPredicate(PredicateFactory.isNotNull(InventoryField.UNIT_FLEX_STRING15))
            //.addDqPredicate(PredicateFactory.in(InventoryField.UNIT_FLEX_STRING15,"33717937","33807230"))
                    .addDqOrdering(Ordering.desc(InventoryField.UNIT_FLEX_STRING15))
                    .addDqOrdering(Ordering.desc(InventoryField.UNIT_CREATE_TIME));
            def unit = null;
            println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());
            if (unitList.size() > 0)
            {
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    unit = iterUnitList.next();
                    if (tagId == unit.unitFlexString15){
                        println("Update TagId as NULL for unit number :: "+ unit.unitId);
                    }
                    else {
                        println("Update TagId as "+unit.unitFlexString15+" for unit number :: "+ unit.unitId);
                    }
                    tagId = unit.unitFlexString15;
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public List getGenRef(String inType, String inId1)
    {
        DomainQuery dq = QueryUtils.createDomainQuery("GeneralReference").addDqPredicate(PredicateFactory.eq(ArgoField.REF_TYPE, inType)).addDqPredicate(PredicateFactory.eq(ArgoField.REF_ID1, inId1));
        println(dq);
        List genRef = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        println ("genRef.size() ::: " + genRef.size())
        return (genRef);
    }

    public  getUnitCnt()
    {

        try {
            println("Inside getUnit");

            Long lineOpGkey = LineOperator.findLineOperatorById("MAT").bzuGkey

            List ufvGateUnits = null;

            DomainQuery dq = QueryUtils.createDomainQuery("Unit");
            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_LINE_OPERATOR,lineOpGkey)).addDqPredicate(PredicateFactory.le(InventoryField.UNIT_FREIGHT_KIND,"MTY"));

            def unit = null;

            println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());
            if (unitList.size() > 0)
            {
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    unit = iterUnitList.next();
                }
            }

            //return unit;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public void getUnit()
    {

        try {
            println("Inside getUnit");
            inj = new GroovyInjectionBase();
            String trucker = null;
            String agentListGkey = null;
            ArrayList units = new ArrayList();
            String containerId = "FCIU3316760";
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            //dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID,"ALE252A"));
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,containerId));
            /*dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE,"S70_DEPARTED"))
            dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_CATEGORY,"STRGE"))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_GDS_DESTINATION,"MOL")); */
            println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());
            if (unitList.size() > 0)
            {
                trucker = null;
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    def unit = iterUnitList.next();
                    println("Here "+unit);
                    println("UnitId " + unit.ufvUnit.unitId);
                    println("unitRemark " + unit.ufvUnit.unitRemark);
                    trucker = unit.ufvUnit.unitRemark;
                    if (trucker.contains("\""))
                    {
                        trucker = trucker.replaceAll("\"", "\"\"");
                        println("trucker " +trucker);
                    }


                    /*if (unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId") != null)
                    {
                        def updateTrucker = inj.getGroovyClassInstance("MatUpdateTrucker");
                        trucker = updateTrucker.getTrucker(unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId"));
                        println("trucker "+trucker);
                        if (trucker != null)
                            {
                            def truckerName = TruckingCompany.findTruckingCompany(trucker)
                            println("truckerName " + truckerName)
                            println("Before Trucking " + unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId"))
                            unit.setFieldValue("unitRouting.rtgTruckingCompany", truckerName);
                            }
                    }
                    println("After Trucking " + unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId"))*/

                    println("UnitId " + unit.getFieldValue("unitId"));
                    //println("Equipment Class " + unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass").getKey())
                    //println("VesVoy " + unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));
                    //println("UnitClass " +unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass"));
                    //println("Destination " + unit.getFieldValue("unitGoods.gdsDestination"));
                    //println("Booking Number "+unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr"))
                    //println("Create Date "+ unit.unitCreateTime);
                }
            }

        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public void getUnitTran()
    {

        try {
            println("Inside getUnit");
            ArrayList units = new ArrayList();
            String containerId = "TOLU8794418";

            Date date = new Date();
            println(" Inside DB Query " + date);

            List ufvYbUnits = null;

            String formattedDate = date.format('MM/dd/yyyy');

            String startDateHST = formattedDate +" "+"00:00:00 HST";//2012-07-19 00:00:00 HST";
            String endDateHST = formattedDate +" "+"23:59:59 HST";//"2012-07-19 23:59:59 HST";

            Date startDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(startDateHST);
            println ("PDT Start date time " + startDate);
            Date endDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(endDateHST);
            println ("PDT End date time " + endDate);


            DomainQuery dq = QueryUtils.createDomainQuery("TruckTransaction").addDqPredicate(PredicateFactory.ge(RoadField.TRAN_CREATED, startDate)).addDqPredicate(PredicateFactory.le(RoadField.TRAN_CREATED, endDate)).addDqPredicate(PredicateFactory.eq(RoadField.TRAN_LINE_ID, "MEA"));
            println("dq---------------"+dq);
            ufvYbUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("Query executed");
            println("unitUfvYB ::"+ufvYbUnits != null ? ufvYbUnits.size() : 0)

        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    private List getShipper()
    {
        DomainQuery dq = QueryUtils.createDomainQuery("Shipper").addDqPredicate(PredicateFactory.like(ArgoRefField.BZU_NAME, "Test Shipper"));

        //DomainQuery dq = QueryUtils.createDomainQuery("Shipper").addDqPredicate(PredicateFactory.isNotNull(ArgoRefField.BZU_NOTES));

        println (dq)
        List shippers = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        println ("shippers.size()" + shippers.size())
        if (shippers.size() <= 0)
            return null;

        return (shippers);
    }


    public List getAgentById(String inName)
    {
        DomainQuery dq = QueryUtils.createDomainQuery("Agent").addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ID, inName));
        println(dq);
        List agent = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        println ("agent.size()" + agent.size())
        return (agent);
    }

    public void getCmcYard()
    {

        try {
            println("Inside getCmcYard");

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.in(UnitField.UNIT_GDS_BL_NBR,"AKL121210858"))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE,"S70_DEPARTED"))
            dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_CATEGORY,"STRGE"))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_GDS_DESTINATION,"MOL","NAW"))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_FREIGHT_KIND,"FCL"));
            println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());


        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }


    public getActiveUnits(String unitNbr)
    {

        try {
            ArrayList units = new ArrayList();
            //DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            //dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID,vesVoy));
            //println(dq);
            Long facilityGkey = ContextHelper.getThreadFacility().getFcyGkey();
            println("ContextHelper.getThreadFacility().getFcyGkey() : "+ContextHelper.getThreadFacility().getFcyGkey())
            DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID,unitNbr))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, facilityGkey));
            println("dq:::::::::"+dq)

            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def unit = iter.next();
                    //if(unit.getFieldValue("unitVisitState").equals(
                    //com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE)) {
                    // units.add(unit);
                    UnitVisitStateEnum visitState = unit.getUnitVisitState();
                    println(visitState.getKey());
                    println(" IbCv "+ unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")+" ObCv : "+unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")+" LastFreeDay : "+unit.getFieldValue("unitActiveUfv.ufvCalculatedLastFreeDay")+" Date2 : "+unit.getFieldValue("unitActiveUfv.ufvFlexDate02")+" Date3 : "+unit.getFieldValue("unitActiveUfv.ufvFlexDate03"));
                    //}
                }
            }
            println("unitsSize" + units.size);
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public List getActiveUnits1(String unitNbr)
    {

        try {
            ArrayList units = new ArrayList();
            Long facilityGkey = ContextHelper.getThreadFacility().getFcyGkey();
            println("ContextHelper.getThreadFacility().getFcyGkey() : "+ContextHelper.getThreadFacility().getFcyGkey())
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, facilityGkey))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, unitNbr));
            println(dq);
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                println(ufvList)
                println(ufvList.ufvGkey)
                println(ufvList.ufvCalculatedLastFreeDay)
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    def fcy = com.navis.argo.ContextHelper.getThreadFacility();
                    def ufv1 = unit.getUfvForFacilityCompletedOnly(fcy);
                    if(unit.getFieldValue("unitVisitState").equals(
                            com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE)) {
                        //def lastfreeDayStr = ufv1.getFieldValue("ufvCalculatedLastFreeDay");
                        //println("lastfreeDayStr"+lastfreeDayStr +":"+ufv1.getFieldValue("ufvFlexDate02")+":"+ufv1.getFieldValue("ufvFlexDate03"))
                        //println("ufvCalculatedLastFreeDay <<<<<<<<<<>>>>>>>>>>>>>>>>> " +unit.getFieldValue("unitActiveUfv.ufvCalculatedLastFreeDay")+":"+unit.getFieldValue("unitActiveUfv.ufvTimeOfLastMove"));

                        units.add(unit);
                        break;
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


// Added by Raghu Iyer on 11/08/2012 to update the BL Number as blank where "DO NOT EDIT ....."
    private void getUnitListForBl(){


        String  blNumber = "TEST123456";
        String	blDest = "HON";
        String  blLinrOptr = "APL";
        String	blPod1 = "HIL";
        String  blPod2 = "KHI";
        String  blPol = "HIL";
        String  blOrgn = "MEL";
        String ownerCode = null;
        if (blLinrOptr != null)
        {
            ownerCode = blLinrOptr+"U";
        }

        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
        //.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_GDS_BL_NBR,blNumber));
                .addDqPredicate(PredicateFactory.in(UnitField.UNIT_ID, "MATU2510061"));
        println("dq:::::::::"+dq)
        HibernateApi hibernate = HibernateApi.getInstance();
        List unitList  = hibernate.findEntitiesByDomainQuery(dq);
        Iterator iterUnitList = unitList.iterator()
        while (iterUnitList.hasNext())
        {
            def unit = iterUnitList.next();
            println("Before Update UnitId:::"+unit.unitId);
            println("POL:"+ unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            println("POD1:"+unit.getFieldValue("unitRouting.rtgPOD1.pointId"))
            println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"))
            println("Orogin:"+unit.getFieldValue("unitGoods.gdsOrigin"))
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("equiOptr:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOperator.bzuId"))
            println("equiOwner:"+unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId"))
            println("Carr Owner:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOwner.bzuId"))


            def operator = LineOperator.findLineOperatorById("APL");
            println("<<<<<<<<<<<<<<>>>>>>>>>>>>>>"+operator)
            unit.setUnitLineOperator(operator);

            def routing = unit.getUnitRouting();

            routing.setRtgPOL(RoutingPoint.findRoutingPoint(blPol));
            routing.setRtgPOD1(RoutingPoint.findRoutingPoint(blPod1));
            routing.setRtgPOD2(RoutingPoint.findRoutingPoint(blPod2));
            unit.setFieldValue("unitGoods.gdsDestination",blDest);
            unit.setFieldValue("unitGoods.gdsOrigin",blOrgn);
            unit.setUnitFlexString13(ownerCode);

            println("After Update UnitId:::"+unit.unitId);
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("POL:"+ unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            println("POD1:"+unit.getFieldValue("unitRouting.rtgPOD1.pointId"))
            println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"))
            println("Orogin:"+unit.getFieldValue("unitGoods.gdsOrigin"))

            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("equiOptr:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOperator.bzuId"))
            println("equiOwner:"+unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId"))
            println("Carr Owner:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOwner.bzuId"))
        }
    }

    private void getUnitHazard(){


        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.in(UnitField.UNIT_ID, "MATU2080356"));
        println("dq:::::::::"+dq)
        HibernateApi hibernate = HibernateApi.getInstance();
        List unitList  = hibernate.findEntitiesByDomainQuery(dq);
        Iterator iterUnitList = unitList.iterator()
        while (iterUnitList.hasNext())
        {

            def unit = iterUnitList.next();
            println("Hazard <<<<<<<<<<<>>>>>>>>>>>>>"+unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId") +" "+unit.getFieldValue("unitGoods.gdsIsHazardous") );

            if (unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId") == "KAU714")
            {


                String drayStatus = unit.getFieldValue("unitDrayStatus");
                String requiredPower = unit.getFieldValue("unitRequiresPower") ? "Y" : "N";
                println("unitDrayStatus:"+drayStatus)
                println("unitRequiresPower:"+requiredPower)

                println("unitSpecialStow:"+ unit.getFieldValue("unitSpecialStow"));
                def unitDeckRqmnt  = unit.getFieldValue("unitDeckRqmnt");
                unitDeckRqmnt = unitDeckRqmnt.getKey();
                println("unitDeckRqmnt:"+ unitDeckRqmnt);

                String etcAttrOpn = "<unit-etc";
                String etcString = etcAttrOpn;
                if (requiredPower !=null)
                {
                    etcString = etcString + " requires-power=" + XML_OVERRIDE + requiredPower + XML_OVERRIDE;
                }
                if (drayStatus !=null)
                {
                    etcString = etcString + " dray-status=" + XML_OVERRIDE + drayStatus + XML_OVERRIDE;
                }
                if (drayStatus != null || requiredPower == "Y")
                {
                    etcString = etcString + XML_END_ELEMENT;
                }

                println("unitIsOog <<>>>"+unit.getFieldValue("unitIsOog"));
                if (unit.getFieldValue("unitIsOog"))
                {
                    String backCm = unit.getFieldValue("unitOogBackCm");
                    String frontCm = unit.getFieldValue("unitOogFrontCm");
                    String leftCm = unit.getFieldValue("unitOogLeftCm");
                    String rightCm = unit.getFieldValue("unitOogRightCm");
                    String topCm = unit.getFieldValue("unitOogTopCm");

                    String oogAttrOpn = "<oog";
                    String oogString = oogAttrOpn
                    if (rightCm !=null)
                    {
                        oogString = oogString + " right-cm=" + XML_OVERRIDE + rightCm + XML_OVERRIDE;
                    }
                    if (leftCm !=null)
                    {
                        oogString = oogString + " left-cm=" + XML_OVERRIDE + leftCm + XML_OVERRIDE;
                    }
                    if (backCm !=null)
                    {
                        oogString = oogString + " back-cm=" + XML_OVERRIDE + backCm + XML_OVERRIDE;
                    }
                    if (frontCm !=null)
                    {
                        oogString = oogString + " front-cm=" + XML_OVERRIDE + frontCm + XML_OVERRIDE;
                    }

                    if (topCm !=null)
                    {
                        oogString = oogString + " top-cm=" + XML_OVERRIDE + topCm + XML_OVERRIDE;
                    }
                    oogString = oogString + XML_END_ELEMENT;
                }

                println(unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"))
                def ufv = unit.getUnitActiveUfvNowActive();

                String timeIn = ufv.ufvTimeIn;
                String timeOut = ufv.ufvTimeOut;
                String timeLoad = ufv.ufvTimeOfLoading;

                String timeStampAttrOpn = "<timestamps";
                String timeStampString = timeStampAttrOpn;
                if (timeIn !=null)
                {
                    timeStampString = timeStampString + " time-in=" + XML_OVERRIDE + timeIn + XML_OVERRIDE;
                }
                if (timeOut !=null)
                {
                    timeStampString = timeStampString + " time-out=" + XML_OVERRIDE + timeOut + XML_OVERRIDE;
                }
                if (timeLoad !=null)
                {
                    timeStampString = timeStampString + " time-load=" + XML_OVERRIDE + timeLoad + XML_OVERRIDE;
                }
                if (timeIn != null || timeOut != null || timeLoad != null)
                {
                    timeStampString = timeStampString + XML_END_ELEMENT;
                }

                println("Timestamp "+timeStampString);

                println("Hazard <<<<<<<<<<<>>>>>>>>>>>>>"+unit.getFieldValue("gdsIsHazardous"));

                if (ufv.ufvUnit.getUnitGoods() != null)
                {
                    def haz = ufv.ufvUnit.getUnitGoods().getGdsHazards();
                    println("hazard<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>"+haz)

                    if (haz != null)
                    {
                        String hazardsAttrOpn = "<hazards>";
                        String addtag = "N";
                        def hazardAttrOpn = "<hazard"
                        def hazardString = "";
                        Iterator hazardIter = ufv.ufvUnit.getUnitGoods().getGdsHazards().getHazardItemsIterator();
                        while(hazardIter.hasNext()) {
                            def hazard = hazardIter.next();
                            def imdgClass = hazard.getHzrdiImdgCode() != null ? hazard.getHzrdiImdgCode().getKey() : null
                            def nbrType = hazard.getHzrdiNbrType() != null ? hazard.getHzrdiNbrType().getKey() : null
                            def qty = hazard.hzrdiQuantity;
                            def packageType = hazard.hzrdiPackageType;
                            def weight = hazard.hzrdiWeight;
                            String properName = hazard.hzrdiProperName;
                            String techName   = hazard.hzrdiTechName;
                            String imdgclass  = hazard.hzrdiImdgClass.name;
                            def im01 = hazard.hzrdiSecondaryIMO1;
                            def im02 = hazard.hzrdiSecondaryIMO2;
                            String un  = hazard.hzrdiUNnum;
                            def pkg = hazard.hzrdiPackingGroup;
                            def flashPoint = hazard.hzrdiFlashPoint;
                            def limited = hazard.hzrdiLtdQty ? "Y" : "N";
                            def marine = hazard.hzrdiMarinePollutants ? "Y" : "N";
                            def phone = hazard.hzrdiEmergencyTelephone;
                            def hzrdiNotes = hazard.hzrdiNotes;
                            def hzrdiInhalationZone = hazard.hzrdiInhalationZone;
                            def hzrdiPageNumber = hazard.hzrdiPageNumber;
                            def hzrdiEMSNumber = hazard.hzrdiEMSNumber;
                            def hzrdiMFAG = hazard.hzrdiMFAG;
                            def hzrdiHazIdUpper = hazard.hzrdiHazIdUpper;
                            def hzrdiSubstanceLower = hazard.hzrdiSubstanceLower;
                            def hzrdiPlannerRef = hazard.hzrdiPlannerRef;
                            def hzrdiMoveMethod = hazard.hzrdiMoveMethod;
                            def hzrdiExplosiveClass = hazard.hzrdiExplosiveClass;
                            def hzrdiDcLgRef = hazard.hzrdiDcLgRef;
                            def hzrdiDeckRestrictions = hazard.hzrdiDeckRestrictions;

                            println("Hazard Details <<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>> "+imdgClass+":"+un+":"+nbrType+":"+limited+":"+packageType+":"+flashPoint+":"+techName+":"+weight+":"+qty+":"+im01+":"+im02+":"+marine+":"+properName+":"+pkg+":"+phone+":"+hzrdiNotes)

                            hazardString = hazardString + hazardAttrOpn;
                            if (nbrType != null)
                            {
                                hazardString = hazardString + " haz-nbr-type=" + XML_OVERRIDE + nbrType + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiNotes != null)
                            {
                                hzrdiNotes = this.stripInvalidXmlCharacters(hzrdiNotes);
                                hzrdiNotes = this.removeDoubleQuotes(hzrdiNotes);
                                hazardString = hazardString + " notes=" +XML_OVERRIDE + hzrdiNotes + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (phone != null)
                            {
                                hazardString = hazardString + " emergency-telephone=" +XML_OVERRIDE + phone + XML_OVERRIDE;
                                addtag = "Y";
                            }
                            println("marine <><><><>:"+marine)
                            if (marine == "Y")
                            {
                                hazardString = hazardString + " marine-pollutants=" +XML_OVERRIDE + marine + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (im01 != null)
                            {
                                hazardString = hazardString + " secondary-imo-1=" +XML_OVERRIDE + im01 + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (im02 != null)
                            {
                                hazardString = hazardString + " secondary-imo-2=" +XML_OVERRIDE + im02 + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (qty != null)
                            {
                                hazardString = hazardString + " quantity=" +XML_OVERRIDE + qty + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (weight != null)
                            {
                                hazardString = hazardString + " weight-kg=" +XML_OVERRIDE + weight + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (pkg != null)
                            {
                                hazardString = hazardString + " packing-group=" +XML_OVERRIDE + pkg + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (properName != null)
                            {
                                properName = this.stripInvalidXmlCharacters(properName);
                                properName = this.removeDoubleQuotes(properName);
                                hazardString = hazardString + " proper-name=" +XML_OVERRIDE + properName + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (techName != null)
                            {
                                techName = this.stripInvalidXmlCharacters(techName);
                                techName = this.removeDoubleQuotes(techName);
                                hazardString = hazardString + " tech-name=" +XML_OVERRIDE + techName + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (flashPoint != null)
                            {
                                hazardString = hazardString + " flash-point=" +XML_OVERRIDE + flashPoint + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (packageType != null)
                            {
                                hazardString = hazardString + " package-type=" +XML_OVERRIDE + packageType + XML_OVERRIDE;
                                addtag = "Y";
                            }
                            println("limited <><><><>:"+limited)
                            if (limited == "Y")
                            {
                                hazardString = hazardString + " ltd-qty-flag=" +XML_OVERRIDE + limited + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (un != null)
                            {
                                hazardString = hazardString + " un=" +XML_OVERRIDE + un + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (imdgClass != null)
                            {
                                hazardString = hazardString + " imdg=" +XML_OVERRIDE + imdgClass + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiInhalationZone != null)
                            {
                                hazardString = hazardString + " inhalation-zone=" +XML_OVERRIDE + hzrdiInhalationZone + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiExplosiveClass != null)
                            {
                                hazardString = hazardString + " explosive-class=" +XML_OVERRIDE + hzrdiExplosiveClass + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiEMSNumber != null)
                            {
                                hazardString = hazardString + " ems-nbr=" +XML_OVERRIDE + hzrdiEMSNumber + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiMFAG != null)
                            {
                                hazardString = hazardString + " mfag=" +XML_OVERRIDE + hzrdiMFAG + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiHazIdUpper != null)
                            {
                                hazardString = hazardString + " haz-id-upper=" +XML_OVERRIDE + hzrdiHazIdUpper + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiSubstanceLower != null)
                            {
                                hazardString = hazardString + " substance-lower=" +XML_OVERRIDE + hzrdiSubstanceLower + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiPlannerRef != null)
                            {
                                hazardString = hazardString + " planner-ref=" +XML_OVERRIDE + hzrdiPlannerRef + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiMoveMethod != null)
                            {
                                hazardString = hazardString + " move-method=" +XML_OVERRIDE + hzrdiMoveMethod + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiDeckRestrictions != null)
                            {
                                hazardString = hazardString + " deck-restrictions=" +XML_OVERRIDE + hzrdiDeckRestrictions + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiDcLgRef != null)
                            {
                                hazardString = hazardString + " dc-lg-ref=" +XML_OVERRIDE + hzrdiDcLgRef + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (hzrdiPageNumber != null)
                            {
                                hazardString = hazardString + " page-number=" +XML_OVERRIDE + hzrdiPageNumber + XML_OVERRIDE;
                                addtag = "Y";
                            }

                            if (addtag == "Y")
                            {
                                hazardString = hazardString + XML_END_ELEMENT;
                            }


                            println("hazardString <<<<<<<<<<>>>>>>>>>>>"+hazardString);

                        }
                        if (addtag == "Y")
                        {
                            hazardsAttrOpn = hazardsAttrOpn + hazardString + "</hazards>";
                            println("hazardsAttrOpn <<<<<<<<<<>>>>>>>>>>>"+hazardsAttrOpn);
                        }
                    }
                }
            }


        }
    }

    public String stripInvalidXmlCharacters(String input){
        String specialCharacter = "&";
        if (input.contains(specialCharacter)) {
            input = input.replaceAll("&", "&amp; ");
            //LOGGER.warn("sb:" + input);
        }
        return input;
    }

    public String removeDoubleQuotes(String input){
        String specialCharacter = "&";
        if (input.contains("\"")) {
            input = input.replaceAll("\"", "&quot;");
        }
        if (input.contains("<")) {
            input = input.replaceAll("<", "&lt; ");
        }
        if (input.contains(">")) {
            input = input.replaceAll(">", "&gt; ");
        }
        return input;
    }

    private void getUnitReefer(){


        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.in(UnitField.UNIT_ID, "MATU2080356"));
        println("dq:::::::::"+dq)
        HibernateApi hibernate = HibernateApi.getInstance();
        List unitList  = hibernate.findEntitiesByDomainQuery(dq);
        Iterator iterUnitList = unitList.iterator()
        while (iterUnitList.hasNext())
        {

            def unit = iterUnitList.next();
            def addtag = "N";
            if (unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId") == "RJP379")
            {
                println(unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));
                def tempReq = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempRequiredC");
                def tempMax = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempLimitMaxC");
                def tempMin = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempLimitMinC");
                def tempTM1 = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTimeMonitor1");
                def tempTM2 = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTimeMonitor2");
                def tempTM3 = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTimeMonitor3");
                def tempTM4 = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTimeMonitor4");
                def tempPwrOnTime = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqLatestOnPowerTime");
                def tempCo2Pct = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqCO2Pct");
                def tempHmdtyPct = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqHumidityPct");
                def tempO2Pct = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqO2Pct");
                def tempVentReq = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqVentRequired");
                def tempVentUnit = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqVentUnit");
                def tempShowFahrenhiet = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempShowFahrenheit");

                tempShowFahrenhiet = tempShowFahrenhiet ? "Y" : "N";

                String reeferAttrOpn = "<reefer";
                String reeferString = reeferAttrOpn;
                if (tempShowFahrenhiet == "Y")
                {
                    reeferString = reeferString + " temp-display-unit=" + XML_OVERRIDE + tempShowFahrenhiet + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempReq !=null)
                {
                    reeferString = reeferString + " temp-reqd-c=" + XML_OVERRIDE + tempReq + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempO2Pct !=null)
                {
                    reeferString = reeferString + " o2-pct=" + XML_OVERRIDE + tempO2Pct + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempCo2Pct !=null)
                {
                    reeferString = reeferString + " co2-pct=" + XML_OVERRIDE + tempCo2Pct + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempHmdtyPct !=null)
                {
                    reeferString = reeferString + " humidity-pct=" + XML_OVERRIDE + tempHmdtyPct + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempVentReq !=null)
                {
                    reeferString = reeferString + " vent-required-value=" + XML_OVERRIDE + tempVentReq + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempVentUnit !=null)
                {
                    reeferString = reeferString + " vent-required-unit=" + XML_OVERRIDE + tempVentUnit + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempMin !=null)
                {
                    reeferString = reeferString + " temp-min-c=" + XML_OVERRIDE + tempMin + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempMax !=null)
                {
                    reeferString = reeferString + " temp-max-c=" + XML_OVERRIDE + tempMax + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempPwrOnTime !=null)
                {
                    reeferString = reeferString + " time-latest-on-power=" + XML_OVERRIDE + tempPwrOnTime + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempTM1 !=null)
                {
                    reeferString = reeferString + " time-monitor-1=" + XML_OVERRIDE + tempTM1 + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempTM2 !=null)
                {
                    reeferString = reeferString + " time-monitor-2=" + XML_OVERRIDE + tempTM2 + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempTM3 !=null)
                {
                    reeferString = reeferString + " time-monitor-3=" + XML_OVERRIDE + tempTM3 + XML_OVERRIDE;
                    addtag = "Y";
                }
                if (tempTM4 !=null)
                {
                    reeferString = reeferString + " time-monitor-4=" + XML_OVERRIDE + tempTM4 + XML_OVERRIDE;
                    addtag = "Y";
                }

                if (addtag == "Y")
                {
                    reeferString = reeferString + XML_END_ELEMENT;
                }

                println("reeferString "+reeferString)

                println("Temp details <<<<<>>>>>>"+tempReq+":"+tempMax+":"+tempMin+":"+tempTM1+":"+tempTM2+":"+tempTM3+":"+tempTM4+":"+tempPwrOnTime+":"+tempCo2Pct+":"+tempHmdtyPct+":"+tempVentReq+":"+tempVentUnit+":"+tempShowFahrenhiet);
            }
        }
    }

    //private void getUnitsForUpdate(String blNbr, String blConsignee, String blConsigneeId ){
    private void getUnitsForUpdate(){
        List unitList = null;
        def unitShipper = "";
        def unitConsignee = "";
        def unitShipperId = "";
        def unitConsigneeId = "";
        try {

            DomainQuery dq = QueryUtils.createDomainQuery("Unit")
            //.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_GDS_BL_NBR,blNbr))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID,"ECMU1463727"));

            HibernateApi hibernate = HibernateApi.getInstance();
            unitList  = hibernate.findEntitiesByDomainQuery(dq);

            Iterator iterUnitList = unitList.iterator()
            while (iterUnitList.hasNext())
            {
                unitShipper = "";
                unitConsignee = "";
                unitShipperId = "";
                unitConsigneeId = "";

                def unit = iterUnitList.next();

                if ( unit.unitGoods != null)
                {

                    unitConsigneeId = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId");
                    unitConsignee = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");
                    unitShipperId = unit.getFieldValue("unitGoods.gdsShipperBzu.bzuId");
                    unitShipper = unit.getFieldValue("unitGoods.gdsShipperBzu.bzuName");
                }

                unitConsigneeId = "0000007907";
                def consignee = ScopedBizUnit.findScopedBizUnit( unitConsigneeId, BizRoleEnum.SHIPPER);
                println ("Consignee ::::: " + consignee);


                println ("Before Unit Number :::: "+ unit.unitId + " Consignee Id :: "+ unitConsigneeId  +  " Consignee Name :: "+ unitConsignee  +" Shipper Id:: "+ unitShipperId + " Shipper Name :: "+ unitShipper);
                //unit.setFieldValue("unitGoods.gdsConsigneeBzu.bzuName",blConsignee);
                //unit.setFieldValue("unitGoods.gdsConsigneeBzu.bzuId",blConsigneeId);
                //unit.setFieldValue("unitGoods.gdsShipperBzu.bzuName",blShipper);
                //unit.setFieldValue("unitGoods.gdsShipperBzu.bzuId",blShipperId);

                unit.getUnitGoods().updateShipper( consignee);

                //unit.setFieldValue("unitGoods.gdsConsigneeBzu.bzuName","Paper PAK INDUSTRIES");
                //unit.setFieldValue("unitGoods.gdsConsigneeBzu.bzuId","C459879990");

                if ( unit.unitGoods != null)
                {
                    unitConsigneeId = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId");
                    unitConsignee = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");
                    unitShipperId = unit.getFieldValue("unitGoods.gdsShipperBzu.bzuId");
                    unitShipper = unit.getFieldValue("unitGoods.gdsShipperBzu.bzuName");
                }

                println ("After Unit Number :::: "+ unit.unitId + " Consignee Id :: "+ unitConsigneeId  +  " Consignee Name :: "+ unitConsignee  +" Shipper Id:: "+ unitShipperId + " Shipper Name :: "+ unitShipper);
            }

        } catch (e) {
            println("Error in Bl Units::::: " + blNbr + " ::::: "+ e);
        }
    }

}


