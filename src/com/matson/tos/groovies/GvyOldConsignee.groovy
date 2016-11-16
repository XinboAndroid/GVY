import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.services.business.event.Event;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.framework.persistence.HibernateApi;
import com.navis.services.business.event.EventFieldChange;

public class GvyOldConsignee {

    public String doIt(Object event)
    {
        println("In Class GvyOldCosignee.doIt() !!!!!!!!!!!!!")

        //Calling Msg Formater class
        def gvyBaseClass = new GroovyInjectionBase();

        //Get OBJECT
        Object unitObj = event.getEntity();

        def eventBase = event.getEvent();
        println("Changes="+eventBase.getEvntFieldChanges());

        Iterator i1 = eventBase.getEvntFieldChanges().iterator();

        String id;
        while(i1.hasNext()) {
            def efc = (EventFieldChange)i1.next();
            def name = efc.getEvntfcMetafieldId();
            def value = efc.getPrevVal();
            println("name="+name+" value="+value);
            println(efc.dump());
        }



        // Test
        def current  =  unitObj.getFieldValue("unitGoods.gdsConsigneeAsString")
        println("CurrentConsignee= "+current);
        def result1 = event.wasFieldChanged("GoodsConsignee")
        def result2 = event.getPreviousPropertyAsString("GoodsConsigneeName");
        def result3 = event.getPreviousPropertyAsString("GoodsConsigneeRef");
        def prevc = event.getPreviousPropertyAsString("GoodsConsignee");
        if(result1) {

            HibernateApi api = HibernateApi.getInstance();
            ScopedBizUnit biz = api.get(ScopedBizUnit.class, Long.valueOf(prevc));
            def prevCosignee = biz.getBzuName();
            println("Previous Consignee = "+prevCosignee+" id="+prevc+" "+result2+" "+result3);
            return prevCosignee;
        } else {
            println("No Change");
        }

        println(event.dump());
        return null;
    }
}