public class GvyCmisUnitRectify
{
    /*
    * Scenario One : N4 Rectify ? Yard (outgate correction)
    * In N4, user will: 1. PreAdvise Empty  2. Rectify to yard.
    */
    public String processRectify(String xmlData,Object event,Object gvyBaseClass)
    {
        println("GvyCmisUnitRecity.processRectify()")
        def xmlGvyString = xmlData
        try
        {
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            //Check if Earlier unit was PreAdvise and the Rectify
            gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"OGX")

        }catch(Exception e){
            e.printStackTrace()
        }
    }//Method Process Ends

    /*
     * Scenario One : N4 Rectify ? Yard (outgate correction)
     * In N4, user will: 1. PreAdvise Empty  2. Rectify to yard.(Auto Update Rule)
     * Scenario Two :
     * Scenario Three :
     * Scenario Four :
     * Scenario Five :
     * Scenario Six :
     */

}//Class Ends