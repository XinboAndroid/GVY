/*
* Srno   Doer   Date      Change
* A1     GR     04/30/10  Added Consignee Fix to Detention
* A2     GR     05/19/10  Change for DTD
* A3     GR     07/08/10  DTD - Added consignee for builtin
* A4     GR     01/27/11  Commodity XMAS Tree to port DTD,DTA
*/

import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Shipper

public class GvyCmisCargoEditDetention {
    def cmisActnList = '';
    def misc3 = '';
    def dir = '';
    def dischPort = '';
    def locationStatus = '';
    def detnMsg = false;
    boolean freightkindChng = false;
    boolean categoryChng = false;
    boolean consigneeChng = false;
    boolean updtdischPort = false;
    boolean lineOperatorChng = false;

    def unit = null;
    def commodity = null;
    boolean commodityChng = false; //A4

    //A2 Change For DTD
    Map _mapPrevField = null;
    def gvyCmisUtil = null;
    def gvyEventUtil = null;

    //Initialize class for Global use
    public void init(Object gvyBaseClass) {
        gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
        gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
        cmisActnList = gvyBaseClass.getGroovyClassInstance("GvyCmisListAction");
    }


    public boolean detentionProcess(String xmlData, Object event, Object gvyBaseClass, String prevDischPort) {
        try {
            def xmlGvyString = xmlData
            unit = event.getEntity()
            def gvyEventObj = event.getEvent()
            String eventType = gvyEventObj.getEventTypeId()
            def previousDischPort = prevDischPort != null ? prevDischPort : ''

            dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")

            def equiClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def equiClassKey = equiClass != null ? equiClass.getKey() : equiClass
            if (!equiClassKey.equals('CONTAINER')) {
                return false;
            }

            init(gvyBaseClass)

            if (!gvyEventObj.getEvntEventType().getEvnttypeIsBuiltInEvent() || eventType.equals('UNIT_PROPERTY_UPDATE')) {
                setEvntFieldChngNonBuiltEvnt(event, gvyEventUtil, gvyBaseClass, previousDischPort)
            }

            //A8
            commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
            commodity = commodity != null ? commodity : ''

            misc3 = gvyCmisUtil.getFieldValues(xmlGvyString, "misc3=")
            dir = gvyCmisUtil.getFieldValues(xmlGvyString, "dir=")
            locationStatus = getlocationStatus(unit, gvyCmisUtil)
            //println('detentionProcess : misc3:'+misc3+'  dir:'+dir+'   locationStatus:'+locationStatus+'freightkindChng :'+freightkindChng+'  consigneeChng:'+consigneeChng+'   updtdischPort:'+updtdischPort)

            if (updtdischPort) {
                detentionDischPortMsg(gvyCmisUtil, gvyEventUtil, event, gvyBaseClass, unit, previousDischPort)
            } else if (freightkindChng) {
                detentionFreightMsg(gvyCmisUtil, gvyEventUtil, event, unit, freightkindChng)
            } else if (consigneeChng) {
                detentionConsigneeMsg(gvyCmisUtil, gvyEventUtil, event, unit)
            } else if (categoryChng) {
                detentionForCategory(unit)
            } else if (lineOperatorChng) {
                cmisActnList.setActionList("DTD")
                cmisActnList.setActionList("DTA")
                detnMsg = true
            } else if (commodityChng && (_mapPrevField.get('commodity') != null && _mapPrevField.get('commodity').contains('XMAS')) ||
                    commodity.contains('XMAS')) //A4
            {
                cmisActnList.setActionList("DTD")
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }

            //DetentionAcetsMessage Check Do not Append EDT & FREE
            if (!detentionAcetsMsgFilter(gvyEventObj)) {
                appendMsgOnEvent(eventType)
            }

            //Post Detention MSG Action
            LinkedHashSet actionList = cmisActnList.getActionList();
            for (aAction in actionList) {
                if ('DTD'.equals(aAction)) {
                    String reFmtDtdXml = reformatDTDwithPrevValues(xmlGvyString, gvyCmisUtil)
                    gvyCmisUtil.postMsgForAction(reFmtDtdXml, gvyBaseClass, aAction)
                } else {
                    gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, aAction)
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }

        return detnMsg
    }

/*
* Method set the Detention Cmis action for Destination change
*/

    public void detentionDischPortMsg(Object gvyCmisUtil, Object gvyEventUtil, Object event, Object gvyBaseClass, Object unit, Object previousDischPort) {
        try {
            //latest Code Updt Starts
            def prevDischPortVal = previousDischPort

            def freightKind = unit.getFieldValue("unitFreightKind")
            freightKind = freightKind != null ? freightKind.getKey() : ''
            //latest Code Updt Ends

            cmisActnList.setActionList("DTD")
            detnMsg = true
            if (misc3.length() > 6) {
                cmisActnList.setActionList("DTA")
            }

            /* def prevPortNis = gvyCmisUtil.isNISPort(prevDischPortVal)
             def updtPortNis = gvyCmisUtil.isNISPort(dischPort)

             //println('prevDischPortVal::'+prevDischPortVal+'  updtPortNis ::'+updtPortNis+'   Detention NIS - prevPortNis: '+prevPortNis)
             if(freightKind.equals('FCL') && ((prevDischPortVal.equals(ContextHelper.getThreadFacility().getFcyId()) ) || ( locationStatus.equals('7'))))
             {
                 cmisActnList.setActionList("DTD")
                 detnMsg = true
              }
              else
             {
                  cmisActnList.setActionList("DTD")
                if(misc3.length() > 6 || updtPortNis ){
                  cmisActnList.setActionList("DTA")
                }
                detnMsg = true
             }*///else Ends
            // }//If ends
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

/*
* Method set the Detention Cmis action for FreightKind and consignee change
*/

    public void detentionFreightMsg(Object gvyCmisUtil, Object gvyEventUtil, Object event, Object unit, boolean freightkindChng) {
        try {
            def currFreightKind = unit.getFieldValue("unitFreightKind")
            currFreightKind = currFreightKind != null ? currFreightKind.getKey() : ''
            def prevFreightKind = currFreightKind
            if (freightkindChng) {
                prevFreightKind = gvyEventUtil.getPreviousPropertyAsString(event, "unitFreightKind")
                prevFreightKind = prevFreightKind != null ? prevFreightKind : ''
            }

            //def dischPortNis = gvyCmisUtil.isNISPort(dischPort)
            if (dischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && prevFreightKind.equals('FCL')) {
                cmisActnList.setActionList("DTD")
                detnMsg = true
            } else if (dischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && currFreightKind.equals('FCL')) {
                if (misc3.length() > 6) {
                    cmisActnList.setActionList("DTA")
                    detnMsg = true
                }
            } else if (/*dischPortNis &&*/ locationStatus.equals('7') && prevFreightKind.equals('FCL')) {
                cmisActnList.setActionList("DTD")
                detnMsg = true
            } else if (/*dischPortNis &&*/ locationStatus.equals('7') && currFreightKind.equals('FCL')) {
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

//A1
    public void detentionConsigneeMsg(Object gvyCmisUtil, Object gvyEventUtil, Object event, Object unit) {
        try {
            def currFreightKind = unit.getFieldValue("unitFreightKind")
            currFreightKind = currFreightKind != null ? currFreightKind.getKey() : ''
            def prevFreightKind = currFreightKind

            //def dischPortNis = gvyCmisUtil.isNISPort(dischPort)
            if (dischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && currFreightKind.equals('FCL')) {
                cmisActnList.setActionList("DTD")
                if (misc3.length() > 6) {
                    cmisActnList.setActionList("DTA")
                }
                detnMsg = true
            } else if ( locationStatus.equals('7') && currFreightKind.equals('FCL')) {
                cmisActnList.setActionList("DTD")
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    public void appendMsgOnEvent(String eventType) {

        // if(availDateChng || detentionDateChng)
        if (!(eventType.equals('UNIT_PROPERTY_UPDATE') || eventType.equals('UNIT_REROUTE') || eventType.equals('CARGO_EDIT') || eventType.equals('REVIEW_FOR_STOW') || eventType.equals('SIT_ASSIGN') || eventType.equals('SIT_UNASSIGN'))) {
            //Passing Detention Msg
            if (detnMsg) {
                cmisActnList.setActionList("FREE")
                cmisActnList.setActionList("EDT")
            }
        }
    }

    public boolean detentionAcetsMsgFilter(Object gvyEventObj) {
        try {
            //DOER
            def doer = gvyEventObj.getEvntAppliedBy();
            def eventNotes = gvyEventObj.getEventNote()
            eventNotes = eventNotes != null ? eventNotes : ''
            CharSequence acetsMsg = "ACETS";
            boolean acetsRecorder = doer.contains(acetsMsg);
            boolean evntNotes = eventNotes.contains(acetsMsg);

            if (acetsRecorder || evntNotes) {
                return true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return false
    }

    public void detentionForCategory(Object unit) {
        try {
            def category = unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''

            if (category.equals('EXPRT')) {
                cmisActnList.setActionList("DTD")
                cmisActnList.setActionList("DTA")
                detnMsg = true
            } else if (category.equals('IMPRT')) {
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
    }//Method Ends

    public void getlocationStatus(Object unit, Object gvyCmisUtil) {
        try {
            def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
            def lkpCarrierId = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")
            locationStatus = lkpLocType.equals('VESSEL') && gvyCmisUtil.getVesselClassType(lkpCarrierId).equals('BARGE') ? '7' : ''
        } catch (Exception e) {
            e.printStackTrace()
        }
    }


    public void setEvntFieldChngNonBuiltEvnt(Object event, Object gvyEventUtil, Object gvyBaseClass, String previousDischPort) {
        //Reads and Maps Event Updated Field value
        Map mapEvntField = gvyEventUtil.eventFieldChangedValues(event, gvyBaseClass)
        _mapPrevField = new HashMap();

        String fieldArr = ["gdsDestination", "gdsConsigneeBzu", "unitFreightKind", "unitCategory", "unitLineOperator", "gdsShipperBzu", "gdsConsigneeAsString", "gdsCommodity"]
        try {
            Iterator it = mapEvntField.keySet().iterator();
            while (it.hasNext()) {
                def aField = it.next();
                if (!fieldArr.contains(aField)) {
                    continue;
                }

                //Fetch Event Updated Field : current and Previous value
                def aEvntFieldObj = mapEvntField.get(aField)
                def fieldname = aEvntFieldObj.getFieldName()
                def previousValue = aEvntFieldObj.getpreviousValue()
                previousValue = previousValue != null ? previousValue : ''
                def currentValue = aEvntFieldObj.getCurrentValue()
                currentValue = currentValue != null ? currentValue : ''

                //println('aField :'+aField+'  fieldname ::'+fieldname+'  previousValue::'+previousValue+'   currentValue::'+currentValue)
                if (aField.equals("gdsDestination") && (!previousDischPort.equals(dischPort))) {
                    updtdischPort = true;
                    _mapPrevField.put('dischargePort', previousValue)
                    _mapPrevField.put('dPort', previousValue)
                } else if (!currentValue.equals(previousValue)) {
                    if (aField.equals("gdsConsigneeBzu")) {
                        consigneeChng = true
                        _mapPrevField.put('consignee', previousValue)
                    } else if (aField.equals("gdsConsigneeAsString")) {
                        consigneeChng = true
                        _mapPrevField.put('consignee', previousValue)
                    } else if (aField.equals("unitFreightKind")) {
                        freightkindChng = true
                        _mapPrevField.put('orientation', previousValue)
                    } else if (aField.equals("unitCategory")) {
                        categoryChng = true
                        _mapPrevField.put('category', previousValue)
                    } else if (aField.equals("unitLineOperator")) {
                        lineOperatorChng = true
                        _mapPrevField.put('locationRow', previousValue)
                    } else if (aField.equals("gdsShipperBzu")) {
                        _mapPrevField.put('shipper', previousValue)
                    } else if (aField.equals("gdsCommodity")) { //A4
                        commodityChng = true
                        //def gvyCmisEventSIT =  gvyBaseClass.getGroovyClassInstance("GvyCmisEventSIT");
                        //def preCommodity  = gvyCmisEventSIT.lookupCommodity(previousValue)
                        //println("previousValue=================="+previousValue)
                        _mapPrevField.put('commodity', previousValue)
                    }
                }//Inner If
            }//While Ends
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    //Method Reformats the messages with previous values
    public String reformatDTDwithPrevValues(String xmlGvyString, Object gvyCmisUtil) {
        String reformattedXmlStr = xmlGvyString
        try {
            if (_mapPrevField == null) {
                return;
            }
            Iterator it = _mapPrevField.keySet().iterator();
            while (it.hasNext()) {
                def aKey = it.next();
                def prevalue = _mapPrevField.get(aKey)
                if ('consignee'.equals(aKey)) {
                    reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr, "cneeCode=", prevalue)
                    def shipper = Shipper.findShipper(prevalue)
                    if (shipper != null) {
                        prevalue = shipper != null ? shipper.bzuName : ""
                        //println('consignee prevalue = '+prevalue+' shipper='+shipper+' shipper.bzuName='+shipper.bzuName)
                    }
                    //Add code to Overwrite Shipper & Consignee Id
                } else if ('shipper'.equals(aKey)) {
                    reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr, "shipperId=", prevalue)
                    def shipper = Shipper.findShipper(prevalue)
                    prevalue = shipper != null ? shipper.bzuName : ''
                } else if ('orientation'.equals(aKey)) {
                    prevalue = 'MTY'.equals(prevalue) ? 'E' : (prevalue.length() > 1 ? 'F' : '')
                }
                reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr, aKey + "=", prevalue)

            }

            //Set DTD misc3 value
            def prevMisc3 = getPreviousMisc3(reformattedXmlStr);
            reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr, "misc3=", prevMisc3)

            //Set last Free Date
            def prevlastFreeDay = getPrevLastFreeDay(reformattedXmlStr)
            reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr, "locationCategory=", prevlastFreeDay)

        } catch (Exception e) {
            e.printStackTrace();
        }
        return reformattedXmlStr
    }

    public String getPreviousMisc3(String xmlGvyString) {
        def currMisc3 = gvyCmisUtil.getFieldValues(xmlGvyString, "misc3=")
        def pervMis3 = unit.getFieldValue("unitSealNbr4")

        //println('currMisc3='+currMisc3+'   pervMis3='+pervMis3)
        if (currMisc3 == null || !currMisc3.equals(pervMis3)) {
            unit.setUnitSealNbr4(currMisc3)
        }
        return pervMis3
    }


    public String getPrevLastFreeDay(String xmlGvyString) {
        def currFreeDay = gvyCmisUtil.getFieldValues(xmlGvyString, "locationCategory=")
        def pervLastFreeDay = unit.getFieldValue("unitSealNbr3")

        //println('currFreeDay='+currFreeDay+'   pervLastFreeDay='+pervLastFreeDay)
        if (currFreeDay == null || !currFreeDay.equals(pervLastFreeDay)) {
            unit.setUnitSealNbr3(currFreeDay)
        }
        return pervLastFreeDay
    }

}//Class Ends