import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.api.NoticeManager;
import com.navis.framework.business.Roastery;

public class GvyCmisEventCorrection extends GroovyInjectionBase
{

    public String execute(Map inParameters)
    {
        try
        {
            def eventGKey = (String) inParameters.get("eventGkey");
            println("Event GKey ------------------------------------------------------------::"+eventGKey)
            NoticeManager noticeManager = (NoticeManager) Roastery.getBean(NoticeManager.BEAN_ID);
            noticeManager.generateNotices( new Long(eventGKey));

        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method execute Ends


}//Class Ends