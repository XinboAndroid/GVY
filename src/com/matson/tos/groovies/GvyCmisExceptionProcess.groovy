import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.inventory.business.units.EquipmentState

public class GvyCmisExceptionProcess
{
    public void processException(Object event, Exception e)
    {
        def unitId = ''
        def eventType = ''
        try
        {
            def unit = event.getEntity()
            def gvyEventObj = event.getEvent()
            eventType =  gvyEventObj.getEventTypeId()
            //Getting UNIT_OBJ from EQUIP_OBJ
            if(unit instanceof EquipmentState){
                GroovyInjectionBase gvyBaseClass =  new GroovyInjectionBase()
                def unitDetails = gvyBaseClass.getGroovyClassInstance("GvyCmisUnitDetail");
                unit = getUnitFromEquipment(unit)
            }

            unitId = unit.getFieldValue("unitId")

            def exceptionMsg = ExceptionToString(ex)
            def msg = "<CmisFeedErrorMsg  unit=\'"+unitId+"\' event=\'"+eventType+"\' errorStack=\'"+exceptionMsg+"\' />";
            println(msg)
            //gvyBaseClass.sendXml(msg)
        }
        catch(Exception ex)
        {
            processException(ex)
        }
    }

    public void processException(Exception e)
    {
        def exceptionMsg = ExceptionToString(ex)
        def msg = "<CmisFeedErrorMsg  errorStack=\'"+exceptionMsg+"\' />"
        println(msg)
        //gvyBaseClass.sendXml(msg)
    }

    /* Method converts the Exception class to a String to pass it out */
    public String ExceptionToString(Exception e)
    {
        StringBuffer strBuff = new StringBuffer();
        StackTraceElement[] stackElement = e.getStackTrace();
        for(eStr in stackElement){
            strBuff.append(eStr);
            errorBuff.append("\r\n");
        }
        return  strBuff.toString();
    }

}