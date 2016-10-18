import com.navis.argo.ContextHelper;
import com.navis.argo.business.api.GroovyApi;
import com.navis.framework.business.Roastery;
import com.navis.framework.util.BizViolation;
import com.navis.inventory.business.api.UnitManager;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.UnitFacilityVisit;

import com.navis.security.business.user.BaseUser;
import org.apache.log4j.Logger;

public class MatMassUpdateYardPosition {
    public void execute(Unit inUnit, String inSlot) {
        GroovyApi api = new GroovyApi();
        LOGGER.info("UPDATE POSITION executed  "+inUnit);

        if (inUnit != null && inSlot!=null) {
            UnitFacilityVisit ufv = inUnit.getUfvForFacilityLiveOnly(ContextHelper.getThreadFacility());
            LOGGER.info("UPDATE POSITION executed for Unit  "+ufv.getUfvUnit().getUnitId() );
            if (ufv!= null ) {
                UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
                try {
                    unitManager.recordUnitYardMove(ufv, inSlot, (String) null);
                } catch (BizViolation bizViolation) {
                    LOGGER.error("Cannot record yard move "+bizViolation.toString());
                    String userId = ContextHelper.getThreadUserId();
                    String emailTo = "1aktosdevteam@matson.com";
                    if (userId!= null) {
                        BaseUser baseUser = BaseUser.findBaseUser(userId);
                        if (baseUser!= null && baseUser.getBuserEMail()!=null) {
                            emailTo = baseUser.getBuserEMail();
                        }
                    }
                    api.sendEmail(emailTo, "gbabu@matson.com","Update Position Failed", " Update of position failed for unit "+ufv.getUfvUnit().getUnitId() +" with error"+bizViolation.toString());
                }
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(MatMassUpdateYardPosition.class);
}
