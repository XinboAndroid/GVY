public class GvyCmisEventEditPortBooking
{
    def cmisActnList = ''

    public String processEditPortBkg(String xmlData,Object event,Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        try
        {
            def unit = event.getEntity()
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def rerouteEvntFeed = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFeedUnitReroute")
            cmisActnList = gvyBaseClass.getGroovyClassInstance("GvyCmisListAction");

            //1]GoodsBLNbr 2] DestPort 3] UnitNotes 4]DischPort 5] LoadPort 6] O/B carrier
            def updtGdsBlNbrFlag = gvyEventUtil.wasFieldChanged(event, "gdsBlNbr")
            def updtDestportFlag = gvyEventUtil.wasFieldChanged(event, "gdsDestination")
            def updtUnitNotesFlag = gvyEventUtil.wasFieldChanged(event, "unitRemark")
            def updtDischPortFlag = gvyEventUtil.wasFieldChanged(event, "rtgPOD1")
            def updtLoadPortFlag = gvyEventUtil.wasFieldChanged(event, "rtgOPL")
            def updtObCarrierFlag = gvyEventUtil.wasFieldChanged(event, "ufvIntendedObCv")

            def locationStatus = gvyCmisUtil.getFieldValues(xmlGvyString, "locationStatus=")

            if (updtObCarrierFlag)
            {
                processOBCarrierChange(xmlGvyString, gvyBaseClass,gvyCmisUtil)
            }
            if(updtGdsBlNbrFlag || updtDischPortFlag || updtLoadPortFlag)
            {
                postFieldChange(xmlGvyString,gvyBaseClass,gvyCmisUtil,locationStatus)
            }
            if(updtDestportFlag)
            {
                rerouteEvntFeed.processUnitRerouteCmisFeed(xmlGvyString,gvyBaseClass,event,unit,cmisActnList)
            }
            if(updtUnitNotesFlag)
            {
                cmisActnList.setActionList("EDT")
            }


            //Post Cmis msg after appending the required action
            LinkedHashSet actionList = cmisActnList.getActionList();
            println("actionList :::::"+actionList.size())
            for(aAction in actionList)
            {
                println("UNIT_EDIT_PORT_BOOKING_POSTING ::"+aAction);
                gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction)
            }

            println("<<<< processEditPortBkg(String xmlData,Object event,Object gvyBaseClass) >>>>")
        }catch(Exception e){
            e.printStackTrace()
        }

    }

    public void postFieldChange(String xmlData,Object gvyBaseClass,Object gvyCmisUtil,String locationStatus)
    {
        def xmlGvyString = xmlData
        try
        {
            if(locationStatus.equals('7'))
            {
                //overwrite ACTION=NIB & Post Msg
                cmisActnList.setActionList("NIB")
            }
            else{
                //overwrite ACTION=BDC & Post Msg
                cmisActnList.setActionList("NIB")
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void processOBCarrierChange(String xmlGvyData, Object gvyBaseClass,Object gvyCmisUtil)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            //OBCarrier Cmis Action
            cmisActnList.setActionList("AVL")
            cmisActnList.setActionList("BDC")
        }catch(Exception e){
            e.printStackTrace();
        }
    }

}
