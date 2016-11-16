public class GvyCmisEventUnitRenumber
{
    public processRenumber(Object event,String xmlData,Object gvybaseClass){
        def xmlGvyString = xmlData
        try{

            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def prevUnitNbr = gvyEventUtil.getPreviousPropertyAsString(event,"unitId")
            def xmlStrPrevUnitNbr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"ctrNo=",prevUnitNbr)

            gvyCmisUtil.postMsgForAction(xmlStrPrevUnitNbr,gvyBaseClass,"DEL")
            gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"ADD")

        }catch(Exception e){
            e.printStackTrace()
        }
    }
}