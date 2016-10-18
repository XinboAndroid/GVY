package com.matson.tos.groovies

/*
* Sr doer  Date      change
* A1 GR    08/22/10  Uncommenting BDC for acets (SN4Q change)
* A2 GR    10/08/10  Gems : Uptd message posting method to Incorporate
                     Message specific field updates(example : AVL)
			         Depending Class GvyCmisUtil
* A3 GR	   10/29/10	 SN4Q: BDC=AO for AVL where OBcarreir Is Updated
* A4 GR	   01/06/12	 Oracle Patch fix
* A5 RI    10/18/13  Added locationStatus=4 for Inbound for CMIS to get the correct Barge vesvoy
* A6 KR    07/09/15  Alaska Ports
*/


import com.navis.argo.business.reference.RoutingPoint;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.ArgoRefField;
import com.navis.framework.persistence.HibernateApi;
import com.navis.argo.ContextHelper;


public class GvyCmisEventFeedUnitReroute
{
    def updtcategory = ''
    def prevCategory = ''
    def locationStatus = ''

    public Object processUnitRerouteCmisFeed(String xmlGvyData, Object gvyBaseClass, Object event, Object unit, Object cmisActionList, boolean detnMsg)
    {
        def xmlGvyString = xmlGvyData
        def processCall = ''
        def cmisActnList = ''
        try
        {
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

            def updtDportFlag = gvyEventUtil.wasFieldChanged(event, "gdsDestination")
            def updtObCarrierFlag = gvyEventUtil.wasFieldChanged(event, "ufvIntendedObCv")
            def updtChasTypeReqFlag = gvyEventUtil.wasFieldChanged(event,"UfvFlexString02")
            def updtReleaseToPartyFlag = gvyEventUtil.wasFieldChanged(event, "UnitFlexString02")
            def updtdischPort = gvyEventUtil.wasFieldChanged(event,'rtgPOD1')

            def updateUCCFlag = gvyEventUtil.wasFieldChanged(event, "UnitFlexString15");
            def updateECCFlag = gvyEventUtil.wasFieldChanged(event,"UfvFlexString08");


            updtcategory = gvyEventUtil.wasFieldChanged(event, "unitCategory")
            prevCategory = gvyEventUtil.getPreviousPropertyAsString(event, "unitCategory")

            def intdObCarrierId=unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
            intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ''
            boolean postObCarrierMsg =  (intdObCarrierId.equals('GEN_TRUCK') || intdObCarrierId.equals('GEN_VESSEL')
                    || intdObCarrierId.equals('GEN_CARRIER')) ? false : true

            locationStatus = gvyCmisUtil.getFieldValues(xmlGvyString, "locationStatus=")
            //def destination = gvyCmisUtil.getFieldValues(xmlGvyString, "dPort=")
            //Changed the Destination to Discharge Port
            def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            dischPort = dischPort != null ? dischPort : ''

            println('locationStatus ::'+locationStatus+'     dischPort::'+dischPort)

            //Check to see if UNIT_PROPERTY_UPDATE call
            if(cmisActionList != null){
                processCall="UNIT_PROPERTY_UPDATE"
                cmisActnList = cmisActionList
            }else{
                processCall="UNIT_REROUTE"
                cmisActnList = gvyBaseClass.getGroovyClassInstance("GvyCmisListAction");
            }

            //Check For Detention Msg
            println("Detention MSG :::"+detnMsg)
            if(detnMsg)
            {
                cmisActnList.setActionList("FREE")
                //cmisActnList.setActionList("EDT")
            }

            //1] processOBCarrierChange 2] processDischPortChange 3] processChasTypeReqChange 4] processReleaseToParty
            if(updtdischPort)
            {
                //DestPort Changes
                xmlGvyString = processDischPortChange(xmlGvyString,gvyBaseClass,event,unit,gvyEventUtil,gvyCmisUtil,dischPort,locationStatus,cmisActnList)
            }
            else if(updtReleaseToPartyFlag || updtcategory || updateECCFlag || updateUCCFlag)
            {
                cmisActnList.setActionList("EDT")
            }
            //DISCH_PORT=HON and LOCSTAT=7
            if(updtdischPort && dischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && locationStatus.equals('7'))
            {
                //SED RECORD
                cmisActnList.setActionList("SED")
            }
            if(updtObCarrierFlag && postObCarrierMsg)
            {
                // OBCarrier Changes
                xmlGvyString = processOBCarrierChange(xmlGvyString, gvyBaseClass,gvyCmisUtil,cmisActnList)
            }
            if(updtChasTypeReqFlag)
            {
                //ACTION PMD - Updt Chassis Type
                cmisActnList.setActionList("PMR")
            }
            if(updtDportFlag){
                processDestinationPortChange(xmlGvyData,gvyBaseClass,gvyCmisUtil,locationStatus,cmisActnList)
            }

            //Post Unit Reroute messages appending cmis action
            if(processCall.equals("UNIT_REROUTE"))
            {
                //Post Cmis msg after appending the required action
                LinkedHashSet actionList = cmisActnList.getActionList();
                println("Inside of GvyCmisEventFeedUnitReroute.processUnitRerouteCmisFeed ");
                println("Number of action to send in message is "+actionList.size());
                println("actionList :::::"+actionList.size())
                for(aAction in actionList)
                {
                    println("UNIT_REROUTE_ACTION_MSG_POSTING ::"+aAction);
                    //gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction)   //A2
                    gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction, unit, event,gvyEventUtil)
                }
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public String processOBCarrierChange(String xmlGvyData, Object gvyBaseClass,Object gvyCmisUtil, Object cmisActnList)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            //OBCarrier Cmis Action   //A3
            cmisActnList.setActionList("AVL")
            //A5
            if(locationStatus.equals('1') || locationStatus.equals('6') || locationStatus.equals('4') ){
                cmisActnList.setActionList("BDC")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"locationStallConfig=","AO")
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return xmlGvyString
    }

    public void processDestinationPortChange(String xmlGvyData, Object gvyBaseClass,Object gvyCmisUtil,String locationStatus, Object cmisActnList)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            cmisActnList.setActionList("BDC")
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public String processDischPortChange(String xmlGvyData, Object gvyBaseClass, Object event, Object unit, Object gvyEventUtil, Object gvyCmisUtil,String dischargePort, String locationStatus,Object cmisActnList)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            cmisActnList.setActionList("EDT")
        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }//Method processDportChange Ends


    public static RoutingPoint findRoutingPoint(String inPointId)
    {
        DomainQuery dq = QueryUtils.createDomainQuery("RoutingPoint").addDqPredicate(PredicateFactory.eq(com.navis.argo.ArgoRefField.POINT_GKEY, inPointId)); //A4
        return (RoutingPoint)HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }
}
