/*
*  A1    Raghu Pattangi      Intial groovy plug-in for sail function (longhaul and barge) reports.
*/
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
import java.text.DecimalFormat;
import java.text.DateFormat
import java.util.HashMap
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.inventory.business.imdg.HazardItem;
import com.navis.inventory.business.imdg.Hazards;
import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.LineOperator;
import com.navis.argo.ContextHelper;
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
import org.apache.log4j.Logger;

public class GvySailBatchProcess extends GroovyInjectionBase
{
    public static final Logger LOGGER = Logger.getLogger(GvySailBatchProcess.class);
    private String outBoundCarrierId = null
    def inj = null;
    public void init(){
        println("Inside Groovy plug in")
        inj = new GroovyInjectionBase();
    }

    // This report prints all the rehandled cotainers in the YARD or INBOUND vessel which are not loaded back to the vessel.
    public void  createRehandleContainerRpt(event) {
        LOGGER.info("Begin createRehandleContainerRpt");
        inj = inj==null ? new GroovyInjectionBase(): inj;
        List restowList = new ArrayList();
        List rehandleRptList = new ArrayList();
        HashMap map = null;
        def visit = event.getEntity();
        def Id = visit.getCvdCv().getCvId()
        def reportDesignName = null; def displayType = null;
        outBoundCarrierId =
                visit.vvdVessel.vesId+visit.getFieldValue("vvdObVygNbr")

        try
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID, Id));

            List unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            Iterator iter = unitList.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next();
                def unit = ufv.ufvUnit;
                def equipType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                def restow = unit.getFieldValue("unitActiveUfv.ufvRestowType");
                map = new HashMap();
                restow = restow != null ? restow.getKey(): ''
                def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
                transitState = transitState != null ? transitState.getKey() : ''
                def tState = transitState.split("_")
                transitState = tState[1]
                LOGGER.info(" Unit number is "+unit.getFieldValue("unitId")+"  restow is "+restow);
                if ("RESTOW".equals(restow) && "YARD".equals(transitState))
                {
                    //println(" Unit number is "+unit.getFieldValue("unitId"));
                    //println("Remarks are "+unit.getFieldValue("unitRemark"));
                    //println(" position slot "+unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));
                    //println("pos 1: "+ unit.getFieldValue("unitActiveUfv.ufvArrivePosition.posSlot"));
                    //println("pos 2: "+ unit.getFieldValue("unitActiveUfv.ufvArrivePosition.posName"));
                    map.put("UnitNbr",unit.getFieldValue("unitId"));
                    map.put("PositionSlot",unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));
                    map.put("UnitRemark",unit.getFieldValue("unitRemark"));
                    map.put("VesselVisitId",Id);
                    restowList.add(map);
                }
            }
            println('restowList ::'+(restowList!= null ? restowList.size() : '0'))


            if (restowList != null && restowList.size() > 0 )
            {
                // Create data source with the restowlist to populate on the report.
                JRDataSource ds = new JRMapCollectionDataSource(restowList);
                // get report runner handle
                def reportRunner = inj.getGroovyClassInstance("ReportRunner");

                //Set report parameters
                HashMap parameters = new HashMap();

                // call report design of rehandle containers not loaded back to vessel report.
                reportDesignName = "REHANDLE CONTAINERS NOTLOADED BACK";

                // Emailing report
                reportRunner.emailReport(ds, parameters,reportDesignName , "1aktosdevteam@matson.com","Rehandle containers not loaded back to "+Id,"");
                LOGGER.info("End createRehandleContainerRpt");
            }
        }
        catch(Exception ex) {
            LOGGER.info("ERROR createRehandleContainerRpt "+ex.printStackTrace());
        }
    }//method ends


    // This report prints all the empty reefers on the outbound vessel whose destination is "OAK"
    // and which are flagged as "Clean and Caliberated".
    public void createCleanAndCaliberatedRpt(event) {
        LOGGER.info("Begin createCleanAndCaliberatedRpt");
        inj = inj==null ? new GroovyInjectionBase(): inj;
        def visit = event.getEntity();
        def Id = visit.getCvdCv().getCvId()
        HashMap resultMap = null;
        List outputList = new ArrayList();
        def reportDesignName = null;
        try
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID,Id))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FREIGHT_KIND, FreightKindEnum.MTY))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_DESTIN, "OAK"))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_PRIMARY_EQTYPE_ID))

            List unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            Iterator iter = unitList.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next();
                def unit = ufv.ufvUnit;
                resultMap = new HashMap();
                def eqType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
                def unitNotes = unit.getFieldValue("unitRemark");
                unitNotes = unitNotes != null ? unitNotes : ''
                if (eqType[0] == "R" && unitNotes.contains("CCR") ) {
                    println(" Unit number is "+unit.getFieldValue("unitId"));
                    println(" Transit state is "+transitState);
                    println(" Equipment type is "+eqType);
                    def cellLocation = unit.getFieldValue("unitActiveUfv.ufvArrivePosition.posSlot");
                    if (cellLocation == null)
                    {
                        cellLocation = ""
                    }
                    resultMap.put("UnitNbr",unit.getFieldValue("unitId"));
                    resultMap.put("EquipmentType",eqType);
                    resultMap.put("ArrivalPositionSlot",cellLocation);
                    resultMap.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"));
                    resultMap.put("VesselVisitId",Id);
                    outputList.add(resultMap);
                }
            }

            if (outputList != null && outputList.size() !=0)
            {
                // Create data source with the restowlist to populate on the report.
                JRDataSource ds = new JRMapCollectionDataSource(outputList);
                // get report runner handle
                def reportRunner = inj.getGroovyClassInstance("ReportRunner");

                //Set report parameters
                HashMap parameters = new HashMap();

                // call report design of CLEAN AND CALIBERATE REPORT.
                reportDesignName = "CLEAN AND CALIBERATE REPORT";

                // Emailing report
                reportRunner.emailReport(ds, parameters,reportDesignName , "1aktosdevteam@matson.com",Id+" MTY RFRS-CLEANED AND CALIBERATED ","");

            }
        }
        catch (Exception ex) {
            ex.printStackTrace()
        }

    }//method ends here


    // Creating ZIMX, NORA, FSCO and CCLU owner reports.
    public void createOwnerContainerReport(event) {
        println("createOwnerContainerReport begins");
        def visit = event.getEntity();
        def vesselId = visit.getCvdCv().getCvId();
        HashMap ownerMap = null;

        List zimList = new ArrayList();
        List noraList = new ArrayList();
        List ccluList = new ArrayList();
        List fscofhcuList = new ArrayList();
        def owner = null;
        def reportDesignName = null;

        try
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID,vesselId))


            List unitOwnerist = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            Iterator iterOwner = unitOwnerist.iterator();

            while(iterOwner.hasNext()) {
                def ufv = iterOwner.next();
                def unit = ufv.ufvUnit;
                def unitOwner = unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId");

                if (unitOwner.contains("ZIM"))
                {
                    ownerMap = new HashMap()
                    def cellLocation = unit.getFieldValue("unitActiveUfv.ufvArrivePosition.posSlot");
                    if (cellLocation == null)
                    {
                        cellLocation = ""
                    }
                    ownerMap.put("UnitNbr",unit.getFieldValue("unitId"));
                    ownerMap.put("FreightKind",unit.getFieldValue("unitCategory").getKey());  // FrieghtKind is initally used just to match the report field. It is actually category.
                    ownerMap.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"));
                    ownerMap.put("POD",unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
                    ownerMap.put("ArrivalPositionSlot",cellLocation);
                    ownerMap.put("VesselVisitId",vesselId);
                    ownerMap.put("EquipmentOwner","ZIM");
                    zimList.add(ownerMap);
                }
                else if (unitOwner.equals("NORA"))
                {
                    ownerMap = new HashMap()
                    def cellLocation = unit.getFieldValue("unitActiveUfv.ufvArrivePosition.posSlot");
                    if (cellLocation == null)
                    {
                        cellLocation = ""
                    }

                    ownerMap.put("UnitNbr",unit.getFieldValue("unitId"));
                    ownerMap.put("FreightKind",unit.getFieldValue("unitCategory").getKey());  // FrieghtKind is initally used just to match the report field. It is actually category.
                    ownerMap.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"));
                    ownerMap.put("POD",unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
                    ownerMap.put("ArrivalPositionSlot",cellLocation);
                    ownerMap.put("VesselVisitId",vesselId);
                    ownerMap.put("EquipmentOwner","NORA");
                    noraList.add(ownerMap);
                }
                else if (unitOwner.equals("CCLU"))
                {
                    ownerMap = new HashMap()
                    def cellLocation = unit.getFieldValue("unitActiveUfv.ufvArrivePosition.posSlot");
                    if (cellLocation == null)
                    {
                        cellLocation = ""
                    }

                    ownerMap.put("UnitNbr",unit.getFieldValue("unitId"));
                    ownerMap.put("FreightKind",unit.getFieldValue("unitCategory").getKey());  // FrieghtKind is initally used just to match the report field. It is actually category.
                    ownerMap.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"));
                    ownerMap.put("POD",unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
                    ownerMap.put("ArrivalPositionSlot",cellLocation);
                    ownerMap.put("VesselVisitId",vesselId);
                    ownerMap.put("EquipmentOwner","CCLU");
                    ccluList.add(ownerMap);
                }
                else if (unitOwner.equals("FSCO") || unitOwner.equals("FHSU"))
                {
                    ownerMap = new HashMap()
                    def cellLocation = unit.getFieldValue("unitActiveUfv.ufvArrivePosition.posSlot");
                    if (cellLocation == null)
                    {
                        cellLocation = ""
                    }

                    ownerMap.put("UnitNbr",unit.getFieldValue("unitId"));
                    ownerMap.put("FreightKind",unit.getFieldValue("unitCategory").getKey());  // FrieghtKind is initally used just to match the report field. It is actually category.
                    ownerMap.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"));
                    ownerMap.put("POD",unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
                    ownerMap.put("ArrivalPositionSlot",cellLocation);
                    ownerMap.put("VesselVisitId",vesselId);
                    ownerMap.put("EquipmentOwner","FHSU");
                    fscofhcuList.add(ownerMap);
                }
            }

            if (zimList!= null && zimList.size() != 0)
            {
                owner="ZIM"
                createOwnerReport(zimList,vesselId,owner)
            }
            if (noraList!= null && noraList.size() != 0)
            {
                owner="NORA"
                createOwnerReport(noraList,vesselId,owner)
            }
            if (ccluList!= null && ccluList.size() != 0)
            {
                owner="CCLU"
                createOwnerReport(ccluList,vesselId,owner)
            }
            if (fscofhcuList!= null && fscofhcuList.size() != 0)
            {
                owner="FSCO-FHSU"
                createOwnerReport(fscofhcuList,vesselId,owner)
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace()
        }

        println("createOwnerContainerReport ends");
    }

    private createOwnerReport(ownerList,vesselId,owner) {

        def reportDesignName = null
        inj = inj==null ? new GroovyInjectionBase(): inj;
        JRDataSource ds = new JRMapCollectionDataSource(ownerList);
        // get report runner handle
        def reportRunner = inj.getGroovyClassInstance("ReportRunner");

        //Set report parameters
        HashMap parameters = new HashMap();

        // call report design of rehandle containers not loaded back to vessel report.
        reportDesignName = "OWNER CONTAINER REPORT";

        // Emailing report
        reportRunner.emailReport(ds, parameters,reportDesignName , "1aktosdevteam@matson.com",owner+" containers loaded to "+vesselId,"");

    } // method ends here

    public void createEBSailingWireRpt(event) {
        println(" Sail function : createEBSailingWireRpt begins")

        def visit = event.getEntity();
        def vesselId = visit.getCvdCv().getCvId();
        List outputlist = new ArrayList()
        HashMap outputMap = null
        List intermediateList = new ArrayList();
        inj = inj==null ? new GroovyInjectionBase(): inj;
        def reportDesignName = null;

        try
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID,vesselId))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_DESTIN))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_FREIGHT_KIND))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_PRIMARY_EQTYPE_ID));
            println("Domain query is "+dq);

            List resultList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            println("resultList size is "+resultList.size())

            Iterator itereb = resultList.iterator();

            while (itereb.hasNext())
            {
                outputMap = new HashMap();
                def ufv = itereb.next();
                def unit = ufv.ufvUnit;
                def unitId = unit.getFieldValue("unitId");
                def commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                def dir = unit.getFieldValue("unitFreightKind");
                dir = dir != null ? dir.getKey() : ''
                if ("FCL".equals(dir) && "AUTO".equals(commodity))
                {
                    dir = "AUTO";
                } else if ("FCL".equals(dir) && !"AUTO".equals(commodity))
                {
                    dir = "LOAD";
                }
                def destination = unit.getFieldValue("unitGoods.gdsDestination");
                def equipmentType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId");
                println("Before Map  "+unitId+" "+destination+" "+equipmentType+" "+dir+" "+commodity+" "+loadPort)
                outputMap.put("UnitNbr",unitId);
                outputMap.put("Destination",destination);
                outputMap.put("FreightKind",dir);
                outputMap.put("Commodity",commodity);
                outputMap.put("EquipmentType",equipmentType);
                outputMap.put("POL",loadPort);
                outputMap.put("VesselVisitId",vesselId);
                intermediateList.add(outputMap);
            }

            if (intermediateList != null && intermediateList.size() !=0)
            {
                println("intermediateList size is "+intermediateList.size())
                // Create data source with the restowlist to populate on the report.
                JRDataSource ds = new JRMapCollectionDataSource(intermediateList);
                // get report runner handle
                def reportRunner = inj.getGroovyClassInstance("ReportRunner");

                //Set report parameters
                HashMap parameters = new HashMap();

                // call report design of CLEAN AND CALIBERATE REPORT.
                reportDesignName = "EB SAILING WIRE";

                // Emailing report
                reportRunner.emailReport(ds, parameters,reportDesignName , "1aktosdevteam@matson.com",vesselId+" ailing wire report ",vesselId+" Sailing wire : Attached are the Total and Detail reports");

            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace()
        }



    } // method ends here

    public void createReportsAfterSail(event){

        def visit = event.getEntity();
        def vesselId = visit.getCvdCv().getCvId();
        def facility = ContextHelper.getThreadFacility().getFcyId();
        def nextFacility = visit.getCvdCv().getCvNextFacility(); //"KDK";
        def reportName = "OUTBOUND DCM - ANCHORAGE";

        try {
            //  generateDCMReports(visit,facility,reportName,"OUTBOUND",event);
            if(nextFacility != null){
                reportName = "INBOUND DCM - KODIAK"
                generateDCMReports(visit,nextFacility.getFcyId(),reportName,"INBOUND",event);
                generateReeferReport(visit,nextFacility.getFcyId(),event,"INBOUND")
                generateOversizeReport(visit,nextFacility.getFcyId(),event,"INBOUND")
            }
        }
        catch (Exception ex ){
            ex.printStackTrace()
        }
    }//method ends here

    public void generateDCMReports(visit,facility,reportName,direction,event){
        def outputMap = null;
        inj = inj==null ? new GroovyInjectionBase(): inj;
        def vesselId = visit.getCvdCv().getCvId();
        List intermediateList = new ArrayList();
        def fcyId = "ANK"; //facility.getFcyId();
        def fcyName = "Anchorage"; //facility.getFcyName();
        reportName = "INBOUND DCM - ANCHORAGE"
        if (facility == "KDK"){
            fcyId = "KDK"
            fcyName = "Kodiak"
            reportName = "INBOUND DCM - KODIAK"
        }
        if (facility == "DUT"){
            fcyId = "DUT"
            fcyName = "Dutch"
            reportName = "INBOUND DCM - DUTCH"
        }
        try {

            println("createReportsAfterSail");
            LOGGER.info("Begin createReportsAfterSail");
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID,vesselId))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_IS_HAZARDS,Boolean.TRUE))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_CURRENT_POSITION_TYPE,LocTypeEnum.VESSEL))

                    .addDqOrdering(Ordering.asc(UnitField.UFV_DESTIN))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_FREIGHT_KIND))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_PRIMARY_EQTYPE_ID));
            println("Domain query is "+dq);

            List resultList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            println("resultList size is "+resultList.size())

            Iterator itereb = resultList.iterator();

            while (itereb.hasNext())
            {

                def ufv = itereb.next();
                def unit = ufv.ufvUnit;


                def unitId = unit.getFieldValue("unitId");
                def commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                def dir = unit.getFieldValue("unitFreightKind");

                def destination = unit.getFieldValue("unitGoods.gdsDestination");
                def equipmentType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId");
                def discPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
                println("Before Map  "+unitId+" "+destination+" "+equipmentType+" "+dir+" "+commodity+" "+loadPort)
                LOGGER.info("Discharge Port ::"+discPort);
                Hazards hazards = unit.getUnitGoods().getGdsHazards();
                if (hazards !=null){
                    Iterator hzrdItems = hazards.getHazardItemsIteratorOrderedBySeverity();
                    while(hzrdItems.hasNext()){
                        HazardItem hzdItem = (HazardItem)hzrdItems.next();
                        outputMap = new HashMap();
                        outputMap.put("OutboundCarrierId",vesselId);
                        outputMap.put("OutboundCarrierOutVoyageNbr",visit.getCarrierIbVoyNbrOrTrainId());
                        outputMap.put("OutboundCarrierType",visit.getCarrierTypeId());
                        outputMap.put("OutboundCarrierName",visit.getCarrierVehicleName());
                        outputMap.put("OutboundCarrierCountry",visit.getCarrierCountryName());
                        outputMap.put("OutboundCarrierOperatorName",visit.getCarrierOperator().getBzuId());
                        outputMap.put("OutboundCarrierETD",visit.getCvdETD());

                        outputMap.put("InboundCarrierCountry",visit.getCarrierCountryName());
                        outputMap.put("InboundCarrierFacilityId",fcyId);
                        outputMap.put("InboundCarrierFacilityName",fcyName);
                        outputMap.put("InboundCarrierId",visit.getCvdCv().getCvId());
                        outputMap.put("InboundCarrierOutVoyageNbr",visit.getCarrierObVoyNbrOrTrainId());
                        outputMap.put("InboundCarrierDocumentationNbr",visit.getCarrierDocumentationNbr());
                        outputMap.put("InboundCarrierATA",visit.getCvdCv().getCvATA());
                        outputMap.put("InboundCarrierETA",visit.getCvdETA());



                        outputMap.put("UnitNbr",unitId);
                        outputMap.put("EquipmentArcheTypeId",equipmentType);
                        outputMap.put("IsHazardous","true");
                        outputMap.put("HazardItemSequence",hzdItem.getHzrdiSeq());
                        outputMap.put("HazardItemUNNumber",hzdItem.getHzrdiUNnum());
                        outputMap.put("HazardItemProperName",hzdItem.getHzrdiProperName());
                        outputMap.put("HazardItemImdgClass",hzdItem.getHzrdiImdgClass().getKey());
                        outputMap.put("HazardItemPackingGroup",hzdItem.getHzrdiPackingGroup());
                        println("HazardItemPackingGroup:::"+hzdItem.getHzrdiPackingGroup());
                        if(hzdItem.getHzrdiPackingGroup() !=null){
                            def pkgGroup= hzdItem.getHzrdiPackingGroup().getKey()
                            println("HazardItemPackageGroup:::"+pkgGroup);
                            if(pkgGroup == "I"){
                                outputMap.put("HazardItemPackingGroup","1");
                            }
                            else if (pkgGroup == "II"){
                                outputMap.put("HazardItemPackingGroup","2");
                            }
                            else if (pkgGroup == "III"){
                                outputMap.put("HazardItemPackingGroup","3");
                            }
                        }
                        outputMap.put("HazardItemFlashPoint",hzdItem.getHzrdiFlashPoint());
                        outputMap.put("HazardItemWeight",hzdItem.getHzrdiWeight());
                        outputMap.put("HazardItemQuantity",hzdItem.getHzrdiQuantity());
                        outputMap.put("HazardItemPackageType",hzdItem.getHzrdiPackageType());
                        println("HazardItemPackageType:::"+hzdItem.getHzrdiPackageType());
                        outputMap.put("HazardItemEmergencyTelephone",hzdItem.getHzrdiEmergencyTelephone());
                        outputMap.put("HazardItemLimitedQty",hzdItem.getHzrdiLtdQty());
                        outputMap.put("HazardItemMarinePollutants",hzdItem.getHzrdiMarinePollutants());
                        if(unit.getUnitGoods().getGdsShipperBzu()!=null){				outputMap.put("GoodsShipperName",unit.getUnitGoods().getGdsShipperBzu().getBzuName());
                        }

                        outputMap.put("BlNbr",unit.getUnitGoods().getGdsBlNbr());
                        outputMap.put("POL",loadPort);
                        outputMap.put("POLPlaceName",loadPort);
                        outputMap.put("POD",discPort);
                        outputMap.put("PODPlaceName",discPort);
                        outputMap.put("OutboundCarrierFacilityId",visit.getCvdCv().getCvFacility().getFcyId());			outputMap.put("OutboundCarrierFacilityName",visit.getCvdCv().getCvFacility().getFcyName());			outputMap.put("PositionSlot",ufv.getUfvLastKnownPosition().getPosSlot());
                        outputMap.put("BookingNbr",unit.getUnitPrimaryUe().getUeDepartureOrderItem());
                        if(visit.getCvdCv().getCvATD() != null) {
                            outputMap.put("OutboundCarrierATD",visit.getCvdCv().getCvATD());
                        }

                        if(visit.getCvdCv().getCvATA() != null){

                            outputMap.put("OutboundCarrierATA",visit.getCvdCv().getCvATA());
                        }

                        if(visit.getCvdCv().getCvATA() != null) {
                            outputMap.put("InboundCarrierATA",visit.getCvdCv().getCvATA());
                        }

                        if(visit.getCvdCv().getCvATA() != null){

                            outputMap.put("InboundCarrierETA",visit.getCvdCv().getCvATA());
                        }
                        outputMap.put("OutboundCarrierDocumentationNbr",visit.getCarrierDocumentationNbr());

                        intermediateList.add(outputMap);
                    }

                }


            }


            JRDataSource ds = new JRMapCollectionDataSource(intermediateList);
            LOGGER.warn("createReportsAfterSail ds creation complted");
            // get report runner handle
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            LOGGER.warn("createReportsAfterSail - ReportRUnner");
            //Set report parameters
            HashMap parameters = new HashMap();
            parameters.put("Vessel_Visit",vesselId);
            LOGGER.warn("createReportsAfterSai Parameterel");

            // call report design of DCM OUTBOUND.

            //String reportDesignName = "OUTBOUND DCM - ANCHORAGE";

            // Emailing report
            reportRunner.emailReport(ds, parameters,reportName , "1aktosdevteam@matson.com","DCM "+direction+" REPORT - "+facility+" "+vesselId,"DCM "+direction+" REPORT - "+facility+" "+vesselId);
            LOGGER.info("createReportsAfterSail report generated");

        }
        catch (Exception ex ){
            ex.printStackTrace()
        }

    }//method ends here

    public void generateReeferReport(visit,facility,event,direction){
        def outputMap = null;
        inj = inj==null ? new GroovyInjectionBase(): inj;
        def vesselId = visit.getCvdCv().getCvId();
        List intermediateList = new ArrayList();
        def fcyId = facility; //facility.getFcyId();
        def fcyName = facility; //facility.getFcyName();

        def reportName = "REEFER CARGO - INBOUND - ANCHORAGE"
        if (facility == "KDK") {
            fcyId = "KDK"
            fcyName = "Kodiak"
            reportName = "REEFER CARGO - INBOUND - KODIAK"
        }
        if (facility == "DUT") {
            fcyId = "DUT"
            fcyName = "Dutch"
            reportName = "REEFER CARGO - INBOUND - DUTCH"
        }
        try {
            println("reefer rpt starts");
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID,vesselId))
                    .addDqPredicate(PredicateFactory.isNotNull(UnitField.UFV_TEMP_REQUIRED_C))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_CURRENT_POSITION_TYPE,LocTypeEnum.VESSEL))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_PRIMARY_EQTYPE_ID));
            println("Domain query is "+dq);
            List resultList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("resultList size is "+resultList.size())
            Iterator itereb = resultList.iterator();
            while (itereb.hasNext())
            {
                def ufv = itereb.next();
                def unit = ufv.ufvUnit;

                def unitId = unit.getFieldValue("unitId");
                def commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                def dir = unit.getFieldValue("unitFreightKind");
                def equipmentType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId");
                def discPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
                def destination = unit.getFieldValue("unitGoods.gdsDestination");

                outputMap = new HashMap();
                outputMap.put("InboundCarrierCountry",visit.getCarrierCountryName());
                outputMap.put("InboundCarrierATA",visit.getCvdCv().getCvATA());
                outputMap.put("InboundCarrierETA",visit.getCvdETA());
                outputMap.put("InboundCarrierFacilityId",fcyId);
                outputMap.put("InboundCarrierFacilityName",fcyName);
                outputMap.put("InboundCarrierId",visit.getCvdCv().getCvId());
                outputMap.put("InboundCarrierOutVoyageNbr",visit.getCarrierObVoyNbrOrTrainId());
                outputMap.put("InboundCarrierDocumentationNbr",visit.getCarrierDocumentationNbr());
                outputMap.put("CommodityDescription",commodity);
                outputMap.put("UfvFlexString04","");
                if(unit.getUnitGoods().getGdsReeferRqmnts()!=null){
                    outputMap.put("RequiredMinTempC",unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempLimitMinC());
                    outputMap.put("RequiredMaxTempC",unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempLimitMaxC());
                }
                outputMap.put("EquipmentNumberNoCD",unit.getUnitPrimaryUe().getUeEquipmentState().getEqsEquipment().getEqIdNoCheckDigit());
                outputMap.put("EquipmentCD",unit.getUnitPrimaryUe().getUeEquipmentState().getEqsEquipment().getEqIdCheckDigit());
                outputMap.put("PositionSlot",ufv.getUfvLastKnownPosition().getPosSlot());
                def grossWeight = unit.getFieldValue("unitGoodsAndCtrWtKg");
                outputMap.put("GrossWeight",grossWeight);
                outputMap.put("POL",loadPort);
                outputMap.put("POD",discPort);
                outputMap.put("Destination",destination);
                outputMap.put("LineOperator",unit.getUnitLineOperator().getBzuId());
                if(unit.getUnitGoods().getGdsShipperBzu()!=null){
                    outputMap.put("GoodsShipperName",unit.getUnitGoods().getGdsShipperBzu().getBzuName());
                }
                if(unit.getUnitGoods().getGdsConsigneeBzu()!=null){
                    outputMap.put("GoodsConsigneeName",unit.getUnitGoods().getGdsConsigneeBzu().getBzuName());
                }
                intermediateList.add(outputMap);
            }


            JRDataSource ds = new JRMapCollectionDataSource(intermediateList);
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            HashMap parameters = new HashMap();
            parameters.put("Vessel_Visit",vesselId);

            String reportDesignName = "REEFER CARGO - INBOUND - KODIAK";
            reportRunner.emailReport(ds, parameters,reportName , "1aktosdevteam@matson.com","REEFER CARGO  "+direction+" REPORT - "+fcyName+" "+vesselId,"REEFER CARGO  "+direction+" REPORT - "+fcyName+" "+vesselId);


        }
        catch(Exception ex){
            ex.printStackTrace();
        }

    }//method ends

    public void generateOversizeReport(visit,facility,event,direction){
        def outputMap = null;
        inj = inj==null ? new GroovyInjectionBase(): inj;
        def vesselId = visit.getCvdCv().getCvId();
        List intermediateList = new ArrayList();

        def fcyId = "ANK"; //facility.getFcyId();
        def fcyName = "Anchorage"; //facility.getFcyName();
        def reportName = "OVERSIZE CARGO - INBOUND - ANCHORAGE"
        if (facility == "KDK"){
            fcyId = "KDK"
            fcyName = "Kodiak"
            reportName = "OVERSIZE CARGO - INBOUND - KODIAK"
        }
        if (facility == "DUT"){
            fcyId = "DUT"
            fcyName = "Dutch"
            reportName = "OVERSIZE CARGO - INBOUND - DUTCH HARBOR"
        }
        try {
            println("oversize rpt starts");
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID,vesselId))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_IS_OOG,Boolean.TRUE))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_CURRENT_POSITION_TYPE,LocTypeEnum.VESSEL))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_PRIMARY_EQTYPE_ID));
            println("Domain query is "+dq);
            List resultList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("resultList size is "+resultList.size())
            Iterator itereb = resultList.iterator();
            while (itereb.hasNext())
            {
                def ufv = itereb.next();
                def unit = ufv.ufvUnit;

                def unitId = unit.getFieldValue("unitId");
                def commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                def dir = unit.getFieldValue("unitFreightKind");
                def equipmentType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId");
                def discPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
                def destination = unit.getFieldValue("unitGoods.gdsDestination");

                outputMap = new HashMap();
                outputMap.put("InboundCarrierCountry",visit.getCarrierCountryName());
                outputMap.put("InboundCarrierATA",visit.getCvdCv().getCvATA());
                outputMap.put("InboundCarrierETA",visit.getCvdETA());
                outputMap.put("InboundCarrierFacilityId",fcyId);
                outputMap.put("InboundCarrierFacilityName",fcyName);
                outputMap.put("InboundCarrierId",visit.getCvdCv().getCvId());
                outputMap.put("InboundCarrierOutVoyageNbr",visit.getCarrierObVoyNbrOrTrainId());
                outputMap.put("InboundCarrierDocumentationNbr",visit.getCarrierDocumentationNbr());
                outputMap.put("CommodityDescription",commodity);
                outputMap.put("UnitNbr",unitId);
                outputMap.put("CargoWeight",unitId);
                outputMap.put("UfvFlexString04","");
                //outputMap.put("EquipmentNumberNoCD",unit.getUnitPrimaryUe().getUeEquipmentState().getEqsEquipment().getEqIdNoCheckDigit());
                //outputMap.put("EquipmentCD",unit.getUnitPrimaryUe().getUeEquipmentState().getEqsEquipment().getEqIdCheckDigit());
                outputMap.put("PositionSlot",ufv.getUfvLastKnownPosition().getPosSlot());
                def grossWeight = unit.getFieldValue("unitGoodsAndCtrWtKg");
                outputMap.put("GrossWeight",grossWeight);
                outputMap.put("CargoWeight",grossWeight);
                outputMap.put("EquipmentArcheTypeId",equipmentType);
                outputMap.put("BlNbr",unit.getUnitGoods().getGdsBlNbr());
                def oogLeft = unit.getFieldValue("unitOogLeftCm");
                def oogRight = unit.getFieldValue("unitOogRightCm");
                def oogFront = unit.getFieldValue("unitOogFrontCm");
                def oogBack = unit.getFieldValue("unitOogBackCm");
                def oogTop = unit.getFieldValue("unitOogTopCm");
                DecimalFormat df = new DecimalFormat("#0.##")
                if(oogLeft!=null || oogLeft ==0){
                    def rawOogLeft= oogLeft/2.54;
                    outputMap.put("OOGLeft",df.format(rawOogLeft));
                }
                if(oogRight!=null || oogRight ==0){
                    def rawOogRight = oogRight/2.54;
                    outputMap.put("OOGRight",df.format(rawOogRight));
                }
                if(oogFront!=null || oogFront ==0){
                    def rawOogFront = oogFront/2.54;
                    outputMap.put("OOGFront",df.format(rawOogFront));
                }
                if(oogBack!=null || oogBack ==0){
                    def rawOogBack = oogBack/2.54;
                    outputMap.put("OOGBack",df.format(rawOogBack));
                }
                if(oogTop!=null || oogTop ==0){
                    def rawOogTop = oogTop/2.54;
                    outputMap.put("OOGTop",df.format(rawOogTop));
                }
                outputMap.put("POL",loadPort);
                outputMap.put("POD",discPort);
                outputMap.put("Destination",destination);
                outputMap.put("LineOperator",unit.getUnitLineOperator().getBzuId());
                if(unit.getUnitGoods().getGdsShipperBzu()!=null){
                    outputMap.put("GoodsShipperName",unit.getUnitGoods().getGdsShipperBzu().getBzuName());
                }
                if(unit.getUnitGoods().getGdsConsigneeBzu()!=null){
                    outputMap.put("GoodsConsigneeName",unit.getUnitGoods().getGdsConsigneeBzu().getBzuName());
                }
                intermediateList.add(outputMap);
            }


            JRDataSource ds = new JRMapCollectionDataSource(intermediateList);
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            HashMap parameters = new HashMap();
            parameters.put("Vessel_Visit",vesselId);

            String reportDesignName = "OVERSIZE CARGO - INBOUND - ANCHORAGE";
            reportRunner.emailReport(ds, parameters,reportName , "1aktosdevteam@matson.com","OVERSIZE CARGO  "+direction+" REPORT - "+fcyName+" "+vesselId,"OVERSIZE CARGO  "+direction+" REPORT - "+fcyName+" "+vesselId);


        }
        catch(Exception ex){
            ex.printStackTrace();
        }

    }//method ends

}//class ends
