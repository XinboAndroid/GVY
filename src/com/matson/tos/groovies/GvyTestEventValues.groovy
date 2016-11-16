import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.services.business.event.EventFieldChange;


public class GvyTestEventValues
{

    /* Method Returns a map object for Event Fields Changed
     * Map contains Field name as Key and Field Object as the value
     * The Field Object has Previous Value and Current Value
    */
    public Map eventFieldChangedValues(Object event, Object gvyBaseClass)
    {
        Map mapFields = new HashMap()
        def newValue=''
        def prevValue = ''
        try
        {
            def gvyEventObj = event.getEvent()
            Set changes =  gvyEventObj.getFieldChanges()
            Iterator iterator = changes.iterator();
            while(iterator.hasNext())
            {
                EventFieldChange  fieldChange = (EventFieldChange)iterator.next();
                String fieldName = fieldChange.getMetafieldId()
                MetafieldId mfId = MetafieldIdFactory.valueOf(fieldName);
                newValue = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcNewVal());
                newValue = newValue != null ? newValue : ''
                prevValue = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcPrevVal());
                prevValue = prevValue != null ? prevValue : ''

                def gvyEventField = gvyBaseClass.getGroovyClassInstance("GvyCmisEventField");
                gvyEventField.setFieldName(fieldName);
                gvyEventField.setpreviousValue(prevValue);
                gvyEventField.setCurrentValue(newValue);
                mapFields.put(fieldName, gvyEventField)
                println('fieldName:'+fieldName+'   mfId :'+mfId+'     newValue:'+newValue+'     prevValue:'+prevValue )

            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return mapFields;
    }

    public void printEventFields(Object mapFieldValues, Object gvyBaseClass)
    {
        try
        {
            Iterator it = mapFieldValues.keySet().iterator();
            while (it.hasNext())
            {
                def aField = it.next();
                def aEvntFieldObj = mapFieldValues.get(aField)
                def fieldname = aEvntFieldObj.getFieldName()
                def previousValue = aEvntFieldObj.getpreviousValue()
                def currentValue = aEvntFieldObj.getCurrentValue()
                println('fieldname ::'+fieldname+'  previousValue::'+previousValue+'   currentValue::'+currentValue)
            }
        }catch(Exception e){
            e.printStackTrace()
        }

    }


}