import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.VisitDetails
import com.navis.argo.business.reference.CarrierService
import com.navis.argo.business.reference.Equipment
import com.navis.argo.util.FieldChangeTracker
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.UserContext
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitReroutePoster
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.orders.business.util.OrderManagerUtils
import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType
import com.navis.framework.portal.UserContext
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.event.Event;
import org.apache.log4j.Logger
import com.navis.argo.business.api.GroovyApi

/*
* Copyright (c) 2014 Navis LLC. All Rights Reserved.
*
*/


public class MATUpdateUnitBkgDetails extends GroovyApi {

    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

    public void execute(GroovyEvent event, Object api)
    {
        LOGGER.info(" MATUpdateUnitBkgDetails started " + timeNow);

        def unit = event.getEntity();
        def bookingNbr;
        def ue = unit.getUnitPrimaryUe();
        if (ue.getUeDepartureOrderItem() != null) {
            bookingNbr = ue.getUeDepartureOrderItem().getEqboiOrder().getEqboNbr();
        }
        LOGGER.info(" Test1 Booking Nbr" + bookingNbr);
        //unit.unitActiveUfv.ufvIntendedObCv = unit.unitRouting.rtgDeclaredCv
        //unit.unitActiveUfv.ufvActualObCv = unit.unitRouting.rtgDeclaredCv

        Routing oldRtg = unit.getUnitRouting();
        Routing newRouting;
        if (oldRtg != null) {
            newRouting = oldRtg;
            // keep a copy of the old rtg to define changes for returned changed feilds
            oldRtg = oldRtg.getDefensiveCopy();
        } else {
            newRouting = new Routing();
        }
        LOGGER.info(" Test2 ");
        List bkgList = Booking.findBookingsByNbr(bookingNbr);
        Booking book;
        if (bkgList != null){
            book = (Booking) bkgList.get(0);
            newRouting.setRtgPOL(book.getEqoPol());
            newRouting.setRtgPOD1(book.getEqoPod1());
            newRouting.setRtgPOD2(book.getEqoPod2());
            newRouting.setRtgOPT1(book.getEqoPodOptional());
        }
        LOGGER.info(" Test3 ");
        String shipper = book.getShipperAsString();
        String consignee = book.getConsigneeAsString();
        String origin = book.getEqoOrigin();
        String destination = book.getEqoDestination();
        unit.modifyGoodsDetails(shipper, consignee, origin, destination);
        LOGGER.info("GvyUpdateUnitRemark ended" + timeNow);
    }
    private static final Logger LOGGER = Logger.getLogger(MATUpdateUnitBkgDetails.class);
}