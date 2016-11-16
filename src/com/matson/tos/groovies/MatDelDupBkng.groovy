import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.framework.business.Roastery
import com.navis.inventory.business.api.UnitFinder
import com.navis.argo.business.reference.Container
import com.navis.argo.ContextHelper
import com.navis.services.business.event.GroovyEvent;
import com.navis.inventory.business.units.Unit;

import com.navis.framework.portal.FieldChanges
import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType

import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import com.navis.framework.portal.query.DomainQuery;
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.inventory.InventoryField;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery;

import com.navis.orders.business.eqorders.Booking;
import com.navis.orders.business.eqorders.EquipmentOrderItem;
import com.navis.argo.business.reference.EquipType;

import com.navis.argo.ArgoPropertyKeys;
import com.navis.argo.ArgoRefField;
import java.util.Calendar;
import org.apache.log4j.Logger


class MatDelDupBkng{

    public boolean execute(Map params)
    {
        def emailTo = "1aktosdevteam@matson.com";
        def inj = new GroovyInjectionBase();
        def sendMail = "N";
        try {

            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR_OF_DAY, -24);
            Date startDate = cal.getTime();
            String delBkng = "Please check and Delete the duplicate booking entry without items.\n\n";

            LOGGER.warn("################################ Calling MatDelDupBkng startDate "+ startDate);

            DomainQuery dq = QueryUtils.createDomainQuery("Booking")
                    .addDqPredicate(PredicateFactory.ge(InventoryField.EQBO_CREATED,startDate));

            def bkngList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            LOGGER.warn("After bkngList :::: "+bkngList.size());

            String bkngGkey = null;
            String bkngNbr = null;
            String updatBkngNbr = "";
            def bkng = null;

            Iterator iter = bkngList.iterator();
            while(iter.hasNext()) {
                bkng = iter.next();
                bkngGkey = bkng.eqboGkey;
                bkngNbr = bkng.eqboNbr;

                LOGGER.warn("Booking number and Gkey :: " + bkngNbr + " :: " + bkngGkey);
                def itemsFound = checkItems(bkngGkey);
                if ("N".equalsIgnoreCase(itemsFound)){
                    LOGGER.warn("No items found Check if the Booking is duplicate for :: " + bkngNbr);
                    DomainQuery dqChk = QueryUtils.createDomainQuery("Booking")
                            .addDqPredicate(PredicateFactory.eq(InventoryField.EQBO_NBR,bkngNbr));

                    def bkngChk = HibernateApi.getInstance().findEntitiesByDomainQuery(dqChk);
                    LOGGER.warn ("Checking duplicate for booking :: "+ bkngNbr +" :: "+ bkngChk.size());
                    if (bkngChk.size() > 1){
                        LOGGER.warn("Delete the booking with Gkey -- No items found for the Booking :: " + bkngNbr + " and Gkey :: "+ bkngGkey);
                        delBkng = delBkng+"Booking Number "+bkngNbr+" :: Gkey :: "+ bkngGkey+" \n\n"
                        sendMail = "Y";
                    }
                }
            }

            if ("Y".equalsIgnoreCase(sendMail)){
                delBkng = delBkng + "\n\nNote : Please verify the BOB message in TDP logs (TosDataProcessor.log) and create the ITN Hold on booking if required.";
                LOGGER.warn(delBkng);
                def emailSender = inj.getGroovyClassInstance("EmailSender");
                emailSender.custSendEmail(emailTo,"Duplicate Bookings",delBkng);
            }else {
                LOGGER.warn("No duplicate booking found for last 3 hours");
            }


        }catch(Exception e){
            e.printStackTrace();
            LOGGER.warn(e.getMessage());
        }
    }

    public String checkItems(String bkngGkey)
    {

        try {
            String itemsFound = "Y";
            DomainQuery dq = QueryUtils.createDomainQuery("EquipmentOrderItem")
                    .addDqPredicate(PredicateFactory.eq(InventoryField.EQBOI_ORDER,bkngGkey));

            def itemList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            LOGGER.warn("After itemList :::: "+itemList.size());

            if (itemList.size() == 0){
                itemsFound = "N";
            }
            return itemsFound;
        }catch(Exception e){
            e.printStackTrace();
            LOGGER.warn(e.getMessage());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MatDelDupBkng.class);
}

