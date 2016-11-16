import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.framework.portal.UserContext
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Logger

/*
* Copyright (c) 2012 Navis LLC. All Rights Reserved.
*  AUTHOR: Siva Raja
*  Date Written: July 23rd, 2012
*  Description: To be Written
*/

public class MATTriggerGroovyJob extends GroovyApi {

    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());
    private static final Logger LOGGER = Logger.getLogger(MATTriggerGroovyJob.class);

    public void triggerGroovyJob(GroovyEvent event, Object api){
        LOGGER.warn(" MATTriggerGroovyJob started " + timeNow);
        Unit tempUnit = (Unit) event.getEntity();
        this.getGroovyClassInstance("MATProcessNewVess").execute(tempUnit);
        LOGGER.warn(" MATTriggerGroovyJob ended " + timeNow);
    }

    public void triggerMatVesGroovyJob(GroovyEvent event, Object api){
        LOGGER.warn(" MATTriggerGroovyJob started for MATProcessMatsonVess " + timeNow);
        Unit tempUnit = (Unit) event.getEntity();
        this.getGroovyClassInstance("MATProcessMatsonVess").execute(tempUnit);
        LOGGER.warn(" MATTriggerGroovyJob for MATProcessMatsonVess ended " + timeNow);
    }

    public void triggerMatVesEventGroovyJob(GroovyEvent event, Object api){
        LOGGER.warn(" MATTriggerGroovyJob started for MATProcessMatsonVess " + timeNow);
        Unit tempUnit = (Unit) event.getEntity();
        this.getGroovyClassInstance("MATProcessMatsonVess").createBdaEdtEvent(event,tempUnit);
        LOGGER.warn(" MATTriggerGroovyJob for MATProcessMatsonVess ended " + timeNow);
    }

//RO RO CONTAINER REPORT

    public void triggerROROContainerReport(GroovyEvent event, Object api){
        LOGGER.warn(" MATTriggerGroovyJob.triggerROROContainerReport started for MATProcessMatsonVess " + timeNow);
        Unit tempUnit = (Unit) event.getEntity();
        this.getGroovyClassInstance("MATProcessMatsonVess").sendingROROContainersReport(tempUnit);
        LOGGER.warn(" MATTriggerGroovyJob.triggerROROContainerReport for MATProcessMatsonVess ended " + timeNow);
    }


}
