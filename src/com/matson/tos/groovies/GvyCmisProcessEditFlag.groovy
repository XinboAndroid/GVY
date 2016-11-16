/*
*  Srno  Changer Date        Desc
*  A1    GR      08/09/10    Added Supplemental Check for EditFlag Update
*  A2    GR      11/23/10    Commented print Statement
*  A3    GR      01/05/11    Newves Fix After Adding Direct posting EDT/BDC to Gems
*  A4    GR      09/02/11    SIT unassign to set misc2=S
*/
import com.navis.argo.business.api.GroovyApi
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.reference.Shipper
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.services.business.event.Event;
import com.navis.services.business.event.EventFieldChange;
import java.text.SimpleDateFormat;

public class GvyCmisProcessEditFlag {

    public String processEditFlag(Object event, String eventType, Object unit, Object gvyBaseClass)
    {
        //MISC2 Field
        def editFlag = ''

        try
        {
            def gvyEvnt = event.getEvent()
            def eventNotes = gvyEvnt.getEventNote()
            eventNotes = eventNotes != null ? eventNotes : ''

            //Get Flag value  //A3 - Newves Fix After Adding Direct posting EDT/BDC to Gems
            editFlag = unit.getUnitFlexString11();
            if(eventNotes != null && (eventNotes.startsWith('Supplemental') || eventNotes.startsWith('NewVes') || eventNotes.startsWith('NIS Load'))){
                return editFlag;
            }

            Map fieldMap = getEditFieldMap();

            //Reads and Maps Event Updated Field value
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            Map mapEvntFld = gvyEventUtil.eventFieldChangedValues(event, gvyBaseClass)

            //Read Fields and stores values into StringBuffer Objects for processing
            editFlag = readEventChangedFields(mapEvntFld, fieldMap, event, editFlag)
            editFlag = processEventEditFlag(eventType, editFlag);

        }catch(Exception e){
            e.printStackTrace()
        }
        return editFlag

    }


// Set's EditFlag for Hold/Release Events
    public static String processEventEditFlag(String eventType, String editFlagVal)
    {
        String editFlagHld = ''
        try
        {
            editFlagHld = editFlagVal;

            if(eventType.equals("HP_HOLD") || eventType.equals("HP_RELEASE")
                    ||eventType.equals("CC_HOLD") || eventType.equals("CC_RELEASE"))
            {
                editFlagHld = appendValue(editFlagHld,"M");
            }
            else if(eventType.equals("AG_HOLD") || eventType.equals("AG_RELEASE")
                    || eventType.equals("XT_HOLD") || eventType.equals("XT_RELEASE"))
            {
                editFlagHld = appendValue(editFlagHld,"A");
            }
            else if(eventType.equals("INB_HOLD") || eventType.equals("INB_RELEASE")
                    || eventType.equals("CUS_HOLD") || eventType.equals("CUS_RELEASE"))
            {
                editFlagHld  = appendValue(editFlagHld,"B");
            }
            else if(eventType.equals("ON_HOLD") || eventType.equals("ON_RELEASE"))
            {
                editFlagHld = appendValue(editFlagHld,"O");
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return editFlagHld;
    }

//Appends EditFlag value from the Right (10Char max)
    public static String appendValue(String edtFlag, String fldEditFlag)
    {
        String editFlagValue = "";
        try
        {
            if(edtFlag != null && edtFlag.length() == 10){
                String stripFirstChar = edtFlag.substring(1, edtFlag.length());
                editFlagValue = stripFirstChar+fldEditFlag;
                //A2- println('editFlagValue=:::'+editFlagValue+' stripFirstChar :'+stripFirstChar+'    fldEditFlag:'+fldEditFlag)
            }else{
                if(edtFlag == null || edtFlag.trim().length() == 0){
                    editFlagValue = fldEditFlag;
                    //A2- println('editFlagValue IF :::'+editFlagValue+'   fldEditFlag:::'+fldEditFlag)
                }else{
                    editFlagValue = edtFlag+fldEditFlag;
                    //A2- println('editFlagValue ELSE :::'+editFlagValue+'   fldEditFlag:::'+fldEditFlag)
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        //A2 - println('editFlagValue :::'+editFlagValue)
        return editFlagValue;
    }


    public Map getEditFieldMap(){

        Map fieldMap = new HashMap();
        fieldMap.put("gdsConsigneeAsString", "C"); fieldMap.put("gdsDestination","P");
        fieldMap.put("unitFreightKind","D"); fieldMap.put("gdsCommodity","D");
        fieldMap.put("unitDrayStatus","S"); fieldMap.put("unitRemark","R");
        fieldMap.put("gdsBlNbr","K");
        fieldMap.put("gdsConsigneeBzu", "C");  // Added for non-BuiltIn Evnts

        // Added for avail Dates.
        fieldMap.put("ufvFlexDate02","X");

        //Added for Release To party
        fieldMap.put("unitFlexString02","C");

        return fieldMap
    }


    /*
    * Method reads the Updated field values from the object
    * and stores the values in a string buffer for processing
    */
    public String readEventChangedFields(Object mapEvntField, Object fieldMap,Object event, String editFlagVal)
    {
        def processFlag = false;
        def fldvalue = "";
        def editFlagFld = editFlagVal;
        try
        {
            Iterator it = mapEvntField.keySet().iterator();
            while (it.hasNext())
            {
                def aField = it.next();
                if(fieldMap.get(aField) == null) {
                    continue;
                }

                //Fetch Field Map : Key,Value
                fldvalue = fieldMap.get(aField)

                //Fetch Event Updated Field : current and Previous value
                def aEvntFieldObj = mapEvntField.get(aField)
                def fieldname = aEvntFieldObj.getFieldName()
                def previousValue = aEvntFieldObj.getpreviousValue()
                previousValue = previousValue != null ? previousValue : ''
                def currentValue = aEvntFieldObj.getCurrentValue()
                currentValue = currentValue != null ? currentValue : ''

                //println('TEST ---- aField :'+aField+'    fldvalue:'+fldvalue+'  fieldname ::'+fieldname+'  previousValue::'+previousValue+'   currentValue::'+currentValue)
                //Validating Field Change and Setting Edit Flag
                if(!currentValue.equals(previousValue))
                {
                    if(aField.equals("gdsCommodity") && currentValue.equals("AUTOMOBILE") || currentValue.equals("AUTO")){
                        editFlagFld = appendValue(editFlagFld,fldvalue);
                    }
                    else if(aField.equals("unitDrayStatus") && (currentValue.equals("DRAYIN") || currentValue.equals("OFFSITE") || currentValue.equals("Dray Out and Back") || previousValue.equals("OFFSITE") || previousValue.equals("Dray Out and Back"))){
                        editFlagFld = appendValue(editFlagFld,fldvalue);
                    }
                    else if (!(aField.equals("gdsCommodity") || aField.equals("unitDrayStatus")))
                    {
                        editFlagFld = appendValue(editFlagFld,fldvalue);
                    }
                }//Inner If
            }//While Ends
        }catch(Exception e){
            e.printStackTrace()
        }
        return editFlagFld;
    }//Method readEventChangedFields Ends


}