import com.navis.inventory.InventoryEntity
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.EquipMaterialEnum
import com.navis.argo.business.atoms.FlagStatusEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.rules.ServiceImpediment
import org.apache.log4j.Logger
import java.text.SimpleDateFormat
import javax.jms.JMSException
import javax.jms.Session
import javax.jms.ConnectionFactory
import javax.jms.MessageProducer
import javax.jms.Destination
import javax.jms.TextMessage
import javax.jms.*

import com.navis.inventory.business.units.Routing;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.text.DateFormat

/*

	Date Written: 07/06/2012
	Author: Siva Raja
	Description: Groovy to extract unit details based on ane event recorded and send xml data to MNS application using JMS.
	  */

public class  MATBargeYBDepartUnits extends GroovyApi {

    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

    private static final String XML_OVERRIDE = "\"";
    private static final String threadFacility = ContextHelper.getThreadFacility().getFcyId();
    private static final String XML_END_ELEMENT = "/>";
    private static final String queueName ="";// "jms.tos.newvess.inbound";
    private static final String topicName ="";// "jms.topic.tdp.newVesselHon";
    private static final String url = "";//"tcp://10.201.1.79:61616";//production
    private String addNode = "Y";

    //  public void execute()
    public boolean getBargeUnits(String carrierId, String isBarge, String facility, String nextFacility)
    {
        LOGGER.warn("MATBargeYBDepartUnits started" + timeNow);
        println("MATBargeYBDepartUnits getBargeUnits started" + timeNow);
        println("Parameters------------------>"+carrierId +" "+facility+" "+ nextFacility+" "+isBarge)
        //String cv = "ALE252A";
        String cv =carrierId;
        if (isBarge == "BARGE")
        {
            if ((facility == "HON") && ((nextFacility == "KHI")||(nextFacility == "KAH")||(nextFacility == "NAW")||
                    (nextFacility == "LNI") || (nextFacility == "MOL") || (nextFacility == "MIX") || (nextFacility == "HIL"))
            )
            {
                List units = getActiveUnits(cv)
                UnitFacilityVisit ufv = null;
                Iterator unitIterator = units.iterator();
                while(unitIterator.hasNext())
                {
                    def unit = unitIterator.next();
                    println(unit.getUnitId());
                    ufv = unit.getUnitActiveUfvNowActive();
                    println(":::::::UFV::::::::::::::"+ufv);
                    break;
                }

                //UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive();
                //if (ufv == null){
                //LOGGER.warn (" Unit facility is null");
                //return;
                //}

                def carrierMode = LocTypeEnum.getEnum("VESSEL");
                println(carrierMode);
                def obCarrierVisit = CarrierVisit.findCarrierVisit(ContextHelper.getThreadFacility(), carrierMode, cv);

                //CarrierVisit cv1 = ufv.getInboundCarrierVisit();

                //Facility fcy = Facility.findFacility("HON");
                def fcy = com.navis.argo.ContextHelper.getThreadFacility();
                CarrierVisit cv1 =  CarrierVisit.findVesselVisit(fcy,cv)
                println("obCarrierVisit"+obCarrierVisit+" "+cv1);

                List unitList = this.getActiveUnits(cv);
                //if (unitList.isEmpty()) {
                //LOGGER.warn ("No Units in CV");
                //return;
                //}
                LOGGER.warn("buildXmlElement Start " + cv);
                println("buildXmlElement Start " + cv);
                if (unitList.size() > 0 ){
                    StringBuffer xml = this.buildXmlElement(unitList, cv1);
                    LOGGER.warn(xml.toString());
                    println(xml.toString());
                    //Send the Complete XML string to MNS application using JMS queue.
                    this.send(xml.toString());
                    this.topicSend(xml.toString());
                }
                LOGGER.warn("MATBargeYBDepartUnits ended" + timeNow);
                println("MATBargeYBDepartUnits ended" + timeNow);
            }
        }
        else {
            println("Carrier " + carrierId +" is a "+ isBarge)
            return;
        }

    }

    //  public void execute()
    public boolean execute(Map  params)
    {
        LOGGER.warn("MATBargeYBDepartUnitsTest started" + timeNow);
        println("MATBargeYBDepartUnitsTest started" + timeNow);
        String cv = "YB3413A";

        List units = getActiveUnits(cv)
        UnitFacilityVisit ufv = null;
        Iterator unitIterator = units.iterator();
        while(unitIterator.hasNext())
        {
            def unit = unitIterator.next();
            println(unit.getUnitId());
            ufv = unit.getUnitActiveUfvNowActive();
            println(":::::::UFV::::::::::::::"+ufv);
            break;
        }

        //UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive();
        //if (ufv == null){
        //LOGGER.warn (" Unit facility is null");
        //return;
        //}

        def carrierMode = LocTypeEnum.getEnum("VESSEL");
        println(carrierMode);
        def obCarrierVisit = CarrierVisit.findCarrierVisit(ContextHelper.getThreadFacility(), carrierMode, cv);

        //CarrierVisit cv1 = ufv.getInboundCarrierVisit();

        //Facility fcy = Facility.findFacility("HON");
        def fcy = com.navis.argo.ContextHelper.getThreadFacility();
        CarrierVisit cv1 =  CarrierVisit.findVesselVisit(fcy,cv)
        println("obCarrierVisit"+obCarrierVisit+" "+cv1);

        List unitList = this.getActiveUnits(cv);
        //if (unitList.isEmpty()) {
        //LOGGER.warn ("No Units in CV");
        //return;
        //}
        LOGGER.warn("buildXmlElement Start " + cv);
        println("buildXmlElement Start " + cv);
        StringBuffer xml = this.buildXmlElement(unitList, cv1);
        LOGGER.warn(xml.toString());
        println(xml.toString());
        LOGGER.warn("MATBargeYBDepartUnitsTest ended" + timeNow);
        println("MATBargeYBDepartUnitsTest ended" + timeNow);
    }

    public List getActiveUnits(String vesVoy)
    {
        try {
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            if(vesVoy.startsWith('YB')) {

                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_INTENDED_OB_ID,vesVoy));

            }  else {

                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID,vesVoy));

            }



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
                        def fcy = com.navis.argo.ContextHelper.getThreadFacility();
                        def ufvFcy = unit.getUfvForFacilityCompletedOnly(fcy);
                        if (ufvFcy != null)
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

    public void send(String msg) throws JMSException, Exception {

        LOGGER.warn (" in send message ");
        Session                	session    = null;
        javax.jms.Connection	connection = null;
        ConnectionFactory      	cf         = null;
        MessageProducer        	mp         = null;
        Destination				destination = null;
        println( "Calling MATBargeYBDepartUnits.send using ActiveMQConnectionFactory");
        try {
            LOGGER.warn( "Getting Connection Factory");
            cf = new ActiveMQConnectionFactory(url);
            LOGGER.warn( "Getting Queue");
            LOGGER.warn( "Getting Connection for Queue " + cf);
            connection = cf.createConnection();
            LOGGER.warn( "staring the connection");
            connection.start();
            LOGGER.warn( "creating session");
            session = connection.createSession(false, 1);
            LOGGER.warn( "creating messageProducer");
            destination = session.createQueue(queueName);
            mp = session.createProducer(destination);
            LOGGER.warn( "creating TextMessage");
            TextMessage outMessage = session.createTextMessage( msg);
            LOGGER.warn( "sending Message to queue: " + queueName);
            mp.send(outMessage);
            mp.close();
            session.close();
            connection.close();
        }
        catch (Exception je)
        {
            LOGGER.warn("Exception in send:" + je )
        }
    }

    public void topicSend(String msg) throws JMSException, Exception {
        LOGGER.warn (" in topicSend message ");
        TopicSession			session    = null;
        TopicConnection			connection = null;
        TopicConnectionFactory	cf         = null;
        MessageProducer        	mp         = null;
        Destination				destination = null;
        println( "Calling MATBargeYBDepartUnits.topicSend using ActiveMQConnectionFactory");
        try {

            LOGGER.warn ("Getting Connection Factory");
            cf = new ActiveMQConnectionFactory(url);

            LOGGER.warn("Getting Connection for Topic");
            connection = cf.createTopicConnection();

            LOGGER.warn( "staring the connection");
            connection.start();

            LOGGER.warn( "creating session");
            session = connection.createTopicSession(false, 1);
            destination = session.createTopic(topicName);

            LOGGER.warn( "creating messageProducer");
            mp = session.createProducer(destination);

            LOGGER.warn( "creating TextMessage");
            TextMessage outMessage = session.createTextMessage( msg);

            LOGGER.warn( "sending Message to topic: " + topicName);
            mp.send(outMessage);

            mp.close();
            session.close();
            connection.close();
        }
        catch (Exception je)
        {
            LOGGER.warn("Exception in topicSend:" + je )
        }
    }

    private List<Unit> getUnitListInCv(CarrierVisit inCv){
        println ("getUnitListInCv Start")
        ArrayList vesVistUnitLists = new ArrayList();
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(ArgoField.UFV_ACTUAL_IB_CV, inCv.getCvGkey()))
                .addDqPredicate(PredicateFactory.in(UnitField.UNIT_VISIT_STATE, "S70_DEPARTED"));
        //.addDqPredicate(PredicateFactory.in(UnitField.UNIT_ID, "MATU2275002"));
        println("dq============"+dq)
        HibernateApi hibernate = HibernateApi.getInstance();
        List unitList  = hibernate.findEntitiesByDomainQuery(dq);
        println("unitList list Size :"+unitList.size())
        println ("getUnitListInCv End")
        return unitList;
        //}

    }

    private StringBuffer buildXmlElement(List inUnitList, CarrierVisit inCv){
        StringBuffer elementList = new StringBuffer();
        //Build the XML Root Elements
        elementList.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        elementList.append("<ns2:snx xmlns:ns2=\"http://www.navis.com/argo\">");
        // Build the XML Shipper Consignee Elements
        println("Here:inUnitList.size()"+inUnitList.size());
        for (int k=0; k < inUnitList.size(); k++){
            //for (int k=0; k < 5; k++){
            Unit unit = (Unit) inUnitList.get(k);
            this.getShipperConsigneeDetails(unit,elementList);
        }
        // Build the XML Vessel Visit Detail element
        this.getVesselVisitDetails(inCv,elementList);
        // Build the XML Unit detail elements
        try{
            int lastElement = inUnitList.size() - 1  ;
            for (int i=0; i < inUnitList.size(); i++){
                Unit unit = (Unit) inUnitList.get(i);
                if (i.equals(lastElement)){
                    LOGGER.warn("LAST ELEMENT:" + i);
                    this.getLastUnitDetails(unit, elementList, inUnitList.size());
                }else{
                    this.getUnitDetails(unit, elementList);
                }
                try
                {
                    elementList = this.getEquipmentDetails(unit, elementList);
                    if (elementList != null)
                        elementList = this.getPositionDetails(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getRoutingDetails(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getCarrierDetails(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getHazard(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getOog(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getBookingNbr(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getReefer(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getHandlingDetails(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getContentsDetails(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getUnitEtc(unit, elementList, inCv);
                    if (elementList != null)
                        elementList = this.getFlagDetails(unit,elementList, inCv);
                    if (elementList != null)
                        elementList = this.getUnitFlexDetails(unit,elementList, inCv);
                    if (elementList != null)
                        elementList = this.getUfvDetails(unit,elementList, inCv);
                    if (elementList != null)
                        elementList = this.geTimeStamp(unit,elementList, inCv);
                    if (elementList != null)
                        elementList.append("</unit>");
                } catch (Exception e){
                    println("Exception for Unit : "+ unit.unitId +" ::"+e)
                }
            }
            elementList.append("</ns2:snx>");
            return elementList;
        }catch (Exception e){
            LOGGER.warn ("Exception in prining xml" + e);
        }
    }

    public StringBuffer getShipperConsigneeDetails(Unit inUnit, StringBuffer inElementList){

        GoodsBase goods = inUnit.getUnitGoods();
        String shipperName = "";
        String shipperId = "";
        String consigneeName = "";
        String consigneeId = "";

        ScopedBizUnit shipper = goods.getGdsShipperBzu();
        if (shipper != null){
            try{
                shipperName = shipper.getBzuName();
                shipperName = removeDoubleQuotes(shipperName);
                shipperId = shipper.getBzuId();
                shipperId = removeDoubleQuotes(shipperId);

                String shipConOpenAttr = "<shipper-consignee";
                String shipperNameAttr = " name=";
                String shipperNameValue = XML_OVERRIDE+shipperName+XML_OVERRIDE;
                String shipperIdAttr = " id=";
                String shipperIdValue =  XML_OVERRIDE+shipperId+XML_OVERRIDE;
                String shipperString = shipConOpenAttr + shipperNameAttr+ shipperNameValue + shipperIdAttr + shipperIdValue + XML_END_ELEMENT;
                shipperString = this.stripInvalidXmlCharacters(shipperString);
                inElementList.append(shipperString);
            } catch (Exception e){
                LOGGER.warn ("Exception in getting Shipper Info" + e);
            }
        }

        ScopedBizUnit consignee = goods.getGdsConsigneeBzu();
        if (consignee != null){
            try{
                consigneeName = consignee.getBzuName();
                consigneeName = removeDoubleQuotes(consigneeName);
                consigneeId = consignee.getBzuId();
                consigneeId = removeDoubleQuotes(consigneeId);

                String shipConOpenAttr = "<shipper-consignee";
                String shipperNameAttr = " name=";
                String consigneeNameValue = XML_OVERRIDE+consigneeName+XML_OVERRIDE;
                String shipperIdAttr = " id=";
                String consigneeIdValue =  XML_OVERRIDE+consigneeId+XML_OVERRIDE;
                String consigneeString = shipConOpenAttr + shipperNameAttr+ consigneeNameValue + shipperIdAttr + consigneeIdValue + XML_END_ELEMENT;
                consigneeString = this.stripInvalidXmlCharacters(consigneeString);
                inElementList.append(consigneeString);
            } catch (Exception e){
                LOGGER.warn ("Exception in getting Consignee info"+ e);
            }
        }
        return inElementList;
    }

    public StringBuffer getVesselVisitDetails(CarrierVisit inCv, StringBuffer inElementList){

        def vvd = VesselVisitDetails.resolveVvdFromCv(inCv);
        Date timeFirstAvailable = vvd.getCvdTimeFirstAvailability();
        String timeFa = "";
        if (timeFirstAvailable != null){
            timeFa = this.formatDate(timeFirstAvailable);
        }
        String vvOpenAttr = "<vessel-visit";
        String vvFirstTimeAvailabilityAttr = " time-first-availability=";
        String vvFirstTimeAvailabilityValue = XML_OVERRIDE+timeFa+XML_OVERRIDE;
        String vvIdAttr = " id=";
        String vvIdValue = XML_OVERRIDE+inCv.getCvId()+XML_OVERRIDE;
        String vesselVisitString = vvOpenAttr+vvFirstTimeAvailabilityAttr+vvFirstTimeAvailabilityValue+vvIdAttr+vvIdValue+ XML_END_ELEMENT;
        inElementList.append(vesselVisitString);
        return inElementList;
    }

    public StringBuffer getLastUnitDetails(Unit inUnit, StringBuffer inElementList, int inUnitCount){

        //unit snx-update-note="NewVes Completed Holds:(RM) unitCnt=1136 facility=HON rdsDtTime=05/01/2012 10:08:48"
        // line="ANL" freight-kind="FCL" category="IMPORT" id="UNIU5027989">

        String unitOpenAttr = "<unit snx-update-note=";
        String unitOpenValue = XML_OVERRIDE+"NewVes Detention Completed"+" unitCnt="+ inUnitCount.toString()+ " facility="+ threadFacility + " rdsDtTime="+
                this.formatRdsDate(timeNow) + XML_OVERRIDE;
        String lineAttr = " line=";
        String freightKindAttr = " freight-kind=";
        String idAttr = " id=";
        String categoryAttr = " category=";
        String freightKindValue = this.findUnitFreightKind(inUnit);
        String idValue = this.findUnitNbr(inUnit);
        String categoryValue = this.findUnitCategory(inUnit);
        ScopedBizUnit lineOp = inUnit.getUnitLineOperator();
        String lineValue = XML_OVERRIDE+lineOp.getBzuId()+XML_OVERRIDE;
        String unitString = unitOpenAttr + unitOpenValue+ lineAttr + lineValue + freightKindAttr + freightKindValue + categoryAttr + categoryValue + idAttr + idValue + ">";
        unitString = this.stripInvalidXmlCharacters(unitString);
        unitString = this.ModifyEscapeChar(unitString);
        inElementList.append(unitString);
        return inElementList;
    }

    public StringBuffer getUnitDetails(Unit inUnit, StringBuffer inElementList){

        List unitImpediments = this.getUnitImpediments(inUnit);
        String holds = "";
        String unitOpenValue;
        addNode = "N";
        if (!unitImpediments.isEmpty()) {
            for (int k=0; k < unitImpediments.size(); k++){
                ServiceImpediment imp = (ServiceImpediment) unitImpediments.get(k);
                def status = imp.getStatus();
                if (FlagStatusEnum.ACTIVE.equals(status) || FlagStatusEnum.REQUIRED.equals(status)) {
                    String holdId = imp.getFlagType().getId();
                    String flgtypPurpose = imp.getFlagType().getPurpose().getKey();
                    println ("holdId "+ holdId +" flgtypPurpose "+flgtypPurpose);
                    if (flgtypPurpose != "PERMISSION")
                    {
                        addNode = "Y";
                        if (k == 0){
                            holds = holds + holdId;
                        }else {
                            holds = holds + ","+ holdId;
                        }
                    }
                }
            }
        }
        if (addNode == "Y")
        {
            unitOpenValue = XML_OVERRIDE+"NewVes Detention Holds:("+holds+")"+XML_OVERRIDE;
        } else {
            unitOpenValue = XML_OVERRIDE+"NewVes Detention"+XML_OVERRIDE;
        }
        String unitOpenAttr = "<unit snx-update-note=";
        String lineAttr = " line=";
        String freightKindAttr = " freight-kind=";
        String idAttr = " id=";
        String categoryAttr = " category=";
        String freightKindValue = this.findUnitFreightKind(inUnit);
        String idValue = this.findUnitNbr(inUnit);
        String categoryValue = this.findUnitCategory(inUnit);
        ScopedBizUnit lineOp = inUnit.getUnitLineOperator();
        String lineValue = XML_OVERRIDE+lineOp.getBzuId()+XML_OVERRIDE;
        String unitString = unitOpenAttr + unitOpenValue+ lineAttr + lineValue + freightKindAttr + freightKindValue + categoryAttr + categoryValue + idAttr + idValue + ">";
        unitString = this.stripInvalidXmlCharacters(unitString);
        unitString = this.ModifyEscapeChar(unitString);
        inElementList.append(unitString);
        return inElementList;
    }

    public StringBuffer getEquipmentDetails(Unit inUnit, StringBuffer inElementList){
        try
        {
            Equipment eq = inUnit.getPrimaryEq();

            def unitEquipment = inUnit.getUnitPrimaryUe()
            def ueEquipmentState = unitEquipment.getUeEquipmentState()
            def equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : ''

            String equipmentOpenAttr = "<equipment";
            String equipmentRoleAttr = " role=";
            String equipmentRoleValue = XML_OVERRIDE+"PRIMARY"+XML_OVERRIDE;
            String equipmentFlex01Attr = " eq-flex-01=";
            String equipmentFlex01Value = XML_OVERRIDE+equipFlex01+XML_OVERRIDE;
            String equipmentMaterialAttr = " material=";
            String equipmentMaterialValue = this.findEquipmentMaterial(eq);
            String equipmentOperatorAttr = " operator="
            String equipmentOperatorValue=XML_OVERRIDE+eq.getEquipmentOperatorId()+XML_OVERRIDE;
            String equipmentOwnerAttr = " owner="
            String equipmentOwnerValue=XML_OVERRIDE+eq.getEquipmentOwnerId()+XML_OVERRIDE;
            String equipmentStrengthCodeAttr = " strength-code=";
            String equipmentStrengthCodeValue = XML_OVERRIDE+eq.getEqStrengthCode()+XML_OVERRIDE;
            String equipmentHeightMmAttr = " height-mm=";
            String equipmentHeightMmValue = XML_OVERRIDE+eq.getEqHeightMm().toString()+XML_OVERRIDE;
            String equipmentTareWeightKgAttr = " tare-kg=";
            String equipmentTareWeightKgValue = XML_OVERRIDE+eq.getEqTareWeightKg().toString()+XML_OVERRIDE;
            String equipmentTypeAttr = " type=";
            String equipmentTypeValue = XML_OVERRIDE+eq.getEqEquipType().eqtypId+XML_OVERRIDE;
            String equipmentClassAttr = " class=";
            String equipClass = eq.getEqClass().getKey();
            equipClass = equipClass = "CONTAINER"?"CTR":eq.getEqClass().getKey()
            String equipmentClassValue = XML_OVERRIDE+equipClass+XML_OVERRIDE
            String equipmentIdAttr = " eqid=";
            String equipmentIdValue = this.findUnitNbr(inUnit);

            String equipmentString = equipmentOpenAttr + equipmentRoleAttr+ equipmentRoleValue + equipmentFlex01Attr +
                    equipmentFlex01Value + equipmentMaterialAttr + equipmentMaterialValue+equipmentOperatorAttr+equipmentOperatorValue+
                    equipmentOwnerAttr+equipmentOwnerValue+equipmentStrengthCodeAttr+equipmentStrengthCodeValue+
                    equipmentHeightMmAttr+equipmentHeightMmValue+equipmentTareWeightKgAttr+equipmentTareWeightKgValue+
                    equipmentTypeAttr+equipmentTypeValue+equipmentClassAttr+equipmentClassValue+equipmentIdAttr+equipmentIdValue+ ">";
            equipmentString = this.stripInvalidXmlCharacters(equipmentString)
            equipmentString = this.ModifyEscapeChar(equipmentString);
            inElementList.append(equipmentString);

            String grade = ueEquipmentState != null ? ueEquipmentState.getEqsGradeID() : ''
            if (grade != null)
            {
                grade = grade.replace("EquipGrade Id:","")
            }
            String physicalOpenAttr = "<physical";
            String gradeAttr = " grade=";
            if (grade!= null)
            {
                String physicalString = physicalOpenAttr + gradeAttr+ XML_OVERRIDE+grade+XML_OVERRIDE +XML_END_ELEMENT
                inElementList.append(physicalString);
            }
            //inElementList.append("<damages");

            String damageOpenAttr = "<damages";
            String damageSevAttr = " severity="
            def eqsDamageSev = inUnit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsDamageSeverity");
            String damageSev = null;
            if (eqsDamageSev !=null)
            {
                damageSev = eqsDamageSev.getKey();
            }

            String damageString = null;
            if (damageSev != "NONE" && damageSev!= null)
            {
                damageString = damageOpenAttr+damageSevAttr+XML_OVERRIDE+damageSev+XML_OVERRIDE+XML_END_ELEMENT;
            }
            else {
                damageString = damageOpenAttr+XML_END_ELEMENT;
            }

            //println("damageString <<<<<<<<<>>>>>>>>>>>>>>>"+damageString)
            inElementList.append(damageString);


            inElementList.append("</equipment>");
        }
        catch (e)
        {
            println("Exception "+e)
        }

        return inElementList;
    }

    public StringBuffer getPositionDetails(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){

        String  positionLocationValue = "";
        String positionLocTypeValue = "";
        if (inCv != null){
            positionLocationValue = XML_OVERRIDE + inCv.getCvId()+XML_OVERRIDE;
            positionLocTypeValue = XML_OVERRIDE+ "VESSEL"+XML_OVERRIDE;
        }
        String positionOpenAttr = "<position"
        String positionSlotAttr = " slot=";
        String positionSlotValue = XML_OVERRIDE+inUnit.getUnitArrivePositionSlot()+XML_OVERRIDE;
        String positionLocationAttr = " location=";
        String positionLocTypeAttr = " loc-type=";


        String positionString = positionOpenAttr+positionSlotAttr+positionSlotValue+positionLocationAttr+positionLocationValue+
                positionLocTypeAttr+positionLocTypeValue+XML_END_ELEMENT;
        positionString = this.stripInvalidXmlCharacters(positionString)
        positionString = this.ModifyEscapeChar(positionString);
        inElementList.append(positionString);

        return inElementList;
    }

    public StringBuffer getRoutingDetails(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){

        String group = null;
        String disc="";
        String pod2=null;
        String opl="";
        String pol="";
        String destination="";
        String designatedTrucker=null;

        try
        {
            designatedTrucker=inUnit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
        }
        catch (te)
        {
            LOGGER.warn("Exception in getting trucker details " + te);
        }
        //try{

        Routing routing = inUnit.getUnitRouting();

        if (routing != null)
        {
            try
            {
                String rtgGroup = routing.getRtgGroup();
                group = rtgGroup.replace("Group Id:","");
            }
            catch (e)
            {
            }

        }

        RoutingPoint point = inUnit.getUnitRouting().getRtgPOD1();
        RoutingPoint point2 = inUnit.getUnitRouting().getRtgPOD2();
        RoutingPoint pointOpl = inUnit.getUnitRouting().getRtgOPL();
        RoutingPoint pointPol = inUnit.getUnitRouting().getRtgPOL();
        if (point != null)
        {
            disc = point.getPointId();
        }
        if (point2 != null)
        {
            pod2 = point2.getPointId();
        }
        if (pointOpl != null)
        {
            opl = pointOpl.getPointId();
        }

        if (pointPol != null)
        {
            pol = pointPol.getPointId();
        }
        destination = inUnit.getUnitGoods().getGdsDestination();
        //} catch (Exception e){
        //LOGGER.warn("Exception in getting routing details " + e);
        //}
        String rtgOpenAttr = "<routing"
        String groupAttr = " group="
        String groupValue = XML_OVERRIDE+group+XML_OVERRIDE;
        String designatedTruckerAttr = " designated-trucker="
        String designatedTruckerValue = XML_OVERRIDE+designatedTrucker+XML_OVERRIDE;
        String rtgDestAttr = " destination=";
        String rtgDestValue = XML_OVERRIDE+destination+XML_OVERRIDE;
        String rtgOplAttr = " opl=";
        String rtgOplValue = XML_OVERRIDE+ opl +XML_OVERRIDE;
        String rtgPod1Attr = " pod-1=";
        String rtgPod2Attr = " pod-2=";
        String rtgPod1Value = XML_OVERRIDE+ disc +XML_OVERRIDE;
        String rtgPod2Value = XML_OVERRIDE+ pod2 +XML_OVERRIDE;
        String rtgPolAttr = " pol=";
        String rtgPolValue = XML_OVERRIDE+ pol +XML_OVERRIDE;
        String routingString = null;


        if (designatedTrucker!= null)
        {
            routingString = designatedTruckerAttr+designatedTruckerValue+rtgDestAttr+rtgDestValue+rtgOplAttr+rtgOplValue+rtgPod1Attr+rtgPod1Value+
                    rtgPolAttr+rtgPolValue;
        }
        else {
            routingString = rtgDestAttr+rtgDestValue+rtgOplAttr+rtgOplValue+rtgPod1Attr+rtgPod1Value+
                    rtgPolAttr+rtgPolValue;
        }

        if (pod2 != null)
        {
            //println("pod2<><><><>"+pod2);
            routingString = routingString+rtgPod2Attr+rtgPod2Value
        }
        if (group != null)
        {
            routingString = rtgOpenAttr + groupAttr + groupValue + routingString
        }
        else
        {
            routingString = rtgOpenAttr + routingString
        }
        routingString = routingString + ">"
        routingString = this.stripInvalidXmlCharacters(routingString);
        routingString = this.ModifyEscapeChar(routingString);
        inElementList.append(routingString);

        return inElementList;
    }

    public StringBuffer getCarrierDetails(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){
        def fcy = com.navis.argo.ContextHelper.getThreadFacility();
        def ufv = inUnit.getUfvForFacilityCompletedOnly(fcy);
        //UnitFacilityVisit ufv = inUnit.getUnitActiveUfvNowActive();
        println("getCarrierDetails "+ufv+inUnit.unitId);
        if (ufv != null)
        {
            CarrierVisit declaredIbCv = inUnit.getUnitDeclaredIbCv();
            String declaredIbCvMode = this.findCarrierVisitMode(declaredIbCv);
            CarrierVisit actualIbCv = ufv.getInboundCarrierVisit();
            String actualIbCvMode = this.findCarrierVisitMode(actualIbCv);
            CarrierVisit declaredObCv = inUnit.getUnitRouting().getRtgDeclaredCv();
            String declaredObCvMode = this.findCarrierVisitMode(declaredObCv);
            CarrierVisit actualObCv = ufv.getUfvObCv();
            String actualObCvMode = this.findCarrierVisitMode(actualObCv);

            String cvOpenAttr = "";
            String cvIdAttr = "";
            String cvIdValue = "";
            String cvFacilityAttr = "";
            String cvFacilityValue = "";
            String cvModeAttr = "";
            String cvModeValue = "";
            String cvQualifierAttr = "";
            String cvQualiferValue = "";
            String cvDirectionAttr = "";
            String cvDirectionValue = "";
            // Declared IB CV Details Block
            cvOpenAttr = "<carrier "
            cvIdAttr = " id=";
            String dclrdCarrierIbCv = declaredIbCv;

            if (dclrdCarrierIbCv.length() > 6)
            {
                dclrdCarrierIbCv = dclrdCarrierIbCv.substring(0,6)
            }
            println ("dclrdCarrierIbCv "+ dclrdCarrierIbCv)


            cvIdValue = XML_OVERRIDE+dclrdCarrierIbCv+XML_OVERRIDE;
            cvFacilityAttr = " facility=";
            cvFacilityValue = XML_OVERRIDE+ threadFacility +XML_OVERRIDE;
            cvModeAttr = " mode=";
            cvModeValue = XML_OVERRIDE+ declaredIbCvMode +XML_OVERRIDE;
            cvQualifierAttr = " qualifier=";
            cvQualiferValue = XML_OVERRIDE+ "DECLARED" +XML_OVERRIDE;
            cvDirectionAttr = " direction=";
            cvDirectionValue = XML_OVERRIDE+ "IB" +XML_OVERRIDE;

            String cvDeclaredIbString = cvOpenAttr+cvIdAttr+cvIdValue+cvFacilityAttr+cvFacilityValue+cvModeAttr+cvModeValue+
                    cvQualifierAttr+cvQualiferValue+cvDirectionAttr+cvDirectionValue+XML_END_ELEMENT;
            cvDeclaredIbString = this.stripInvalidXmlCharacters(cvDeclaredIbString);
            inElementList.append(cvDeclaredIbString);

            String actlCarrierIbCv = actualIbCv;

            if (actlCarrierIbCv.length() > 6)
            {
                actlCarrierIbCv = actlCarrierIbCv.substring(0,6)
            }
            println ("actlCarrierIbCv "+ actlCarrierIbCv)


            // Actual IB CV Details Block
            cvOpenAttr = "<carrier "
            cvIdAttr = " id=";
            cvIdValue = XML_OVERRIDE+actlCarrierIbCv+XML_OVERRIDE;
            cvFacilityAttr = " facility=";
            cvFacilityValue = XML_OVERRIDE+ threadFacility +XML_OVERRIDE;
            cvModeAttr = " mode=";
            cvModeValue = XML_OVERRIDE+ actualIbCvMode +XML_OVERRIDE;
            cvQualifierAttr = " qualifier=";
            cvQualiferValue = XML_OVERRIDE+ "ACTUAL" +XML_OVERRIDE;
            cvDirectionAttr = " direction=";
            cvDirectionValue = XML_OVERRIDE+ "IB" +XML_OVERRIDE;

            String cvActualIbString = cvOpenAttr+cvIdAttr+cvIdValue+cvFacilityAttr+cvFacilityValue+cvModeAttr+cvModeValue+
                    cvQualifierAttr+cvQualiferValue+cvDirectionAttr+cvDirectionValue+XML_END_ELEMENT;
            cvActualIbString = this.stripInvalidXmlCharacters(cvActualIbString);
            inElementList.append(cvActualIbString);

            // Declared Ob CV Details Block
            cvOpenAttr = "<carrier "
            cvIdAttr = " id=";
            cvIdValue = XML_OVERRIDE+declaredObCv+XML_OVERRIDE;
            cvFacilityAttr = " facility=";
            cvFacilityValue = XML_OVERRIDE+ threadFacility +XML_OVERRIDE;
            cvModeAttr = " mode=";
            cvModeValue = XML_OVERRIDE+ declaredObCvMode +XML_OVERRIDE;
            cvQualifierAttr = " qualifier=";
            cvQualiferValue = XML_OVERRIDE+ "DECLARED" +XML_OVERRIDE;
            cvDirectionAttr = " direction=";
            cvDirectionValue = XML_OVERRIDE+ "OB" +XML_OVERRIDE;

            String cvDeclaredObString = cvOpenAttr+cvIdAttr+cvIdValue+cvFacilityAttr+cvFacilityValue+cvModeAttr+cvModeValue+
                    cvQualifierAttr+cvQualiferValue+cvDirectionAttr+cvDirectionValue+XML_END_ELEMENT;
            cvDeclaredObString = this.stripInvalidXmlCharacters(cvDeclaredObString);
            inElementList.append(cvDeclaredObString);

            // Actual Ob CV Details Block
            cvOpenAttr = "<carrier "
            cvIdAttr = " id=";
            cvIdValue = XML_OVERRIDE+actualObCv+XML_OVERRIDE;
            cvFacilityAttr = " facility=";
            cvFacilityValue = XML_OVERRIDE+ threadFacility +XML_OVERRIDE;
            cvModeAttr = " mode=";
            cvModeValue = XML_OVERRIDE+ actualObCvMode +XML_OVERRIDE;
            cvQualifierAttr = " qualifier=";
            cvQualiferValue = XML_OVERRIDE+ "ACTUAL" +XML_OVERRIDE;
            cvDirectionAttr = " direction=";
            cvDirectionValue = XML_OVERRIDE+ "OB" +XML_OVERRIDE;

            String cvActualObString = cvOpenAttr+cvIdAttr+cvIdValue+cvFacilityAttr+cvFacilityValue+cvModeAttr+cvModeValue+
                    cvQualifierAttr+cvQualiferValue+cvDirectionAttr+cvDirectionValue+XML_END_ELEMENT;
            cvActualObString = this.stripInvalidXmlCharacters(cvActualObString);
            if (cvActualObString != null){
                inElementList.append(cvActualObString);
                inElementList.append("</routing>");
            }
            return inElementList;
        }
    }

    public StringBuffer getHazard(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){

        def fcy = com.navis.argo.ContextHelper.getThreadFacility();
        def ufv = inUnit.getUfvForFacilityCompletedOnly(fcy);

        if (ufv != null)
        {
            if (ufv.ufvUnit.getUnitGoods() != null)
            {
                def haz = ufv.ufvUnit.getUnitGoods().getGdsHazards();
                //println("hazard<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>"+haz)

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
                        if (pkg != null)
                        {
                            pkg = pkg.getKey();
                        }
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

                    }
                    if (addtag == "Y")
                    {
                        hazardsAttrOpn = hazardsAttrOpn + hazardString + "</hazards>";
                        hazardsAttrOpn = this.stripInvalidXmlCharacters(hazardsAttrOpn);
                        hazardsAttrOpn = this.ModifyEscapeChar(hazardsAttrOpn);
                        inElementList.append(hazardsAttrOpn);
                    }
                }
            }
        }
        return inElementList;
    }

    public StringBuffer getOog(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv)
    {
        if (inUnit.getFieldValue("unitIsOog"))
        {
            String backCm = inUnit.getFieldValue("unitOogBackCm");
            String frontCm = inUnit.getFieldValue("unitOogFrontCm") ;
            String leftCm = inUnit.getFieldValue("unitOogLeftCm");
            String rightCm = inUnit.getFieldValue("unitOogRightCm");
            String topCm = inUnit.getFieldValue("unitOogTopCm");

            backCm = backCm !=null ? backCm:0;
            frontCm = frontCm !=null ? frontCm:0;
            leftCm = leftCm !=null ? leftCm:0;
            rightCm = rightCm !=null ? rightCm:0;
            topCm = topCm !=null ? topCm:0;

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
            inElementList.append(oogString);
        }

        return inElementList;
    }

    public StringBuffer getBookingNbr(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){

        String bookingId = null;
        try
        {
            bookingId = inUnit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");
        }
        catch (be)
        {
            LOGGER.warn("Exception in getting routing details " + be);
            println("Exception in getting routing details " + be);
        }

        String unitBkngOpenAttr = "<booking "
        String unitBkngAttr = "id=";
        if (bookingId != null)
        {
            bookingId = XML_OVERRIDE+bookingId+XML_OVERRIDE
            String unitBkngString = unitBkngOpenAttr+unitBkngAttr+bookingId+XML_END_ELEMENT;
            unitBkngString = this.stripInvalidXmlCharacters(unitBkngString);
            unitBkngString = this.ModifyEscapeChar(unitBkngString);
            inElementList.append(unitBkngString);
        }

        return inElementList;
    }

    public StringBuffer getReefer(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv)
    {

        def tempReq = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempRequiredC");
        def tempMax = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempLimitMaxC");
        def tempMin = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempLimitMinC");
        def tempTM1 = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTimeMonitor1");
        def tempTM2 = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTimeMonitor2");
        def tempTM3 = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTimeMonitor3");
        def tempTM4 = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTimeMonitor4");
        def tempPwrOnTime = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqLatestOnPowerTime");
        def tempCo2Pct = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqCO2Pct");
        def tempHmdtyPct = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqHumidityPct");
        def tempO2Pct = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqO2Pct");
        def tempVentReq = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqVentRequired");
        def tempVentUnit = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqVentUnit");
        def tempShowFahrenhiet = inUnit.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempShowFahrenheit");

        tempShowFahrenhiet = tempShowFahrenhiet ? "F" : "N";
        String reeferAttrOpn = "<reefer";
        String reeferString = reeferAttrOpn;
        String addtag = "N";

        if (tempShowFahrenhiet == "F")
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
        //println("reeferString <><><><><><>" + addtag +"  :   "+reeferString)

        if (addtag == "Y")
        {
            //println("Here reeferString <><><><><><>" + addtag +"  :   "+reeferString)
            reeferString = reeferString + XML_END_ELEMENT;
            reeferString = this.stripInvalidXmlCharacters(reeferString);
            reeferString = this.ModifyEscapeChar(reeferString);
            inElementList.append(reeferString);
        }
        return inElementList;
    }

    public StringBuffer getHandlingDetails(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){

        def fcy = com.navis.argo.ContextHelper.getThreadFacility();
        def ufv = inUnit.getUfvForFacilityCompletedOnly(fcy);

        String lastfreeDay = null;
        String handlingOpenAttr = "<handling"
        String handlingString = handlingOpenAttr;
        String lastfreeDayAttr = " last-free-day=";
        String handlingRemarkAttr = " remark=";
        String handlingRemark = inUnit.getUnitRemark();
        if (ufv !=null)
        {
            lastfreeDay = ufv.getFieldValue("ufvCalculatedLastFreeDay");
            if (lastfreeDay != null)
            {
                lastfreeDay = lastfreeDay.replace("!","")
            }

        }

        addNode = "N";
        if (lastfreeDay != null)
        {
            addNode = "Y";
            try
            {
                Date date = new SimpleDateFormat("yyyy-MMM-dd", Locale.ENGLISH).parse(lastfreeDay);
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                lastfreeDay = df.format(date);
            }
            catch (Exception de){
                LOGGER.warn ("Exception in while converting date "+ lastfreeDay+":"+de);
                lastfreeDay = null;
                addNode = "N";
            }
        }
        if (addNode == "Y")
        {
            lastfreeDay=XML_OVERRIDE+lastfreeDay+XML_OVERRIDE;
        }
        else {
            addNode = "N";
        }

        if (handlingRemark != null)
        {
            handlingRemark = removeDoubleQuotes(handlingRemark);
        }
        else {
            handlingRemark = " "
        }
        if (addNode == "Y" )
        {
            handlingString = handlingString + lastfreeDayAttr + lastfreeDay;
        }

        String handlingRemarkValue = XML_OVERRIDE+handlingRemark+XML_OVERRIDE;
        handlingString = handlingString+handlingRemarkAttr+handlingRemarkValue+ XML_END_ELEMENT;
        handlingString = this.stripInvalidXmlCharacters(handlingString);
        handlingString = this.ModifyEscapeChar(handlingString);
        inElementList.append(handlingString);

        return inElementList;
    }

    public StringBuffer getContentsDetails(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){

        GoodsBase goods = inUnit.getUnitGoods();
        String blNbr =  goods.getGdsBlNbr();
        ScopedBizUnit shipper = goods.getGdsShipperBzu();
        String shipperName = "";
        String shipperId = "";
        String consigneeName = "";
        String consigneeId = "" ;
        String contentsShipperNameAttr = " shipper-name=";
        String contentsShipperIdAttr = " shipper-id=";
        String contentsConsigneeNameAttr = " consignee-name=";
        String contentsConsigneeIdAttr = " consignee-id=";
        String contentsConsigneeNameValue = "";
        String contentsConsigneeIdValue = "";
        String contentsShipperNameValue = "";
        String contentsShipperIdValue = "";

        ScopedBizUnit consignee = goods.getGdsConsigneeBzu();
        if (consignee != null){
            try{
                consigneeName = consignee.getBzuName();
                consigneeName = removeDoubleQuotes(consigneeName);
                consigneeId = consignee.getBzuId();
                consigneeId = removeDoubleQuotes(consigneeId);

                contentsConsigneeNameValue = XML_OVERRIDE+consigneeName+XML_OVERRIDE;
                contentsConsigneeIdValue =  XML_OVERRIDE+consigneeId+XML_OVERRIDE;
            } catch (Exception e){
                LOGGER.warn ("Exception in getting Consignee info"+ e);
            }
        }
        if (blNbr == null || blNbr.startsWith("DO NOT EDIT")){
            blNbr = "";
        }
        String gdsWeight = inUnit.getUnitGoodsAndCtrWtKg().toString();
        String contentsOpenAttr = "<contents";
        String contentsCommodityNameAttr = " commodity-name=";
        String commodity = inUnit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
        String commodityName = inUnit.getFieldValue("unitGoods.gdsCommodity.cmdyShortName")
        String contentsCommodityNameValue = XML_OVERRIDE+commodityName+XML_OVERRIDE;
        String contentsCommodityIdAttr = " commodity-id=";
        String contentsCommodityIdValue = XML_OVERRIDE+commodity+XML_OVERRIDE;
        String contentsBlNbrAttr = " bl-nbr=";
        String contentsBlNbrValue = XML_OVERRIDE+blNbr+XML_OVERRIDE;
        String contentsWeightKgAttr = " weight-kg=";
        String contentsWeightKgValue = XML_OVERRIDE+gdsWeight+XML_OVERRIDE;

        if (shipper != null){
            try{
                shipperName = shipper.getBzuName();
                shipperName = removeDoubleQuotes(shipperName);
                shipperId = shipper.getBzuId();
                shipperId = removeDoubleQuotes(shipperId);

                contentsShipperNameValue = XML_OVERRIDE+shipperName+XML_OVERRIDE;
                contentsShipperIdValue =  XML_OVERRIDE+shipperId+XML_OVERRIDE;
            } catch (Exception e){
                LOGGER.warn ("Exception in getting Shipper Info" + e);
            }
        }
        String handlingString = contentsOpenAttr+contentsCommodityNameAttr+contentsCommodityNameValue+contentsCommodityIdAttr+contentsCommodityIdValue;
        if (shipper != null){
            handlingString = handlingString+contentsShipperNameAttr+contentsShipperNameValue+contentsShipperIdAttr+contentsShipperIdValue;

        }
        if (consignee != null){
            handlingString = handlingString+contentsConsigneeNameAttr+contentsConsigneeNameValue+contentsConsigneeIdAttr+contentsConsigneeIdValue;
        }
        //if (blNbr !=null){
        handlingString = handlingString+contentsBlNbrAttr+contentsBlNbrValue;
        //}
        if (gdsWeight != null){
            handlingString = handlingString+contentsWeightKgAttr+contentsWeightKgValue;
        }

        handlingString = handlingString+XML_END_ELEMENT;
        handlingString = this.stripInvalidXmlCharacters(handlingString);
        inElementList.append(handlingString);
        //Add Seal Element
        String sealsOpenAttr = "<seals";
        String seals1Attr = " seal-1=";
        String seals1Value = XML_OVERRIDE+inUnit.getUnitSealNbr1()+XML_OVERRIDE;
        if (inUnit.getUnitSealNbr1() != null){
            String sealsString = sealsOpenAttr+seals1Attr+seals1Value+XML_END_ELEMENT;
            inElementList.append(sealsString);
        }
        return inElementList;
    }

    public StringBuffer getUnitEtc(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv)
    {

        def drayStatus = inUnit.getFieldValue("unitDrayStatus");
        if (drayStatus != null)
        {
            drayStatus = drayStatus.getKey();
        }

        String requiredPower = inUnit.getFieldValue("unitRequiresPower") ? "Y" : "N";

        String etcAttrOpn = "<unit-etc";
        String etcString = etcAttrOpn;
        if (requiredPower == "Y")
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
            inElementList.append(etcString);
        }
        return inElementList;
    }

    public getFlagDetails = {Unit inUnit, StringBuffer inElementList, CarrierVisit inCv ->

        String flagOpenCloseAttr = "<flags/>";
        String flagOpenAttr = "<flags>";
        String flagCloseAttr = "</flags>";
        String holdIdValue = "";
        String holdString = "";
        addNode = "N";
        List unitImpediments = this.getUnitImpediments(inUnit);

        if (unitImpediments.isEmpty()) {
            return inElementList;
        }
        for (ServiceImpediment imp : unitImpediments) {
            def status = imp.getStatus();
            if (FlagStatusEnum.ACTIVE.equals(status) || FlagStatusEnum.REQUIRED.equals(status)) {
                String holdOpenAttr = "<hold";
                String holdIdAttr = " id=";
                String holdId = imp.getFlagType().getId();
                String flgtypPurpose = imp.getFlagType().getPurpose().getKey();
                println ("holdId "+ holdId +" flgtypPurpose "+flgtypPurpose);
                if (flgtypPurpose != "PERMISSION")
                {
                    addNode = "Y";
                    holdIdValue = XML_OVERRIDE + holdId + XML_OVERRIDE;
                    holdString = holdString + holdOpenAttr + holdIdAttr + holdIdValue + XML_END_ELEMENT;
                }
            }
        }

        if (addNode == "Y")
        {
            holdString = this.stripInvalidXmlCharacters(holdString);
            holdString = this.ModifyEscapeChar(holdString);
            holdString = flagOpenAttr + holdString + flagCloseAttr
            inElementList.append(holdString);
        }

        return inElementList;
    }

    private List getUnitImpediments (Unit inUnit){

        ServicesManager serviceManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
        List unitImpediments = serviceManager.getImpedimentsForEntity(inUnit);
        return unitImpediments;
    }

    private String formatDate(Date inDate) {
        SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat hhmmss = new SimpleDateFormat("HH:mm:ss.SSS");
        String sDate = yyyyMMdd.format(inDate) + "T" + hhmmss.format(inDate);
        return sDate
    }

    private String formatRdsDate(Date inDate) {
        SimpleDateFormat yyyyMMdd = new SimpleDateFormat("MM/dd/yyyy");
        SimpleDateFormat hhmmss = new SimpleDateFormat("HH:mm:ss");
        String rdsDate = yyyyMMdd.format(inDate) + " " + hhmmss.format(inDate);
        return rdsDate
    }

    public String findCarrierVisitMode (CarrierVisit inCv){

        final LocTypeEnum carrierMode = inCv.getCvCarrierMode();
        if (carrierMode.equals(LocTypeEnum.VESSEL)){
            return "VESSEL";
        }
        if (carrierMode.equals(LocTypeEnum.TRUCK)){
            return "TRUCK";
        }
        if (carrierMode.equals(LocTypeEnum.TRAIN)){
            return "TRAIN";
        }
        if (carrierMode.equals(LocTypeEnum.YARD)){
            return "YARD";
        }
        if (carrierMode.equals(LocTypeEnum.RAILCAR)){
            return "TRAIN";
        }
        else{
            return "UNKNOWN";
        }
    }

    public String findEquipmentMaterial(Equipment inEq){

        String material="";
        if ((EquipMaterialEnum.ALUMINUM.equals(inEq.getEqMaterial()))){
            material = XML_OVERRIDE+"ALUMINUM"+XML_OVERRIDE;
        }
        if ((EquipMaterialEnum.STEEL.equals(inEq.getEqMaterial()))){
            material = XML_OVERRIDE+"STEEL"+XML_OVERRIDE;
        }
        if ((EquipMaterialEnum.UNKNOWN.equals(inEq.getEqMaterial()))){
            material = XML_OVERRIDE+"UNKNOWN"+XML_OVERRIDE;
        }
        return material;
    }

    public String findUnitNbr(Unit inUnit){

        Equipment eq = inUnit.getPrimaryEq();
        String  equipId = eq.getEqIdFull();
        equipId = XML_OVERRIDE+equipId+XML_OVERRIDE;
        return equipId;
    }

    public String findUnitCategory(Unit inUnit){

        String category="";
        if ((UnitCategoryEnum.EXPORT.equals(inUnit.getUnitCategory()))){
            category = XML_OVERRIDE+"EXPORT"+XML_OVERRIDE;
        }
        if ((UnitCategoryEnum.IMPORT.equals(inUnit.getUnitCategory()))){
            category = XML_OVERRIDE+"IMPORT"+XML_OVERRIDE;
        }
        if ((UnitCategoryEnum.THROUGH.equals(inUnit.getUnitCategory()))){
            category = XML_OVERRIDE+"THROUGH"+XML_OVERRIDE;
        }
        if ((UnitCategoryEnum.TRANSSHIP.equals(inUnit.getUnitCategory()))){
            category = XML_OVERRIDE+"TRANSSHIP"+XML_OVERRIDE;
        }

        if (category == "")
        {
            def categoryOther = inUnit.getUnitCategory();
            categoryOther = categoryOther != null ? categoryOther.getKey() : ""
            categoryOther = categoryOther == "STRGE" ? "STORAGE" : ""
            category = XML_OVERRIDE+categoryOther+XML_OVERRIDE;
        }

        return category;
    }

    public String findUnitFreightKind(Unit inUnit){

        String freightKind="";
        if ((FreightKindEnum.MTY.equals(inUnit.getUnitFreightKind()))){
            freightKind = XML_OVERRIDE+"MTY"+XML_OVERRIDE;
        }
        if ((FreightKindEnum.LCL.equals(inUnit.getUnitFreightKind()))){
            freightKind = XML_OVERRIDE+"LCL"+XML_OVERRIDE;
        }
        if ((FreightKindEnum.FCL.equals(inUnit.getUnitFreightKind()))){
            freightKind = XML_OVERRIDE+"FCL"+XML_OVERRIDE;
        }
        return freightKind;
    }

    public String getTimeFormat(String inDate)
    {
        String outDate = null;
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        if (inDate != null)
        {
            try
            {
                Date date = new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy", Locale.ENGLISH).parse(inDate);
                outDate = df.format(date);
            }
            catch (de)
            {
                Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.ENGLISH).parse(inDate);
                outDate = df.format(date);
            }
            outDate = outDate + "T00:00:00.000";
        }
        return outDate;
    }

    public StringBuffer getUfvDetails(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){

        def fcy = com.navis.argo.ContextHelper.getThreadFacility();
        def ufv = inUnit.getUfvForFacilityCompletedOnly(fcy);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        if (ufv != null){
            String addTag = "N";

            String ufvflexdate1 = ufv.getFieldValue("ufvFlexDate01");
            String ufvflexdate2 = ufv.getFieldValue("ufvFlexDate02");
            String ufvflexdate3 = ufv.getFieldValue("ufvFlexDate03");
            String ufvflexdate4 = ufv.getFieldValue("ufvFlexDate04");
            String ufvflexdate5 = ufv.getFieldValue("ufvFlexDate05");
            String ufvflexdate6 = ufv.getFieldValue("ufvFlexDate06");
            String ufvflexdate7 = ufv.getFieldValue("ufvFlexDate07");
            String ufvflexdate8 = ufv.getFieldValue("ufvFlexDate08");

            String ufvflexString1 = ufv.getFieldValue("ufvFlexString1")!= null ? removeDoubleQuotes(ufv.getFieldValue("ufvFlexString1")) : null;
            String ufvflexString2 = ufv.getFieldValue("ufvFlexString2")!= null ? removeDoubleQuotes(ufv.getFieldValue("ufvFlexString2")) : null;
            String ufvflexString3 = ufv.getFieldValue("ufvFlexString3")!= null ? removeDoubleQuotes(ufv.getFieldValue("ufvFlexString3")) : null;
            String ufvflexString4 = ufv.getFieldValue("ufvFlexString4")!= null ? removeDoubleQuotes(ufv.getFieldValue("ufvFlexString4")) : null;

            String date1Attr = " ufv-flex-date-1=";
            String date2Attr = " ufv-flex-date-2=";
            String date3Attr = " ufv-flex-date-3=";
            String date4Attr = " ufv-flex-date-4=";
            String date5Attr = " ufv-flex-date-5=";
            String date6Attr = " ufv-flex-date-6=";
            String date7Attr = " ufv-flex-date-7=";
            String date8Attr = " ufv-flex-date-8=";

            String string1Attr = " uvf-flex-1=";
            String string2Attr = " uvf-flex-2=";
            String string3Attr = " uvf-flex-3=";
            String string4Attr = " uvf-flex-4=";

            String string1Value = XML_OVERRIDE+ufvflexString1+XML_OVERRIDE;
            String string2Value = XML_OVERRIDE+ufvflexString2+XML_OVERRIDE;
            String string3Value = XML_OVERRIDE+ufvflexString3+XML_OVERRIDE;
            String string4Value = XML_OVERRIDE+ufvflexString4+XML_OVERRIDE;


            String ufvFlexOpenAttr = "<ufv-flex"
            String ufvFlexString = ufvFlexOpenAttr;

            if (ufvflexdate8 != null)
            {
                ufvflexdate8 = this.getTimeFormat(ufvflexdate8)
                String date8Value = XML_OVERRIDE+ufvflexdate8+XML_OVERRIDE;
                ufvFlexString = ufvFlexString+date8Attr+date8Value;
                addTag = "Y";
            }
            if (ufvflexdate7 != null)
            {
                ufvflexdate7 = this.getTimeFormat(ufvflexdate7)
                String date7Value = XML_OVERRIDE+ufvflexdate7+XML_OVERRIDE;
                ufvFlexString = ufvFlexString+date7Attr+date7Value;
                addTag = "Y";
            }
            if (ufvflexdate6 != null)
            {
                ufvflexdate6 = this.getTimeFormat(ufvflexdate6)
                String date6Value = XML_OVERRIDE+ufvflexdate6+XML_OVERRIDE;
                ufvFlexString = ufvFlexString+date6Attr+date6Value;
                addTag = "Y";
            }
            if (ufvflexdate5 != null)
            {
                ufvflexdate5 = this.getTimeFormat(ufvflexdate5)
                String date5Value = XML_OVERRIDE+ufvflexdate5+XML_OVERRIDE;
                ufvFlexString = ufvFlexString+date5Attr+date5Value;
                addTag = "Y";
            }
            if (ufvflexdate4 != null)
            {
                ufvflexdate4 = this.getTimeFormat(ufvflexdate4)
                String date4Value = XML_OVERRIDE+ufvflexdate4+XML_OVERRIDE;
                ufvFlexString = ufvFlexString+date4Attr+date4Value;
                addTag = "Y";
            }
            if (ufvflexdate3 != null)
            {
                ufvflexdate3 = this.getTimeFormat(ufvflexdate3)
                String date3Value = XML_OVERRIDE+ufvflexdate3+XML_OVERRIDE;
                ufvFlexString = ufvFlexString+date3Attr+date3Value;
                addTag = "Y";
            }
            if (ufvflexdate2 != null)
            {
                ufvflexdate2 = this.getTimeFormat(ufvflexdate2)
                String date2Value = XML_OVERRIDE+ufvflexdate2+XML_OVERRIDE;
                ufvFlexString = ufvFlexString+date2Attr+date2Value;
                addTag = "Y";
            }
            if (ufvflexdate1 != null)
            {
                ufvflexdate1 = this.getTimeFormat(ufvflexdate1)
                String date1Value = XML_OVERRIDE+ufvflexdate1+XML_OVERRIDE;
                ufvFlexString = ufvFlexString+date1Attr+date1Value;
                addTag = "Y";
            }

            if (ufvflexString1 != null)
            {
                ufvFlexString = this.removeDoubleQuotes(ufvFlexString);
                ufvFlexString = ufvFlexString+string1Attr+string1Value;
                addTag = "Y";
            }

            if (ufvflexString2 != null)
            {
                ufvFlexString = ufvFlexString+string2Attr+string2Value;
                addTag = "Y";
            }

            if (ufvflexString3 != null)
            {
                ufvFlexString = ufvFlexString+string3Attr+string3Value;
                addTag = "Y";
            }

            if (ufvflexString4 != null)
            {
                ufvFlexString = ufvFlexString+string4Attr+string4Value;
                ufvFlexString = this.stripInvalidXmlCharacters(ufvFlexString);
                addTag = "Y";
            }

            if (addTag == "Y")
            {
                ufvFlexString = ufvFlexString + XML_END_ELEMENT;
                //fvFlexString = this.stripInvalidXmlCharacters(ufvFlexString);
                ufvFlexString = this.ModifyEscapeChar(ufvFlexString);
                inElementList.append(ufvFlexString);
            }

        }
        return inElementList;
    }

    public StringBuffer getUnitFlexDetails(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv){

        String  unitflexString1 = "";
        String	unitflexString2 = "";
        String	unitflexString3 = "";
        String  unitflexString4 = "";
        String	unitflexString5 = "";
        String	unitflexString6 = "";
        String  unitflexString7 = "";
        String	unitflexString8 = "";
        String	unitflexString9 = "";
        String	unitflexString10 = "";
        String	unitflexString12 = "";

        unitflexString1 = inUnit.getFieldValue("unitFlexString01") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString01")): null;
        unitflexString2 = inUnit.getFieldValue("unitFlexString02") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString02")): null;
        unitflexString3 = inUnit.getFieldValue("unitFlexString03") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString03")): null;
        unitflexString4 = inUnit.getFieldValue("unitFlexString04") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString04")): null;
        unitflexString5 = inUnit.getFieldValue("unitFlexString05") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString05")): null;
        unitflexString6 = inUnit.getFieldValue("unitFlexString06") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString06")): null;
        unitflexString7 = inUnit.getFieldValue("unitFlexString07") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString07")): null;
        unitflexString8 = inUnit.getFieldValue("unitFlexString08") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString08")): null;
        unitflexString9 = inUnit.getFieldValue("unitFlexString09") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString09")): null;
        unitflexString10 = inUnit.getFieldValue("unitFlexString10") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString10")): null;
        unitflexString12 = inUnit.getFieldValue("unitFlexString12") != null ? removeDoubleQuotes(inUnit.getFieldValue("unitFlexString12")): null;

        String unitFlexOpenAttr = "<unit-flex"
        String string1Attr = " unit-flex-1=";
        String string2Attr = " unit-flex-2=";
        String string3Attr = " unit-flex-3=";
        String string4Attr = " unit-flex-4=";
        String string5Attr = " unit-flex-5=";
        String string6Attr = " unit-flex-6=";
        String string7Attr = " unit-flex-7=";
        String string8Attr = " unit-flex-8=";
        String string9Attr = " unit-flex-9=";
        String string10Attr = " unit-flex-10=";
        String string12Attr = " unit-flex-12=";

        String string1Value = XML_OVERRIDE+unitflexString1+XML_OVERRIDE;
        String string2Value = XML_OVERRIDE+unitflexString2+XML_OVERRIDE;
        String string3Value = XML_OVERRIDE+unitflexString3+XML_OVERRIDE;
        String string4Value = XML_OVERRIDE+unitflexString4+XML_OVERRIDE;
        String string5Value = XML_OVERRIDE+unitflexString5+XML_OVERRIDE;
        String string6Value = XML_OVERRIDE+unitflexString6+XML_OVERRIDE;
        String string7Value = XML_OVERRIDE+unitflexString7+XML_OVERRIDE;
        String string8Value = XML_OVERRIDE+unitflexString8+XML_OVERRIDE;
        String string9Value = XML_OVERRIDE+unitflexString9+XML_OVERRIDE;
        String string10Value = XML_OVERRIDE+unitflexString10+XML_OVERRIDE;
        String string12Value = XML_OVERRIDE+unitflexString12+XML_OVERRIDE;
        try {

            //unitflexString6 = "\"MCMILLON, STEVEN & P\"";
            //unitflexString6 = removeDoubleQuotes(unitflexString6);
            //string6Value = XML_OVERRIDE+unitflexString6+XML_OVERRIDE;

            String unitFlexString = unitFlexOpenAttr;

            if (unitflexString12 != null)
            {
                unitFlexString = unitFlexString+string12Attr+string12Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            if (unitflexString10 != null)
            {
                unitFlexString = unitFlexString+string10Attr+string10Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            if (unitflexString9 != null)
            {
                unitFlexString = unitFlexString+string9Attr+string9Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }

            if (unitflexString8 != null)
            {
                unitFlexString = unitFlexString+string8Attr+string8Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            if (unitflexString7 != null)
            {
                unitFlexString = unitFlexString+string7Attr+string7Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            //LOGGER.warn("unitflexString6::::::" + unitflexString6);
            if (unitflexString6 != null)
            {
                unitFlexString = unitFlexString+string6Attr+string6Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            if (unitflexString5 != null)
            {
                unitFlexString = unitFlexString+string5Attr+string5Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            if (unitflexString4 != null)
            {
                unitFlexString = unitFlexString+string4Attr+string4Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            if (unitflexString3 != null)
            {
                unitFlexString = unitFlexString+string3Attr+string3Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            if (unitflexString2 != null)
            {
                unitFlexString = unitFlexString+string2Attr+string2Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }
            if (unitflexString1 != null)
            {
                unitFlexString = unitFlexString+string1Attr+string1Value;
                unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
            }

            if (unitflexString1 != null || unitflexString2 != null || unitflexString3 != null || unitflexString4 != null || unitflexString5 != null || unitflexString6 != null
                    || unitflexString7 != null || unitflexString8 != null || unitflexString9 != null || unitflexString10 != null || unitflexString12 != null)
            {
                unitFlexString = unitFlexString+XML_END_ELEMENT;
                //unitFlexString = this.stripInvalidXmlCharacters(unitFlexString);
                unitFlexString = this.ModifyEscapeChar(unitFlexString);
                inElementList.append(unitFlexString);
            }
        }
        catch (e){

            LOGGER.warn("unitFlexString Error ::::::" + e);
        }

        return inElementList;
    }

    public StringBuffer geTimeStamp(Unit inUnit, StringBuffer inElementList, CarrierVisit inCv)
    {

        def ufv = inUnit.getUnitActiveUfvNowActive();
        if (ufv !=null)
        {
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
                inElementList.append(timeStampString);
            }
        }
        return inElementList;
    }


    public String stripInvalidXmlCharacters(String input){
        String specialCharacter = "&";
        try {
            if (input.contains(specialCharacter)) {
                input = input.replaceAll("&", "&amp; ");
                //LOGGER.warn("sb:" + input);
            }

            if (input.contains("null")) {
                input = input.replaceAll("null", " ");
                //LOGGER.warn("sb:" + input);
            }
        }
        catch (e){

        }
        return input;
    }

    public String removeDoubleQuotes(String input){
        String specialCharacter = "&";
        try {
            if (input.contains("\"")) {
                input = input.replaceAll("\"", "#quot;");
            }
            if (input.contains("<")) {
                input = input.replaceAll("<", "#lt; ");
            }
            if (input.contains(">")) {
                input = input.replaceAll(">", "#gt; ");
            }
        }
        catch (e){

        }
        return input;
    }

    public String ModifyEscapeChar(String input){
        try {
            if (input.contains("#quot")) {
                input = input.replaceAll("#quot;", "&quot;");
            }
            if (input.contains("#lt")) {
                input = input.replaceAll("#lt;", "&lt; ");
            }
            if (input.contains("#gt")) {
                input = input.replaceAll("#gt;", "&gt; ");
            }
        }
        catch (e){

        }
        return input;
    }

    private static final Logger LOGGER = Logger.getLogger(MATBargeYBDepartUnits.class);
}