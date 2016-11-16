import com.navis.argo.business.api.GroovyApi
import com.navis.services.business.event.GroovyEvent
import com.navis.edi.business.entity.EdiSession
import com.navis.framework.portal.UserContext
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.ContextHelper
import com.navis.edi.business.atoms.EdiMessageDirectionEnum
import com.navis.edi.business.entity.EdiBatch
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.Ordering;
import com.navis.framework.business.Roastery;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.edi.EdiEntity;
import com.navis.edi.EdiField
import com.navis.edi.business.entity.EdiTransaction
import com.navis.edi.business.entity.EdiError;
import com.navis.edi.business.atoms.EdiStatusEnum;
import com.navis.edi.business.api.EdiFinder;

import com.navis.argo.ArgoBizMetafield;
import com.navis.services.business.api.EventManager;
import com.navis.services.business.rules.EventType;
import com.navis.framework.persistence.HibernatingEntity;
import com.navis.framework.portal.FieldChanges;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.InventoryField;


/*
* Copyright (c) 2010 Zebra Technologies Corp. All Rights Reserved.
*  AUTHOR: Siva Raja
*  Date Written: 04/20/2011
*  Requirements: For every 350 US CUstoms batches which are in error status
   with error message "vessel.encoding_not_found". Such batch status will be updated to Cancelled status. This groovy is run as Groovy Plugin based on
   General Notices event EDI_POST_FAILED.

* Modified By : Raghu Iyer
* Date        : 09-26-2012
* Change Desc : Getting the container number after EDI_POST to get the vesvoy. using this vesvoy Import discrapancies report will be gererated.

*/

public class MatGetStowplanTrankey extends GroovyApi{

    public String execute(GroovyEvent ipEvent, Object api){
        Thread.sleep(2500);
        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());
        System.out.println("MatGetStowplanTrankey Started !" + timeNow);
        String containerId = null;
        String vesVoy = null;

        EdiSession session = (EdiSession) ipEvent.getEntity();
        Serializable inSessionGkey = (Serializable) session.getEdisessGkey();
        EdiMessageDirectionEnum ediDirection = EdiMessageDirectionEnum.R;
        EdiBatch currentEdiBatch = this.getLatestEdiBatch(inSessionGkey, ediDirection);
        if (currentEdiBatch == null){
            println (" No EDI Batches found");
            println("MatGetStowplanTrankey Ended !" + timeNow);
            return;
        } else {
            println ("*********************************************************")
            println("Batch Number:" + currentEdiBatch.getEdibatchNbr().toString());
            println ("Batch Transaction Count:" + currentEdiBatch.getTransactionCount().toString());
            List<EdiTransaction> tranList = this.getEdiFndr().findTxnForBatch(currentEdiBatch);
            if (tranList.size() == 0) {
                println ("Batch has no transaction;")
                return;
            }
            EdiTransaction trans = tranList.get(0);
            Serializable tranGkey = trans.getEditranGkey();
            containerId = trans.getEditranPrimaryKeywordValue();
            println("tranList  " + tranList.size() +" "+tranList +" tranGkey " + tranGkey);
            println ("Transaction Primary KeyWord:" + trans.getEditranPrimaryKeywordValue()+" containerId "+containerId);

            Iterator unitList = tranList.iterator();
            println("unitList "+unitList)
            while (unitList.hasNext()) {
                def unit = unitList.next();
                def UnitId = unit.getEditranPrimaryKeywordValue();
                println ("unitList KeyWord:" + UnitId)
                vesVoy = attachStowplanEvent(UnitId);
                println("vesVoy" + vesVoy);

            }

            return vesVoy;
        }
        println("MatGetStowplanTrankey Ended !" + timeNow);
    }

    public static EdiBatch getLatestEdiBatch(Serializable inSessionGkey, EdiMessageDirectionEnum inDirection) {
        DomainQuery dq = QueryUtils.createDomainQuery(EdiEntity.EDI_BATCH)
                .addDqPredicate(PredicateFactory.eq(EdiField.EDIBATCH_SESSION, inSessionGkey))
                .addDqPredicate(PredicateFactory.eq(EdiField.EDIBATCH_DIRECTION, inDirection))
                .addDqOrdering(Ordering.desc(EdiField.EDIBATCH_CREATED));
        List batches = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        return batches != null && !batches.isEmpty() ? (EdiBatch) batches.get(0) : null;
    }

    private EdiFinder getEdiFndr() {
        return (EdiFinder) Roastery.getBean(EdiFinder.BEAN_ID);
    }

    private boolean findtranErrorMessage(Serializable inTranGkey){

        List<EdiTransaction> transaction = this.findEdiTransaction(inTranGkey);
        //It is assumed that US Customs Batch always has just 1 transaction.
        boolean returnValue = false;
        EdiTransaction trans = (EdiTransaction) transaction[0];
        println ("Transaction Number:" + trans.getEditranControlNbr().toString());
        println(" Transaction KeyWord:" + trans.getEditranPrimaryKeywordValue());
        List<EdiError> errors =  this.findEdiError(trans);
        errors.each { EdiError error ->
            println ("Error Message:" + error.getEdierrMessage().getMessageKey().getKey());
            if (error.getEdierrMessage().getMessageKey().getKey() == "vessel.encoding_not_found"){
                println (" Return Value: " + "true");
                returnValue = true;
            }else{
                println (" Return Value:" + "false");
                returnValue = false;
            }
        }
        return returnValue;
    }

    private static List<EdiTransaction> findEdiTransaction (Serializable inTranGkey){

        DomainQuery dq = QueryUtils.createDomainQuery(EdiEntity.EDI_TRANSACTION)
                .addDqPredicate(PredicateFactory.in(EdiField.EDITRAN_GKEY, inTranGkey));

        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }


    private boolean updateBatchStatus(EdiBatch inEdiBatch){
        if (inEdiBatch == null){
            println ("inEdiBatch is Null");
            return;
        }
        try{
            inEdiBatch.setEdibatchStatus(EdiStatusEnum.CANCELLED);
            HibernateApi.getInstance().saveOrUpdate(this);
        } catch (Exception e){
            println (" Exception in Updating Edi Batch" + e);
        }
    }

    public static List<EdiError> findEdiError(EdiTransaction inTransaction){
        DomainQuery dq = QueryUtils.createDomainQuery(EdiEntity.EDI_ERROR)
                .addDqPredicate(PredicateFactory.eq(EdiField.EDIERR_TRANSACTION,inTransaction.getEditranGkey()));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    public String attachStowplanEvent(containerId)
    {

        try {
            println("Inside getUnit");
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("Unit");
            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_ID,containerId));

            println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());

            def itemUnit = unitList.get(0);

            String VesVoy = itemUnit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId");

            String stowEventType = "EDI_STOWPLAN";

            EventManager sem = (EventManager) Roastery.getBean(EventManager.BEAN_ID);
            EventType eventType = EventType.findEventType(stowEventType);
            FieldChanges fld = new FieldChanges();
            fld.setFieldChange(ArgoBizMetafield.EVENT_APPLIED_TO_NATURAL_KEY,  itemUnit.getUnitId());
            fld.setFieldChange(ArgoBizMetafield.EVENT_APPLIED_TO_GKEY, itemUnit.getPrimaryKey());
            fld.setFieldChange(ArgoBizMetafield.EVENT_APPLIED_TO_CLASS, itemUnit.getClass());
            itemUnit.recordUnitEvent(eventType, fld, null);

            return VesVoy;

        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

}