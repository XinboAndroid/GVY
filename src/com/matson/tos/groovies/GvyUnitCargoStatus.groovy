/*
*****************************************************************************
* Srno    Date               Changer	         Change Description
* A1      02/03/09        Glenn Raposo              Added Check to compare value change
* A2      02/09/2009    Glenn Raposo             i)condition not to updt GoodsConsigneeName twice
                                                                       ii)Repalce Dray Status Code with Desc
* A3      02/11/2009    Glenn Raposo             Repalced the Destination with Discharge Port
* A4      04/23/2009    Steven Bauer		 Added location
* A5      04/28/2009	Steven Bauer		 Added event time
*A6       05/01/2009    Steven Bauer             Escape quite in updt fields.
*A7       11/27/2009    Glenn Raposo         Added MetaId mapping to fetch NonBuiltIn Evnt previous value
*A8       12/08/2009    GR                   Commented out Multiple Xml Escape char Checks
*A9       01/12/2009    GR                   Added RelToParty to Consignee Value
*****************************************************************************
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.api.GroovyApi
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.framework.util.DateUtil;
import com.navis.argo.business.atoms.EquipClassEnum;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.argo.business.reference.Shipper;

public class GvyUnitCargoStatus {

    def prevUnitHolds = ""
    def activeUnitHldList = ""
    def gvyCmisCrsUtil = null;
    def gvyBase = null;

    public String sendXml(String N4MsgType,Object event)
    {
        try{
            def unit = event.getEntity();
            if ( ! EquipClassEnum.CONTAINER.equals( unit.getPrimaryEq().getEqClass())){
                return "Not a container. No Cargo Status email sent out.";
            }
            else if(UnitVisitStateEnum.DEPARTED.equals(unit.getFieldValue("UnitVisitState"))){
                return "Master visit State Departed. No Cargo Status email sent out.";
            }

            gvyBase = new GroovyInjectionBase()
            def gvyMsgFmt = gvyBase.getGroovyClassInstance("GvyMsgFormatter");
            gvyCmisCrsUtil = gvyBase.getGroovyClassInstance("GvyCmisCargoStatusUtil");
            def gvyEventUtil = gvyBase.getGroovyClassInstance("GvyEventUtil")
            def _msgType =  'CARGO_STATUS';
            def api = new GroovyApi();

            //Event ID and Recorder
            def editedBy = event.getEvent().getFieldValue("evntAppliedBy") ;

            def eventNote = event.getEvent().getFieldValue("evntNote") ;
            if(eventNote == null){
                eventNote = "";
            }else{
                eventNote = gvyCmisCrsUtil.replaceQuotesUtil(eventNote)
            }
            def eventTypeId = event.getEvent().getEventTypeId()

            //Sets Previous and Current Hold Values
            getUnitHoldsValues(eventTypeId,unit,gvyBase,gvyCmisCrsUtil)

            //Fields Change that Generate Cargo status Report

            def unitTags = ['POD', 'FreightKind', 'CommodityDescription','Destination',
                            'GoodsConsigneeName', 'UnitHoldsAndPermissions', 'DrayStatus',
                            'SpecialStow', 'RoutingGroup', 'GoodsBlNbr', 'UnitRemark', 'UnitNbr','UnitFlexString02'];

            def xmlMsg = new StringBuffer();

            def tempMsg = event.getPropertyXml( "GroovyMsg", unitTags);

            //Appending Xml Closing tag below
            xmlMsg.append( tempMsg.substring( 0, tempMsg.length()-2));

            //println("First cut :"+xmlMsg)
            //Do not send CS Report If UnitRemark changed on Supplemental File
            def gvyEventObj = event.getEvent()
            def doer = gvyEventObj.getEvntAppliedBy()
            def isBuiltInEvnt = event.getEvent().getEvntEventType().getEvnttypeIsBuiltInEvent()

            //To Check if the Field changed
            boolean fieldChange = false
            //
            HashMap metaIdMap = getMetaIdList(isBuiltInEvnt)


            for( eachAttr in unitTags)
            {
                //1] Check if any Field in the Field Set Changed  2] Check if the Previous and Update value is different
                def metaId = metaIdMap.get(eachAttr)
                metaId = metaId != null ? metaId : ""

                boolean isFieldChanged = isBuiltInEvnt ? event.wasFieldChanged(eachAttr) : gvyEventUtil.wasFieldChanged(event,metaId)

                def prevFldValue = gvyEventUtil.getPreviousPropertyAsString(event,metaId);
                prevFldValue = prevFldValue != null ? prevFldValue : ""

                if(isFieldChanged && eachAttr.equals("GoodsConsigneeName") && metaId.equals("gdsConsigneeBzu")){
                    def prevValue = getMetaIdString(gvyEventUtil,event,gvyBase,metaId)
                    def shipper = prevValue != null ? Shipper.findShipper(prevValue) : null
                    prevFldValue = shipper != null ? shipper.bzuName : ""
                }
                else if(isFieldChanged && eachAttr.equals("CommodityDescription")) {
                    prevFldValue = lookupCommodity(prevFldValue);
                }
                else if(isFieldChanged && eachAttr.equals("RoutingGroup")){
                    prevFldValue = getMetaIdString(gvyEventUtil,event,gvyBase,metaId)
                    prevFldValue = prevFldValue != null ? prevFldValue : ""
                }
                else if(isFieldChanged && eachAttr.equals("SpecialStow")){
                    prevFldValue = getMetaIdString(gvyEventUtil,event,gvyBase,metaId)
                    prevFldValue = prevFldValue != null ? prevFldValue : ""
                }

                def currFldValue = event.getProperty(eachAttr);
                currFldValue = currFldValue != null ? currFldValue : ""



                //println("ExternalTag:"+eachAttr+"  metaId:"+metaId+"  FieldChanged:"+isFieldChanged+"  CurrentValue:"+currFldValue+"   PreviousValue:"+prevFldValue)

                if (isFieldChanged && !currFldValue.equals(prevFldValue))
                {
                    //println("eachAttr Changed : "+eachAttr)
                    def preValue = event.getPreviousPropertyAsString(eachAttr)
                    if(eachAttr.equals("CommodityDescription")) {
                        preValue = prevFldValue;
                    }
                    else if(eachAttr.equals("GoodsConsigneeName") || metaId.equals("gdsConsigneeBzu")){
                        preValue = metaId.equals("gdsConsigneeBzu") ? prevFldValue : getPrevValue( event, metaId)
                    }else if (eachAttr.equals("DrayStatus")){
                        preValue = getDrayDesc(preValue)
                    }else if (metaId.equals("gdsDestination") || eachAttr.equals("POD")){
                        preValue = metaId.equals("gdsDestination") ? prevFldValue : getPrevDischargePort(gvyBase,event)
                    }else if(eachAttr.equals("RoutingGroup")){
                        preValue = prevFldValue
                    }else if(eachAttr.equals("SpecialStow")){
                        preValue = prevFldValue
                    }

                    if ( preValue == null || preValue.size() == 0){
                        preValue = "";
                    }
                    //A8 - preValue = gvyCmisCrsUtil.replaceQuotesUtil(preValue);
                    if(!eachAttr.equals("UnitHoldsAndPermissions")) {
                        xmlMsg.append(gvyCmisCrsUtil.formatField(' updt_'+eachAttr, preValue))
                    }
                    fieldChange = true
                }
            }

            // IF No Field's were updated then Do not proceed further
            if(!fieldChange){  //Condition Check for Holds and Release Event
                fieldChange = eventTypeId.endsWith("_HOLD") || eventTypeId.endsWith("_RELEASE") ? true : false
            }
            if(!fieldChange){
                return "NO FIELDS UPDATE - DO NOT GENERATE CS REPORT"
            }

            def category = unit.getFieldValue("unitCategory");
            api.log( "unitCategory = " + category);
            def vesvoy = "";
            if ( UnitCategoryEnum.IMPORT.equals( category) || UnitCategoryEnum.TRANSSHIP.equals( category))
                vesvoy = unit.getFieldValue("unitDeclaredIbCv.cvId");
            else if ( UnitCategoryEnum.EXPORT.equals(category) || UnitCategoryEnum.THROUGH.equals(category) )
                vesvoy = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId");

            def cnee = ""
            // explicitly appending the Holds previous value each time as Reporting tag not working
            def updt_UnitHoldsAndPermissions = " updt_UnitHoldsAndPermissions=\""+prevUnitHolds.trim()+"\""
            //Check for user in No Email Group
            def userRole = gvyCmisCrsUtil.getCsrUserRole(doer)
            userRole = userRole == null ? 'Email' : userRole
            def userRoleAttr = "userRole=\""+userRole+"\""

            // get local time
            def inTime = event.getEvent().getEventTime();
            def timezone = unit.getUnitComplex().getTimeZone();
            def eventTime = DateUtil.convertDateToLocalTime(inTime, timezone);

            //Replacing Escape Characters
            //A8 - def repXmlMsg = gvyCmisCrsUtil.replaceQuotes(""+xmlMsg)
            def repXmlMsg = ""+xmlMsg

            //Overriding Dray Status & Unit Holds from event.getPropertyXml()
            def curDrayStatus = gvyCmisCrsUtil.getFieldValues(repXmlMsg,"DrayStatus=")
            def curDrayStat = getDrayDesc(curDrayStatus)

            def location = null;
            try {
                location = getLocationStatus(unit);
            } catch (Exception e) {}

            if(location != null) location = " location='"+location+"' ";
            else location = "";


            def fmtXmlMsg = gvyCmisCrsUtil.eventSpecificFieldValue(repXmlMsg,"DrayStatus=",curDrayStat)
            fmtXmlMsg = gvyCmisCrsUtil.eventSpecificFieldValue(fmtXmlMsg,"UnitHoldsAndPermissions=",activeUnitHldList)

            def finalmsg = fmtXmlMsg + cnee +updt_UnitHoldsAndPermissions+' '+userRoleAttr+location+"  Vesvoy='" + vesvoy + "' editedBy='" +
                    editedBy + "' msgType='" + N4MsgType + "' eventTime='" + eventTime + "' eventNote='"+eventNote+"'/>";
            //println("finalmsg :"+finalmsg)
            finalmsg = isBuiltInEvnt ? appendRelToPartyToConsigneeValue(finalmsg) : finalmsg
            return finalmsg
        }catch(Exception e){
            e.printStackTrace()
        }
    } //Method Ends


    public String sendXml(String N4MsgType,Object event,String previousPod)
    {
        try
        {
            def outxml = sendXml(N4MsgType,event)
            if(outxml != null && outxml.indexOf("updt_POD=") == -1){
                def xmlMsg = new StringBuffer()
                xmlMsg.append(outxml.substring( 0, outxml.length()-2))
                xmlMsg.append(" updt_POD='"+previousPod+"'")
                xmlMsg.append(" />")
                outxml =  xmlMsg.toString()
            }
            outxml = appendRelToPartyToConsigneeValue(outxml)
            return outxml
        }catch(Exception e){
            e.printStackTrace()
        }

    } //Method Ends


    //A8
    public String appendRelToPartyToConsigneeValue(String xmlData)
    {
        String xmlGvyData = xmlData
        try{
            //1.If UnitFlexString02 is part of the xml and updt_UnitFlexString02 is not part of the xml
            if(xmlGvyData.contains("UnitFlexString02=") && !xmlGvyData.contains("updt_UnitFlexString02=")){

                def relToParty = gvyCmisCrsUtil.getFieldValues(xmlGvyData, "UnitFlexString02=")
                def goodsCneeName = gvyCmisCrsUtil.getFieldValues(xmlGvyData, "GoodsConsigneeName=")
                println("relToParty="+relToParty+"  goodsCneeName="+goodsCneeName)
                if(relToParty != null && relToParty.trim().length() > 0){
                    goodsCneeName = goodsCneeName+"%"+relToParty
                }
                if(xmlGvyData.contains("updt_GoodsConsigneeName=")){
                    def updtGoodsCneeName = gvyCmisCrsUtil.getFieldValues(xmlGvyData, "updt_GoodsConsigneeName=")
                    updtGoodsCneeName = (relToParty != null && relToParty.trim().length() > 0) ? updtGoodsCneeName+"%"+relToParty : updtGoodsCneeName
                    xmlGvyData = gvyCmisCrsUtil.eventSpecificFieldValue(xmlGvyData,"updt_GoodsConsigneeName=",updtGoodsCneeName)
                }
                xmlGvyData = gvyCmisCrsUtil.eventSpecificFieldValue(xmlGvyData,"GoodsConsigneeName=",goodsCneeName)
            }
            else if(xmlGvyData.contains("UnitFlexString02=") && xmlGvyData.contains("updt_UnitFlexString02=")){
                def relToParty = gvyCmisCrsUtil.getFieldValues(xmlGvyData, "UnitFlexString02=")
                def updtRelToParty = gvyCmisCrsUtil.getFieldValues(xmlGvyData, "updt_UnitFlexString02=")
                def goodsCneeName = gvyCmisCrsUtil.getFieldValues(xmlGvyData, "GoodsConsigneeName=")
                def updtGoodsCneeName = ""
                //Check if xml has UpdtGoodsConsignee
                if(xmlGvyData.contains("updt_GoodsConsigneeName=")){
                    updtGoodsCneeName = gvyCmisCrsUtil.getFieldValues(xmlGvyData, "updt_GoodsConsigneeName=")
                }else{// IF there is no Updt Goods Entry add one to support this function
                    updtGoodsCneeName = goodsCneeName
                    def xmlMsg = new StringBuffer()
                    xmlMsg.append(xmlGvyData.substring( 0, xmlGvyData.length()-2))
                    xmlMsg.append(" updt_GoodsConsigneeName=\""+goodsCneeName+"\"")
                    xmlMsg.append(" />")
                    xmlGvyData = xmlMsg.toString();
                }
                //Check if RelToParty or Update is Blank
                if(relToParty != null && relToParty.trim().length() > 0){
                    goodsCneeName = goodsCneeName+"%"+relToParty
                }
                if(updtRelToParty != null && updtRelToParty.trim().length() > 0){
                    updtGoodsCneeName = updtGoodsCneeName+"%"+updtRelToParty
                }
                xmlGvyData = gvyCmisCrsUtil.eventSpecificFieldValue(xmlGvyData,"GoodsConsigneeName=",goodsCneeName)
                xmlGvyData = gvyCmisCrsUtil.eventSpecificFieldValue(xmlGvyData,"updt_GoodsConsigneeName=",updtGoodsCneeName)
            }

        }catch(Exception e){
            e.printStackTrace();
        }

        return xmlGvyData
    }


    //Commodity Lookup
    public String lookupCommodity(String id) {
        DomainQuery dq = QueryUtils.createDomainQuery("Commodity").addDqPredicate(PredicateFactory.eq(com.navis.argo.ArgoRefField.CMDY_GKEY, id));
        Commodity c = (Commodity)HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
        if(c == null)  {
            return "";
        }
        return c.getCmdyShortName();
    }

//Previous Event value Lookup
    public String getPrevValue( Object event, String tagName)
    {
        String ret = "";
        try
        {
            Set set = event.getEvent().getEvntFieldChanges();
            Iterator iter = set.iterator();
            EventFieldChange efc;
            while ( iter.hasNext()) {
                efc = (EventFieldChange)iter.next();
                if ( tagName.equalsIgnoreCase(efc.getMetafieldId())) {
                    return efc.getPrevVal();
                    //println("getPrevValue :"+efc.getPrevVal())
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }

        return ret;
    }

    //Method to get nonBuiltIn event consignee String
    public String getMetaIdString(Object gvyEventUtil,Object event,Object gvyBaseClass,String metFldId){
        def previousValue = null
        try
        {
            Map mapEvntFld = gvyEventUtil.eventFieldChangedValues(event, gvyBaseClass)
            def aEvntFieldObj = mapEvntFld.get(metFldId)
            if(aEvntFieldObj == null){
                return previousValue
            }
            def fieldname = aEvntFieldObj.getFieldName()
            previousValue = aEvntFieldObj.getpreviousValue()
            previousValue = previousValue != null ? previousValue : null
        }catch(Exception e){
            e.printStackTrace();
        }
        //println("getMetaIdString ::"+previousValue)
        return previousValue
    }

//Method Get Dray Desc for Unit
    public String getDrayDesc(String drayId)
    {
        String drayDesc = "";
        try
        {
            if(drayId == null || drayId.length() == 0){
                return "";
            }

            Map drayMap = new HashMap();
            drayMap.put("FORWARD","FORWARD TO LOADING POINT");
            drayMap.put("RETURN","RETURN TO SHIPPER");
            drayMap.put("DRAYIN","DRAY IN");
            drayMap.put("OFFSITE","DRAY OUT AND BACK");
            drayMap.put("TRANSFER","TRANSFER TO OTHER FACILITY");

            drayDesc = (String)drayMap.get(drayId);
        }catch(Exception e){
            e.printStackTrace();
        }
        return drayDesc;
    }//Method Dray Ends

    //Method : Fetches Previouse Discharge Port value
    public String getPrevDischargePort(Object gvyBaseClass,Object event)
    {
        def preValue = ''
        try
        {
            def gvyDomQueryObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDomainQueryUtil")
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil")
            def prevDischPort =  gvyEventUtil.getPreviousPropertyAsString(event, "rtgPOD1");
            preValue = gvyDomQueryObj.lookupRtgPOD(prevDischPort)
        }catch(Exception e){
            e.printStackTrace()
        }
        return preValue
    }

    //Method : Sets Previous and Current Hold Value
    public String getUnitHoldsValues(String eventTypeId,Object unit,Object gvyBaseClass,Object gvyCmisCrsUtil)
    {
        try
        {
            def eventId = eventTypeId.endsWith("_HOLD") || eventTypeId.endsWith("_RELEASE") ? eventTypeId.substring(0,eventTypeId.indexOf("_")) : ""
            def holdslist = ["BND","CUS","CC","HP","ON","INB","AG","XT"]

            activeUnitHldList = gvyCmisCrsUtil.getUnitActiveHolds(unit)

            for(Id in holdslist){
                if(eventTypeId.endsWith("_HOLD") && eventId.equals(Id)){
                    prevUnitHolds = activeUnitHldList.replace(eventId,"")
                    prevUnitHolds = prevUnitHolds.replace(",,",",")
                    prevUnitHolds = prevUnitHolds.endsWith(",") ?  prevUnitHolds.substring(0,prevUnitHolds.length()-1) : prevUnitHolds
                    break;
                }else if(eventTypeId.endsWith("_RELEASE") && eventId.equals(Id)){
                    prevUnitHolds = activeUnitHldList+","+eventId
                    break;
                } else{
                    prevUnitHolds = activeUnitHldList
                }
            }//For Ends

        }catch(Exception e){
            e.printStackTrace()
        }
        return prevUnitHolds.trim()
    }


    private String getLocationStatus(Object u) {
        def gvyBaseClass = new GroovyInjectionBase();
        def lkpLocType=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType");
        def lkpLocTypeKey = lkpLocType != null ? lkpLocType.getKey() : '';
        def lkpCarrierId=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId");
        def _transitState=u.getFieldValue("unitActiveUfv.ufvTransitState");
        def transitStateKey = _transitState != null ? _transitState.getKey() : '';
        def _category=u.getFieldValue("unitCategory");
        def categoryKey = _category != null ? _category.getKey() : '';
        def dischPort = u.getFieldValue("unitRouting.rtgPOD1.pointId");
        def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
        def gvyPosition = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetail");
        def locationStatus = gvyPosition.getLocationStatus(lkpLocTypeKey,lkpCarrierId,transitStateKey,gvyCmisUtil,categoryKey,dischPort);

        return locationStatus
    }


    public Map getMetaIdList(boolean isBuiltInEvent){
        Map fieldMap = new HashMap();
        fieldMap.put("POD","gdsDestination")
        fieldMap.put("FreightKind","unitFreightKind");
        fieldMap.put("CommodityDescription","gdsCommodity");
        fieldMap.put("Destination","gdsDestination");
        fieldMap.put("GoodsConsigneeName","gdsConsigneeBzu");
        fieldMap.put("DrayStatus","unitDrayStatus");
        fieldMap.put("SpecialStow","unitSpecialStow");
        fieldMap.put("RoutingGroup","rtgGroup");
        fieldMap.put("GoodsBlNbr","gdsBlNbr");
        fieldMap.put("UnitRemark","unitRemark")
        fieldMap.put("UnitFlexString02","unitFlexString02")

        if(isBuiltInEvent){
            fieldMap.put("POD","rtgPOD1")
            fieldMap.put("GoodsConsigneeName","gdsConsigneeAsString");
        }

        return fieldMap
    }

}