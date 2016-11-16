import org.apache.xmlbeans.XmlObject
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlGoodsBl
import com.navis.cargo.business.model.GoodsBl
import org.apache.log4j.Logger;
import com.navis.cargo.business.model.BlItem

import org.apache.commons.lang.StringUtils

import com.navis.argo.ArgoBizMetafield
import com.navis.argo.ArgoField
import com.navis.argo.BlTransactionDocument
import com.navis.argo.BlTransactionDocument.BlTransaction
import com.navis.argo.BlTransactionsDocument
import com.navis.argo.ContextHelper
import com.navis.argo.EdiBlItem
import com.navis.argo.EdiCommodity
import com.navis.argo.EdiFacility
import com.navis.argo.EdiVesselVisit
import com.navis.argo.EdiBlEquipment
import com.navis.argo.EdiContainer
import com.navis.argo.Temperature
import com.navis.argo.Port
import com.navis.argo.PortCodes
import com.navis.argo.ShippingLine
import com.navis.argo.business.api.ArgoEdiUtils
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.CarrierDirectionEnum
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.Commodity
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType
import com.navis.vessel.VesselEntity
import com.navis.vessel.VesselField
import com.navis.vessel.VesselPropertyKeys
import com.navis.vessel.business.operation.Vessel
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.road.business.model.TruckingCompany

import com.navis.inventory.business.units.Unit;
import com.navis.inventory.InventoryField;
import com.navis.inventory.InventoryEntity;
import com.navis.inventory.business.api.UnitField;
import com.navis.argo.ArgoRefField;
import com.navis.argo.business.reference.RoutingPoint;
import com.navis.argo.business.reference.UnLocCode;
import com.navis.framework.portal.query.ObsoletableFilterFactory;

import com.navis.argo.business.model.GeneralReference;

import java.text.DecimalFormat;
import java.sql.Connection;

/*
* Author : Raghu Iyer
* Date Written : 06/05/2013
* Description: This groovy is used to conditionally update existing (Baplie) unit values with manifest data.
*/

public class MatUpdateWithManifestData extends GroovyInjectionBase
{
    private static String loadPortCode = "";
    private static String vesVoy="";
    private static String unitNbr = "";
    private static String unitTypeCode = "";
    private static String unitSealNbr = "";
    private static String unitGrossWt = "";
    private static String unitTareWt = "";
    private static String unitPrefTemp = "";
    private static String unitLoadPort = "";
    private static String unitLoadPortId = "";
    private static String unitVesVoy = "";
    private static String checkVes = "";
    private static String lloydsId = "";
    private static String ibVoyage = "";
    private static String sendAlert = "N";
    private static String honFacility = "HON";
    private static String ediLoadPort = "";

    private static String container = "";
    private static String typeCode = "";
    private static String sealNbr = "";
    private static String grossWt = "";
    private static String tareWt = "";
    private static String prefTemp = "";
    private static String portIdConvention;

    private static String retTypeCode = "";
    private static String retSealNbr = "";
    private static String retGrossWt = "";
    private static String retTareWt = "";
    private static String retVesVoy="";
    private static String retPrefTemp = "";
    private static String repVesVoy = "";

    DecimalFormat df = new DecimalFormat("#.##");

    private final String  emailFrom = '1aktosdevteam@matson.com'
    private final String emailTo = "1Tosdevteamhon@gmail.com";

    def inj = new GroovyInjectionBase();


    private void updateManifestData(XmlObject inXmlObject) {
        LOGGER.warn("updateManifestData Start");
        BlTransactionsDocument blDocument = (BlTransactionsDocument) inXmlObject;
        final BlTransactionsDocument.BlTransactions bltrans = blDocument.getBlTransactions();
        final BlTransactionDocument.BlTransaction[] bltransArray = bltrans.getBlTransactionArray();
        final BlTransactionDocument.BlTransaction blTran = bltransArray[0];

        Port loadPort = blTran.getLoadPort();
        EdiVesselVisit ediVesselVisit = blTran.getEdiVesselVisit();
        CarrierVisit cv = this.getVesselVisit(blTran);
        if (cv != null) {
            vesVoy = cv;
        }

        List<BlTransaction.EdiBlItemHolder> blItems = blTran.getEdiBlItemHolderList();
        if (!blItems.isEmpty()) {
            for (int i = 0; i < blTran.getEdiBlItemHolderArray().length; i++) {
                BlTransaction.EdiBlItemHolder ediBlItemHolder = blTran.getEdiBlItemHolderArray(i);
                this.getContainer(ediBlItemHolder.getEdiBlEquipmentArray(),loadPort,ediVesselVisit);
            }
        }
        LOGGER.warn("updateManifestData Start");
    }

    private void getContainer(EdiBlEquipment[] inEdiBlItemArray,Port loadPort,EdiVesselVisit ediVesselVisit) {
        try {
            List unitList = null;
            GeneralReference generalReference = new GeneralReference();
            for (int i = 0; i < inEdiBlItemArray.length; i++)
            {
                EdiBlEquipment ediBlEquipment = inEdiBlItemArray[i];

                EdiContainer ediContainer = ediBlEquipment.getEdiContainer();
                Temperature temperature = ediContainer.getTemperature();
                ediLoadPort = ""
                prefTemp = "";
                container = "";
                typeCode = "";
                sealNbr = "";
                grossWt = "";
                tareWt = "";
                retTypeCode = "";
                retSealNbr = "";
                retGrossWt = "";
                retTareWt = "";
                retVesVoy="";
                retPrefTemp = "";

                if (ediContainer!= null){
                    container = ediContainer.getContainerNbr();
                    typeCode = ediContainer.getContainerISOcode();
                    sealNbr = ediContainer.getContainerSealNumber1();
                    grossWt = ediContainer.getContainerGrossWt();
                    tareWt = ediContainer.getContainerTareWt();
                }

                if (loadPort != null){
                    loadPortCode = loadPort.getPortId();
                    portIdConvention = loadPort.getPortIdConvention();
                    try{
                        RoutingPoint rtg = this.resolveRoutingPointFromEncoding(portIdConvention,loadPortCode);
                        ediLoadPort = rtg.getPointId();
                        LOGGER.warn("::::RoutingPoint:::::"+loadPortCode);
                    }
                    catch (e)
                    {
                        LOGGER.warn("::::RoutingPoint:::::"+e);
                    }
                }

                if (temperature != null){
                    prefTemp = temperature.getPreferredTemperature();
                }

                println("InputediContainer:::::::"+container);
                unitList = this.getUnit(container);
                if (unitList.size() > 0)
                {
                    Iterator iterUnitList = unitList.iterator()
                    while (iterUnitList.hasNext())
                    {
                        def unit = iterUnitList.next();
                        unitNbr = unit.getFieldValue("unitId");
                        unitTypeCode = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                        unitSealNbr = unit.getFieldValue("unitSealNbr1");
                        unitGrossWt = unit.getFieldValue("unitGoodsAndCtrWtKg");
                        unitTareWt = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg");
                        if (unit.getUnitGoods().getGdsReeferRqmnts() != null)
                        {
                            unitPrefTemp = unit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempRequiredC");
                        }

                        unitLoadPortId = unit.getFieldValue("unitRouting.rtgPOL.pointScheduleKCode");
                        unitLoadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId");
                        unitVesVoy = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId");
                        repVesVoy = unitVesVoy;
                        if (unitVesVoy != null){
                            checkVes = unitVesVoy.substring(0,3);
                            lloydsId = this.getLloydsId(checkVes);
                        }

                    } // End of while

                    String valueRetained = "";

                    println("StartedBuilding 1");

                    if (unitTypeCode != typeCode){
                        sendAlert = "Y";
                        valueRetained = "";
                        if (unitTypeCode != null){
                            valueRetained = unitTypeCode;
                            ediContainer.setContainerISOcode(unitTypeCode);
                        }else{
                            valueRetained = typeCode;
                        }
                        retTypeCode = valueRetained;
                    }
                    else {
                        unitTypeCode = null;
                        typeCode = null;
                        retTypeCode = null;
                    }
                    LOGGER.warn("Started building 2");
                    if (unitSealNbr != sealNbr){
                        sendAlert = "Y";
                        valueRetained = "";
                        if (unitSealNbr != null){
                            valueRetained = unitSealNbr;
                            ediContainer.setContainerSealNumber1(unitSealNbr);
                        }else{
                            valueRetained = sealNbr;
                        }
                        retSealNbr = valueRetained;
                    }
                    else {
                        unitSealNbr = null;
                        sealNbr = null;
                        retSealNbr = null;
                    }
                    LOGGER.warn("Started building 3 "+unitTareWt+":::"+tareWt);
                    if ((unitTareWt==null||unitTareWt=="" ? "0.0" : Double.parseDouble(unitTareWt)) != (tareWt==null ||tareWt=="" ? "0.0" : Double.parseDouble(tareWt))){
                        sendAlert = "Y";
                        LOGGER.warn("Inside Started building 3");
                        valueRetained = "";
                        if (unitTareWt != null){
                            valueRetained = unitTareWt;
                            ediContainer.setContainerTareWt(unitTareWt);
                        }else{
                            valueRetained = tareWt;
                        }
                        tareWt = tareWt==null || tareWt=="" ? "0.0" : tareWt;

                        def storeUnitTareWt = Double.valueOf(df.format(Double.parseDouble(unitTareWt)));
                        def storeTareWt = Double.valueOf(df.format(Double.parseDouble(tareWt)));
                        def storeRetainedTareWt = Double.valueOf(df.format(Double.parseDouble(valueRetained)));

                        unitTareWt = storeUnitTareWt.toString();
                        tareWt = storeTareWt.toString();
                        retTareWt = storeRetainedTareWt.toString();
                    }
                    else {
                        unitTareWt = null;
                        tareWt = null;
                        retTareWt = null;
                    }
                    LOGGER.warn("Started building 4");
                    if ((unitGrossWt==null||unitGrossWt=="" ? "0.0" : Double.parseDouble(unitGrossWt)) != (grossWt==null||grossWt=="" ? "0.0" : Double.parseDouble(grossWt))){
                        sendAlert = "Y";
                        valueRetained = "";
                        if (unitGrossWt != null){
                            valueRetained = unitGrossWt;
                            ediContainer.setContainerGrossWt(unitGrossWt)
                        }else{
                            valueRetained = grossWt;
                        }
                        grossWt = grossWt==null || grossWt=="" ? "0.0" : grossWt;

                        def storeUnitGrossWt = Double.valueOf(df.format(Double.parseDouble(unitGrossWt)));
                        def storeRetainedGrossWt = Double.valueOf(df.format(Double.parseDouble(valueRetained)))
                        unitGrossWt = storeUnitGrossWt.toString();
                        retGrossWt = storeRetainedGrossWt.toString();
                    }
                    else {
                        unitGrossWt = null;
                        grossWt = null;
                        retGrossWt = null;
                    }
                    LOGGER.warn("Started building 5 " + unitPrefTemp + "::"+prefTemp);
                    if ((unitPrefTemp==null || unitPrefTemp==""? "0.0" : Double.parseDouble(unitPrefTemp)) != (prefTemp==null || prefTemp==""? "0.0" : Double.parseDouble(prefTemp))){
                        sendAlert = "Y";
                        valueRetained = "";
                        if (unitPrefTemp != null){
                            valueRetained = unitPrefTemp;
                            temperature.setPreferredTemperature(unitPrefTemp);
                        }else{
                            valueRetained = prefTemp;
                        }

                        def storeUnitPrefTemp = Double.valueOf(df.format(Double.parseDouble(unitPrefTemp)));
                        def storePrefTemp = Double.valueOf(df.format(Double.parseDouble(prefTemp)));
                        def storeRetainedPrefTemp = Double.valueOf(df.format(Double.parseDouble(valueRetained)));

                        unitPrefTemp = storeUnitPrefTemp.toString();
                        prefTemp = storePrefTemp.toString();
                        retPrefTemp = storeRetainedPrefTemp.toString();
                        println("retPrefTempValue::::"+retPrefTemp+"::"+storeRetainedPrefTemp);
                    }
                    else {
                        unitPrefTemp = null;
                        prefTemp = null;
                        retPrefTemp = null;
                    }
                    LOGGER.warn("Started building 6");
                    if (unitVesVoy != vesVoy){
                        sendAlert = "Y";
                        valueRetained = "";
                        if (unitLoadPortId != null){
                            valueRetained = unitVesVoy;
                            ediVesselVisit.setVesselId(lloydsId);
                        }else{
                            valueRetained = vesVoy;
                        }
                        retVesVoy = valueRetained;
                    }
                    else {
                        unitVesVoy = null;
                        vesVoy = null;
                        retVesVoy = null;
                    }
                    LOGGER.warn("EmailediContainerTest:::::::"+container);

                    if (sendAlert == "Y") {
                        def GvyRefDataLookup = inj.getGroovyClassInstance("GvyRefDataLookup");
                        Connection conn;
                        try {
                            println("Inserting data for :::"+ unitNbr);
                            conn = GvyRefDataLookup.connect();
                            println("BaplieValuesCheck:::" + unitTypeCode+" :: "+unitSealNbr+" :: "+unitPrefTemp+" :: "+unitTareWt+" :: "+unitGrossWt+" :: "+unitVesVoy);
                            GvyRefDataLookup.insertDiscrepancy(repVesVoy,"BAPLIE",unitNbr,unitTypeCode,unitSealNbr,unitPrefTemp,unitTareWt,unitGrossWt,unitVesVoy,conn);
                            conn = GvyRefDataLookup.connect();
                            println("ManifestValues:::" + typeCode+" :: "+sealNbr+" :: "+prefTemp+" :: "+tareWt+" :: "+grossWt+" :: "+vesVoy);
                            GvyRefDataLookup.insertDiscrepancy(repVesVoy,"MANIFEST",unitNbr,typeCode,sealNbr,prefTemp,tareWt,grossWt,vesVoy,conn);
                            conn = GvyRefDataLookup.connect();
                            println("RetainedValues:::" + retTypeCode+" :: "+retSealNbr+" :: "+retPrefTemp+" :: "+retTareWt+" :: "+retGrossWt+" :: "+retVesVoy);
                            GvyRefDataLookup.insertDiscrepancy(repVesVoy,"RETAINED",unitNbr,retTypeCode,retSealNbr,retPrefTemp,retTareWt,retGrossWt,retVesVoy,conn);
                            println("GvyRefDataLookup.deleteGenRefData Calling");

                        } catch (Exception e){
                            //GvyRefDataLookup.disconnect(conn);
                            println("GvyRefDataLookup.insertDiscrepancy Ended with errors :: " +  e);
                        }
                    }// End of sendAlert
                }// End unitList IF
            }// End of For loop
        }
        catch (e){
            println("Error in getContainer for conatiner <<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>"+e);
        }
    }
    public List getUnit(String unitNbr)
    {
        try {
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, unitNbr));
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            LOGGER.warn("After unitList"+ufvList.size());
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
            return units;
        }catch(Exception e){
            e.printStackTrace();
            LOGGER.warn(e.getMessage());
        }
    }

    public static RoutingPoint resolveRoutingPointFromEncoding(String inEncodingScheme, String inCode)
    {
        RoutingPoint point;
        if ("UNLOCCODE".equals(inEncodingScheme)) {
            point = resolveRoutingPointFromUnLoc(inCode);
        } else {
            DomainQuery dq = QueryUtils.createDomainQuery("RoutingPoint");
            if ("SCHED_D".equals(inEncodingScheme))
                dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.POINT_SCHEDULE_D_CODE, inCode));
            else if ("SCHED_K".equals(inEncodingScheme)) {
                dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.POINT_SCHEDULE_K_CODE, inCode));
            }
            else if ("SPLC".equals(inEncodingScheme))
                dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.POINT_SPLC_CODE, inCode));
            else if ("PORT_CODE".equals(inEncodingScheme))
                dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.POINT_ID, inCode));
            else {
                return resolveRoutingPointFromPortCode(inCode);
            }

            dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());
            point = (RoutingPoint)HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
        }
        return point;
    }

    public static RoutingPoint resolveRoutingPointFromUnLoc(String inUnLocCode)
    {
        RoutingPoint routingPoint = null;

        UnLocCode un = UnLocCode.findUnLocCode(inUnLocCode);
        if (un != null)
        {
            MetafieldId pointUnLocId = MetafieldIdFactory.getCompoundMetafieldId(ArgoRefField.POINT_UN_LOC, ArgoRefField.UNLOC_ID);
            DomainQuery dq = QueryUtils.createDomainQuery("RoutingPoint").addDqPredicate(PredicateFactory.eq(pointUnLocId, inUnLocCode));

            dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());
            List points = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            if (points.size() == 1)
            {
                routingPoint = (RoutingPoint)points.get(0);
            } else if (points.isEmpty())
            {
                routingPoint = findOrCreateRoutingPoint(un.getUnlocPlaceCode(), inUnLocCode);
            }
            else
            {
                for (Iterator iterator = points.iterator(); iterator.hasNext(); ) {
                    RoutingPoint rp = (RoutingPoint)iterator.next();
                    if (StringUtils.equals(rp.getPointId(), un.getUnlocPlaceCode()))
                        routingPoint = rp;

                }

                if (routingPoint == null) {
                    routingPoint = (RoutingPoint)points.get(0);
                    LOGGER.warn("resolveRoutingPoint: guessing routing point <" + routingPoint.getPointId() + "> for <" + inUnLocCode + ">");
                }
            }

        }

        if (routingPoint == null)
            LOGGER.info("resolveRoutingPointFromUnLoc: could not resolve UnLoc code <" + inUnLocCode + ">");

        return routingPoint;
    }

    public static RoutingPoint resolveRoutingPointFromPortCode(String inPortCode)
    {
        RoutingPoint routingPoint = findRoutingPoint(inPortCode);

        if (routingPoint == null)
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnLocCode").addDqPredicate(PredicateFactory.eq(ArgoRefField.UNLOC_PLACE_CODE, inPortCode)).addDqPredicate(PredicateFactory.eq(ArgoRefField.UNLOC_IS_PORT, Boolean.TRUE));

            List uns = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            if (!(uns.isEmpty())) {
                UnLocCode un = (UnLocCode)uns.get(0);
                routingPoint = findOrCreateRoutingPoint(un.getUnlocPlaceCode(), un.getUnlocId());
                if (uns.size() > 1)
                    LOGGER.warn("resolveRoutingPoint: guessed routing point <" + routingPoint.getPointId() + "> is for <" + un.getUnlocId() + ">");

            }
            else if ((inPortCode != null) && (inPortCode.length() == 3)) {
                routingPoint = findOrCreateRoutingPoint(inPortCode, "XXXXX");
                LOGGER.warn("resolveRoutingPoint: could not find a UnLoc for <" + routingPoint.getPointId() + ">");
            }
        }

        if (routingPoint == null)
            LOGGER.warn("resolveRoutingPoint: could not resolve location code <" + inPortCode + ">");

        return routingPoint;
    }

    private CarrierVisit getVesselVisit(BlTransaction inBlTrans) {

        String vesselOperatorId = null;
        String vesselOperatorIdAgency = null;
        EdiVesselVisit vesselVisit = inBlTrans.getEdiVesselVisit();
        if (vesselVisit != null) {
            if (vesselVisit.getShippingLine() != null) {
                vesselOperatorId = vesselVisit.getShippingLine().getShippingLineCode();
                vesselOperatorIdAgency = vesselVisit.getShippingLine().getShippingLineCodeAgency();
            }
        }
        ScopedBizUnit vesselOperator = ScopedBizUnit.resolveScopedBizUnit(vesselOperatorId, vesselOperatorIdAgency, BizRoleEnum.LINEOP);
        LineOperator line = LineOperator.resolveLineOprFromScopedBizUnit(vesselOperator);
        CarrierVisit cv = this.findVesselVisit(inBlTrans.getEdiVesselVisit(), inBlTrans.getDischargeFacility(), line);
        if (cv == null) {
            LOGGER.warn("Vessel Visit is Null and Not found");
        }else {
            LOGGER.warn("Vessel Visit is:"+ cv);
        }
        return cv;

    }

    private CarrierVisit findVesselVisit(EdiVesselVisit inEdiVesselVisit, EdiFacility inEdiFacility, LineOperator inLine) {

        LOGGER.warn (" in find Vessel visit:");
        CarrierVisit cv;
        Complex complex = ContextHelper.getThreadComplex();
        if (complex == null) {
            LOGGER.warn("Complex is Null");
            return;
        }
        if (inEdiFacility == null) {
            LOGGER.warn("Facility is Null");
            return;
        }
        String vvConvention = inEdiVesselVisit.getVesselIdConvention();
        String vvId = inEdiVesselVisit.getVesselId();
        String vvName = inEdiVesselVisit.getVesselName();
        String vygNbr;
        vygNbr = inEdiVesselVisit.getInVoyageNbr();
        try {
            cv = ArgoEdiUtils.findVesselVisit(inEdiVesselVisit);
            LOGGER.warn("value of cv:" + cv.toString());
        } catch (BizViolation inBv) {
            LOGGER.warn(" Carrier Visit Not Found " + inBv);
        }

        RoutingPoint discPoint = RoutingPoint.findRoutingPoint(honFacility);
        if (cv == null){
            cv = this.findVesselVisitForInboundStow(complex, vvConvention, vvId, vygNbr,discPoint,inLine);
        }
        LOGGER.warn(" foundCv:" + cv);
        return cv;
    }

    public CarrierVisit findVesselVisitForInboundStow(Complex inComplex,
                                                      String inIdConvention, String inId, String inInboundVoyage,
                                                      RoutingPoint inPoint, LineOperator inLineOperator) throws BizViolation
    {

        Facility fcy = Facility.findFacility(honFacility) ;
        List vvdList = this.findVvdList(inIdConvention, inId, inComplex, fcy, inInboundVoyage, true, inLineOperator);
        CarrierVisit foundCv = null;
        VesselVisitDetails vvd = null;
        int size = vvdList.size();

        LOGGER.warn ("vvdList size:" + size);
        String lineVoyageNbr = "";
        if (size == 0) {
            LOGGER.warn(VesselPropertyKeys.VVFINDER_CAN_NOT_RESOLVE_VISIT);
            return null;
        }

        for (int i = 0; i < vvdList.size(); i++){
            vvd =  (VesselVisitDetails) vvdList.get(i);
            lineVoyageNbr = vvd.getCarrierLineVoyNbrOrTrainId(inLineOperator,CarrierDirectionEnum.IB);
            ibVoyage = vvd.vvdIbVygNbr;
            LOGGER.warn("ibVoyage"+ibVoyage);
            if (lineVoyageNbr.equals(inInboundVoyage)){
                foundCv = vvd.getCvdCv();
            }
        }
        LOGGER.warn(" foundCv:" + foundCv+"::::"+ibVoyage);
        return foundCv;
    }

    private List findVvdList(String inIdConvention, String inId, Complex inComplex, Facility inFacility, String inVoyage, boolean inIsInbound,
                             LineOperator inOperator) throws BizViolation {

        Vessel vessel = Vessel.findVesselByEncoding(inId, inIdConvention);
        if (vessel == null) {
            LOGGER.warn (" Vessel is null");
            return null;
        }

        DomainQuery dq = QueryUtils.createDomainQuery(VesselEntity.VESSEL_VISIT_DETAILS)
                .addDqPredicate(PredicateFactory.eq(VVD_COMPLEX, inComplex.getCpxGkey()))
                .addDqPredicate(PredicateFactory.eq(VesselField.VVD_VESSEL, vessel.getVesGkey()));

        List vvdList =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        return vvdList;
    }

    private String getLloydsId(String vesId) throws BizViolation {
        String lloydsId = ""
        DomainQuery dq = QueryUtils.createDomainQuery("Vessel")
                .addDqPredicate(PredicateFactory.eq(VesselField.VES_ID, vesId));
        LOGGER.warn("getLloydsId"+dq);
        List vvdList =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        LOGGER.warn("getLloydsId"+dq);
        try{
            if (vvdList.size() == 0) {
                return null;
            }
            Iterator iter = vvdList.iterator();
            while(iter.hasNext()) {
                def vessel = iter.next();
                lloydsId = vessel.vesLloydsId;
            }
        }
        catch(e){
            LOGGER.warn("lloydsId::"+e);
        }
        LOGGER.warn("lloydsId::"+lloydsId);
        return lloydsId;
    }

    private static final Logger LOGGER = Logger.getLogger(MatUpdateWithManifestData.class);
}