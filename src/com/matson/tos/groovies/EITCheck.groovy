/*

Groovy code called to check EIT

Change History

A1:  skb  4/10/09  (103) Out BAT # Not Allowed for BOB.  Check if not bat on out but on in error, check for numeric eit.
A2: GR  06/13/11 Throw Error if EIT not in 101-200 or 900-999 series
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.road.business.util.RoadBizUtil
import com.navis.framework.util.message.MessageLevel
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.BizFailure

public class EITCheck  extends GroovyInjectionBase
{
    // Outgate
    public void execute(inDao, api)
    {
        api.log("\nEITCheck: --Executing Groovy Gate Task---")

        def eitString = inDao.tv.tvdtlsBatNbr
        def eitOutString = inDao.tv.tvdtlsOutBatNbr


        api.log("EITCheck: "+eitString+" "+eitOutString+"!\n\n")


        // println(inDao.tv.dump());
        if(eitString != null && eitOutString == null) {
            def msg = [eitString];
            PropertyKey INVALID_EIT = PropertyKeyFactory.valueOf("gate.enter_out_bat");
            RoadBizUtil.appendMessage(MessageLevel.SEVERE,INVALID_EIT ,msg);
            return;
        }

        /*
if(eitString == null && eitOutString != null) {
throw new Exception("Out Gate: No EIT given at ingate, please confirm truck ID!  If you can not find the truck Id used at In Gate, leave the Bat # blank to Out Gate.");
}
       */

        try {
            if(eitOutString != null) {
                def eitNum = Integer.parseInt(eitOutString);
            }
        } catch (Exception e) {
            def msg = [eitOutString];
            PropertyKey INVALID_EIT = PropertyKeyFactory.valueOf("gate.invalid_eit");
            RoadBizUtil.appendMessage(MessageLevel.SEVERE,INVALID_EIT ,msg);

        }
    }

    public void executeIngate(inDao, api) {
        def eitString = inDao.tv.tvdtlsBatNbr;
        def msg = [eitString];
        int eitNum = 0;
        try {
            if(eitString != null) {
                eitNum = Integer.parseInt(eitString);
            }
        }catch (Exception e) {
            PropertyKey INVALID_EIT = PropertyKeyFactory.valueOf("gate.invalid_eit");
            RoadBizUtil.appendMessage(MessageLevel.SEVERE,INVALID_EIT ,msg);
        }
    }

    public void gateSIEitCheck(inDao){
        def eitString = inDao.tv.tvdtlsBatNbr;
        def msg = [eitString];
        int eitNum = 0;
        if(eitString != null) {
            eitNum = Integer.parseInt(eitString);
        }
        if(eitNum >= 101 && eitNum <= 200 && !"PASSPASS".equals(inDao.tv.tvdtlsGate.gateId)){
        }else if(eitNum >= 900 && eitNum <= 999 && !"PASSPASS".equals(inDao.tv.tvdtlsGate.gateId)){
        }else if(!"PASSPASS".equals(inDao.tv.tvdtlsGate.gateId)){
            throw com.navis.framework.util.BizFailure.create("EIT "+eitNum+" is not a valid EIT number.   Please re-enter");
        }
    }
}