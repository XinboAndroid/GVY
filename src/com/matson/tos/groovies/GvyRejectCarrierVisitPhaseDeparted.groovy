import com.navis.argo.business.atoms.CarrierVisitPhaseEnum;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.framework.util.message.MessageCollector;
import com.navis.framework.util.message.MessageLevel;
import com.navis.road.RoadPropertyKeys;
import com.navis.road.business.atoms.TranSubTypeEnum;
import com.navis.road.business.model.TruckTransaction;
import com.navis.road.business.util.RoadBizUtil;
import com.navis.road.business.workflow.TransactionAndVisitHolder;
import java.util.Date;

public class GvyRejectCarrierVisitPhaseDeparted
{
    private static java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MM/dd/yyyy");
    public void execute(TransactionAndVisitHolder inDao)
    {
        TruckTransaction tran = inDao.getTran();
        if(tran != null && TranSubTypeEnum.RE.equals(tran.getTranSubType()))
        {
            CarrierVisit cv = tran.getCarrierVisit();
            if(cv != null)
            {
                def date = cv.getCvATD();
                if(date != null) {
                    date = format.format(date)
                }
                String[] depart = [cv.getCvId(), date];
                String[] cancelled = [cv.getCvId()];
                CarrierVisitPhaseEnum phase = cv.getCvVisitPhase();
                println("GvyRejectCarrierVisitPhaseDeparted "+phase+" "+cv.getCvId()+" "+date);
                if(phase != null)  {
                    if(phase.equals(CarrierVisitPhaseEnum.DEPARTED) || phase.equals(CarrierVisitPhaseEnum.CLOSED ))  {
                        println("Closed");
                        RoadBizUtil.getMessageCollector().appendMessage(MessageLevel.SEVERE, RoadPropertyKeys.GATE__CARRIER_DEPARTED, null, depart);
                    } else if(phase.equals(CarrierVisitPhaseEnum.CANCELED)) {
                        println("Cancel");
                        RoadBizUtil.getMessageCollector().appendMessage(MessageLevel.SEVERE, RoadPropertyKeys.GATE__CARRIER_CANCELED, null, cancelled);
                    }
                }
            }
        }
    }

    public static String BEAN_ID = "rejectCarrierVisitPhaseDeparted";

}