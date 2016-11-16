import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.GoodsBase;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.framework.business.Roastery;
import com.navis.framework.persistence.HibernateApi;
import org.apache.log4j.Logger;
import com.navis.cargo.business.model.BillOfLading;
import com.navis.inventory.business.api.UnitField;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.business.reference.RoutingPoint;
import com.navis.argo.business.reference.LineOperator;
import com.navis.apex.business.model.GroovyInjectionBase;


import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType
import com.navis.framework.business.Roastery
import com.navis.framework.portal.FieldChanges
import com.navis.argo.ArgoBizMetafield
import com.navis.argo.ArgoField

/**
 *
 */
public class UpdateBlNumber extends AbstractEntityLifecycleInterceptor {

    String  blNumber = null;
    String	blDest = null;
    String  blLinrOptr = null;
    String	blPod1 = null;
    String  blPod2 = null;
    String  blPol = null;
    String  blOrgn = null;

    def inj = new GroovyInjectionBase();

    public void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        println("Groovy call: UpdateBlNumber.onUpdate()");
        try {
            BillOfLading blNbr =  inEntity._entity;
            System.out.println("BillOfLading<<<<<<<<>>>>>>>>>>>>>>>>>>"+blNbr.blNbr+" inOriginalFieldChanges "+inOriginalFieldChanges) ;

            blNumber = blNbr.blNbr;
            blDest = blNbr.blDestination;
            blLinrOptr = blNbr.blLineOperator.bzuId;
            blOrgn = blNbr.blOrigin;

            if (blNbr.blPol != null)
            {
                blPol = blNbr.blPol.pointId;
            }
            if (blNbr.blPod1 != null)
            {
                blPod1 = blNbr.blPod1.pointId;
            }
            if (blNbr.blPod2 != null)
            {
                blPod2 = blNbr.blPod2.pointId;
            }

            println("Bl Details:"+blNumber+":"+blDest+":"+blLinrOptr+":"+blOrgn+":"+blPol+":"+blPod1+":"+blPod2);

            getUnitListForAttachEvent(blNumber)
        } catch (Throwable e) {
            log("Testing " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void getUnitListForAttachEvent(String blNbr){
        println("MatUnitUpdateWithBlDetails.getUnitListForAttachEvent")
        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_GDS_BL_NBR,blNbr));
//			.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID,"GLDU0570236"));
        println("dq:::::::::"+dq)
        HibernateApi hibernate = HibernateApi.getInstance();
        List unitList  = hibernate.findEntitiesByDomainQuery(dq);
        Iterator iterUnitList = unitList.iterator()
        while (iterUnitList.hasNext())
        {
            def unit = iterUnitList.next();

            String eventId = "BL_UPDATE";
            try
            {
                println ("recordManifetEvent")

                EventManager sem = (EventManager) Roastery.getBean(EventManager.BEAN_ID);
                EventType eventType = EventType.findEventType(eventId);
                FieldChanges fld = new FieldChanges();
                fld.setFieldChange(ArgoBizMetafield.EVENT_APPLIED_TO_NATURAL_KEY,  unit.getUnitId());
                fld.setFieldChange(ArgoBizMetafield.EVENT_APPLIED_TO_GKEY, unit.getPrimaryKey());
                fld.setFieldChange(ArgoBizMetafield.EVENT_APPLIED_TO_CLASS, unit.getClass());
                unit.recordUnitEvent(eventType, fld, null);
            }
            catch (Throwable throwable) {
                println("Unexpected Error occured while recording service event" + throwable);
            }

        }
    }

}

