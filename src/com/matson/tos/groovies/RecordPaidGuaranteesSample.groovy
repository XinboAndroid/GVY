/*
* Copyright (c) 2007 Navis LLC. All Rights Reserved.
* $Id: RecordPaidGuaranteesSample.groovy,v 1.1 2016/10/05 21:10:21 vnatesan Exp $
*/

import com.navis.external.framework.util.EFieldChanges
import com.navis.argo.business.api.ServicesManager
import com.navis.framework.util.ValueObject
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.services.IServiceExtract
import com.navis.inventory.InventoryBizMetafield
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.ChargeableMarineEvent
import com.navis.inventory.InventoryPropertyKeys
import com.navis.framework.util.BizViolation
import com.navis.framework.portal.FieldChanges
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.atoms.PaymentTypeEnum
import com.navis.framework.metafields.MetafieldId
import com.navis.external.framework.util.EFieldChange
import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.reference.ContactInfo
import com.navis.inventory.external.inventory.AbstractRecordGuarantee
import com.navis.inventory.external.inventory.ERecordGuarantee
import com.navis.framework.util.DateUtil

/**
 * This is a Pre-Deployable Groovy Plug-in which creates PAID guarantees for Invoice items.
 * NOTE: This groovy should be copied and renamed as "RecordGuarantee" when used in Code Extensions
 * @author <a href="mailto:tramakrishnan@navis.com"> tramakrishnan</a> Dec 30, 2009 Time: 2:36:01 PM
 */
public class RecordPaidGuaranteesSample extends AbstractRecordGuarantee implements ERecordGuarantee {

    public void recordPaidGuarantee(EFieldChanges inChanges) {
        log("Groovy: RecordPaidGuaranteeSample started to record paid guarantees!");
        System.out.println("Groovy: RecordPaidGuaranteeSample started to record paid guarantees!");

        if (inChanges.hasFieldChange(InventoryBizMetafield.INVOICE_ITEMS)) {

            ServicesManager servicesMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
            List itemList = (List) inChanges.findFieldChange(InventoryBizMetafield.INVOICE_ITEMS).getNewValue();
            for (Object itemObj: itemList) {
                //need to find the source event
                ValueObject chargeValueObj = (ValueObject) itemObj;
                String extractGkey = (String) chargeValueObj.getFieldValue(InventoryBizMetafield.EXTRACT_GKEY);
                BillingExtractEntityEnum extractEnum = BillingExtractEntityEnum.getEnum((String) chargeValueObj.getFieldValue(
                        InventoryBizMetafield.EXTRACT_CLASS));
                IServiceExtract serviceExtract;
                if (BillingExtractEntityEnum.INV.equals(extractEnum)) {
                    serviceExtract = (IServiceExtract) HibernateApi.getInstance().get(ChargeableUnitEvent.class, Long.valueOf(extractGkey));
                } else if (BillingExtractEntityEnum.MARINE.equals(extractEnum)) {
                    serviceExtract = (IServiceExtract) HibernateApi.getInstance().get(ChargeableMarineEvent.class, Long.valueOf(extractGkey));
                } else {
                    throw BizViolation.create(InventoryPropertyKeys.ENTITY_NOT_FOUND_BY_GKEY, null, extractEnum.getKey(), extractGkey);
                }
                Guarantee guarantee = createGuarantee(serviceExtract, extractEnum, chargeValueObj, inChanges);
                if (guarantee != null) {
                    log("Groovy: RecordPaidGuaranteeSample - Guarantee with ID : " + guarantee.getGnteGuaranteeId() + "  for event  ID: " + serviceExtract.getEventType() + " for UNIT: " + serviceExtract.getServiceEntityId()
                            + " created successfully !");
                    System.out.println("Groovy: RecordPaidGuaranteeSample - Guarantee with ID : " + guarantee.getGnteGuaranteeId() + "  for event  ID: " + serviceExtract.getEventType() + " for UNIT: " + serviceExtract.getServiceEntityId()
                            + " created successfully !");
                } else {
                    log("Groovy: RecordPaidGuaranteeSample - Guarantee creation for event  ID: " + serviceExtract.getEventType() + " for UNIT: " + serviceExtract.getServiceEntityId() + " falied !");
                    System.out.println("Groovy: RecordPaidGuaranteeSample - Guarantee creation for event  ID: " + serviceExtract.getEventType() + " for UNIT: " + serviceExtract.getServiceEntityId() + " falied !");
                }
            }
        } else {
            log("Groovy: RecordPaidGuaranteeSample - No invoice items found !");
            System.out.println("Groovy: RecordPaidGuaranteeSample - No invoice items found !");
        }

        log("Groovy: RecordPaidGuaranteeSample end to record paid guarantees!");
        System.out.println("Groovy: RecordPaidGuaranteeSample end to record paid guarantees!");
    }


    /**
     * Returns the guarantee which is created for the extract event
     */
    private Guarantee createGuarantee(IServiceExtract inIServiceExtract, BillingExtractEntityEnum inEntityEnum, ValueObject inItemValuObject, EFieldChanges inEFieldChanges) {
        Guarantee gtr = null;
        FieldChanges changes = prepareGuaranteeFieldChanges(inIServiceExtract, inEntityEnum, inItemValuObject, inEFieldChanges);
        if (changes != null) {
            gtr = new Guarantee();
            String gtId = gtr.getGuaranteeIdFromSequenceProvide();
            changes.setFieldChange(ArgoExtractField.GNTE_GUARANTEE_ID, gtId);
            gtr.applyFieldChanges(changes);
            HibernateApi.getInstance().saveOrUpdate(gtr);
        }
        return gtr;
    }


    /**
     * Prepares FieldChanges for the new guarantee from extract event and and fieldchanges and returns.
     */
    private FieldChanges prepareGuaranteeFieldChanges(IServiceExtract inIServiceExtract, BillingExtractEntityEnum inEntityEnum, ValueObject inItemValuObject, EFieldChanges inEFieldChanges) {
        ValueObject vao = new ValueObject(ArgoExtractEntity.GUARANTEE);
        //Guarantee.Chargeable Event gkey from the chargeable event
        vao.setFieldValue(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY, inIServiceExtract.getServiceExtractGkey());
        //Guarantee.AppliedToNaturalKey unitId from chargeable event
        vao.setFieldValue(ArgoExtractField.GNTE_APPLIED_TO_NATURAL_KEY, inIServiceExtract.getServiceEntityId());
        //Guarantee.AppliedToClass eventAppliedToClass from chargeable event
        vao.setFieldValue(ArgoExtractField.GNTE_APPLIED_TO_CLASS, inEntityEnum);

        //Guarantee.Customer from the Payee specfied in the Query Charges dialog
        Long payeeGkey = (Long) getNewFieldValue(inEFieldChanges, InventoryBizMetafield.INVOICE_PAYEE);
        ScopedBizUnit payee = (ScopedBizUnit) HibernateApi.getInstance().get(ScopedBizUnit.class, payeeGkey);
        vao.setFieldValue(ArgoExtractField.GNTE_GUARANTEE_CUSTOMER, payee);
        //Guarantee.Guarantee Type is Paid.
        vao.setFieldValue(ArgoExtractField.GNTE_GUARANTEE_TYPE, GuaranteeTypeEnum.PAID);
        //Guaratee.Payment Type from the Payment Type entered in Pay Unit Charges dialog
        String payTypeStr = (String) getNewFieldValue(inEFieldChanges, InventoryBizMetafield.PAYMENT_TYPE);
        PaymentTypeEnum paymentType = PaymentTypeEnum.getEnum(payTypeStr);
        vao.setFieldValue(ArgoExtractField.GNTE_PAYMENT_TYPE, paymentType);
        //Guarantee.Payment # as entered from Pay Unit Charges dialog
        Long chequeNbr = (Long) getNewFieldValue(inEFieldChanges, InventoryBizMetafield.CHECK_NUMBER);
        vao.setFieldValue(ArgoExtractField.GNTE_PAYMENT_NBR, String.valueOf(chequeNbr));
        //Guarantee.Payment Remarks as entered from Pay Unit Charges dialog
        String gnteNotes = "Recorded through Groovy: " + this.getClass().getName();
        vao.setFieldValue(ArgoExtractField.GNTE_NOTES, gnteNotes);
        //Guarantee.Payment Date as from Pay Unit Charges
        vao.setFieldValue(ArgoExtractField.GNTE_PAYMENT_DATE, new Date());
        //Guarantee.N4 User ID from the user ID of the person making the Guarantee
        String n4UserId = ContextHelper.getThreadUserId();
        vao.setFieldValue(ArgoExtractField.GNTE_N4_USER_ID, n4UserId);
        // Guarantee.Quantity is the Quantity from invoice item
        String quantity = inItemValuObject.getFieldString(InventoryBizMetafield.QUANTITY_BILLED);
        vao.setFieldValue(ArgoExtractField.GNTE_QUANTITY, new Double(quantity));
        //Guarantee.Amount is the sum of invoice line item amounts for chargeable event
        Double amount = (Double) getNewFieldValue(inEFieldChanges, InventoryBizMetafield.PAYMENT_AMOUNT);
        vao.setFieldValue(ArgoExtractField.GNTE_GUARANTEE_AMOUNT, amount);
        //Guarantee.First Day from LFD or GTD (storage invoice line items are processed on order to advance GTD as needed)
        String evntPerformedFromString = inItemValuObject.getFieldString(InventoryBizMetafield.EVENT_PERFORMED_FROM);
        Date evntPerformedFrom;
        if (evntPerformedFromString != null && !evntPerformedFromString.isEmpty()) {
            try {
                evntPerformedFrom = DateUtil.xmlDateStringToDate(evntPerformedFromString);
            } catch (Exception e) {
                log("eventPerformedFromDate cannot be parsed due to" + e);
            }
        }
        vao.setFieldValue(ArgoExtractField.GNTE_GUARANTEE_START_DAY, evntPerformedFrom);
        //Guarantee.Last Day from Proposed PTD or Guarantee First Day from any pre-existing storage Guarantees for the unit
        String evntPerformedToString = inItemValuObject.getFieldString(InventoryBizMetafield.EVENT_PERFORMED_TO);
        Date evntPerformedTo;
        if (evntPerformedToString != null && !evntPerformedToString.isEmpty()) {
            try {
                evntPerformedTo = DateUtil.xmlDateStringToDate(evntPerformedToString);
            } catch (Exception e) {
                log("eventPerformedToDate cannot be parsed due to" + e);
            }
        }

        evntPerformedTo = DateUtil.xmlDateStringToDate(evntPerformedToString);
        vao.setFieldValue(ArgoExtractField.GNTE_GUARANTEE_END_DAY, evntPerformedTo);

        // Guarantee.User Details
        // Guarantee.External User Profile:
        // User ID is blank
        vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_USER_ID, "");
        ContactInfo contact = payee.getBzuCtct();
        if (contact != null) {
            //    Contact Name from Payee.Contact Name
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_CONTACT_NAME, contact.getCtctName());
            //    address line 1 from Payee.Contact address line 1
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_ADDRESS1, contact.getCtctAddressLine1());
            //    address line 2 from Payee.Contact address line 2
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_ADDRESS2, contact.getCtctAddressLine2());
            //    address line 3 from Payee.Contact address line 3
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_ADDRESS3, contact.getCtctAddressLine3());
            //    city from Payee.Contact city
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_CITY, contact.getCtctCity());
            //    state from Payee.Contact state
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_STATE, contact.getCtctState());
            //    country from Payee.Contact country
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_COUNTRY, contact.getCtctCountry());
            //    mailcode from Payee.Contact mailcode
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_MAIL_CODE, contact.getCtctMailCode());
            //    telephone from Payee.Contact telephone
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_TELEPHONE, contact.getCtctTel());
            //    email address from Payee.Contact email address
            vao.setFieldValue(ArgoExtractField.GNTE_EXTERNAL_EMAIL_ADDRESS, contact.getCtctEmailAddress());
        }
        return new FieldChanges(vao);
    }


    /**
     * Returns the new field value from the field changes if field change is present for the requested metafield, else null is returned
     */
    private Object getNewFieldValue(EFieldChanges inFieldChanges, MetafieldId inMetafieldId) {
        EFieldChange fieldChange = inFieldChanges.findFieldChange(inMetafieldId);
        if (fieldChange != null) {
            return fieldChange.getNewValue();
        }
        return null;
    }
}
