public class GvyCmisEventPassPass
{
    //Method Post Pass Pass Assign (OTR) Msg
    public void passpassAssignOtr(String xmlData,Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
        gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"PDU")
    }

    //Method Post Pass Pass Assign (SHOW) Msg
    public void passpassAssignShow(String xmlData,Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
        gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"PDU")
    }

    //Method Post Pass Pass UNAssign Msg
    public void passpassUnAssign(String xmlData,Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
        //plan Disp gets set in Rtg method
        //gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"planDisp=","null")
        gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"PDD")
    }

}