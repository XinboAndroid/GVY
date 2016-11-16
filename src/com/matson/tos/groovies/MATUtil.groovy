import com.navis.argo.business.api.ArgoUtils
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.ImpedimentsBean
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.Booking
import com.navis.road.business.util.RoadBizUtil

/*
* Groovy for all utilities
*
*/

public class MATUtil {

    /*
    * Method to refresh the units on booking when haz permission is granted
     */
    public void refreshUnitsForBooking(Booking inBooking) {
        if (inBooking != null) {
            UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
            if (unitFinder != null) {
                List<Unit> units = unitFinder.findUnitsForOrder(inBooking);
                if (units != null && units.size() > 0) {
                    for (Unit unit : units) {
                        refreshUnit(unit);
                    }
                    RoadBizUtil.commit();
                }
            }
        }
    }

    public String refreshUnit(Unit inUnit) {

        if (inUnit != null) {
            ImpedimentsBean impedimentsBean = inUnit.calculateImpediments(Boolean.TRUE);
            if (impedimentsBean != null) {
                Date dateNow = new Date(ArgoUtils.timeNowMillis() + 1);
                inUnit.updateImpediments(impedimentsBean, dateNow);
                HibernateApi.getInstance().saveOrUpdate(inUnit);
            }
        }

    }

}