/*
*  Change   Changer  Date       Desc
*  A1       GR       07/07/09   AMB Temp Req To cmis
*  A2       GR       11/24/09   Flip Mty Client Cntr Operator from MAT to client
*  A3       GR       03/17/2010 Added equipTypeCode field for DAS as we dont set for CMIS
*  A4       GR       08/30/10   Add HazOpenCloseFlag and compute trade value
*  A5       GR       10/30/11   TOS2.1 : Change UNIT_DISCH_COMPLETE
*  A6       GR       02/17/12   TOS2.1 : Updt Field unitFlexString07 to UfvFlexString07
*  A7       LC       12/05/12   Include typeCode value for CMIS_DATA_REFRESH event
*/

public class GvyCmisEquiDetail {

    public String doIt(Object gvyTxtMsgFmt, Object unitObj, Object event, Object isUnitObj, Object gvyBaseClass, String eventType) {
        def u = unitObj
        def equiFieldAttr = ''
        try {
            println("In Class GvyCmisEquiDetail.doIt()")
            def _equiType = u.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId")
            def eqHgt = u.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypNominalHeight")
            def equiHeight = eqHgt != null ? eqHgt.getKey() : ''
            def _equiMaterial = u.getFieldValue("unitPrimaryUe.ueEquipment.eqMaterial")
            _equiMaterial = _equiMaterial != null ? _equiMaterial.getKey() : ''

            //TYPE CODE & HGT
            def typeCode = '%'
            def equiHgtfmt = '%'
            if ((isUnitObj.equals(Boolean.TRUE) && event.wasFieldChanged('EquipmentType')) || eventType.equals('UNIT_IN_GATE')
                    || eventType.equals('CMIS_DATA_REFRESH') ||eventType.equals('UNIT_REROUTE')||eventType.equals('UNIT_ROLL')) {
                // TypeCode
                typeCode = TypeCodeProcessing(_equiType, equiHeight, _equiMaterial)
                //HGT - Processing
                def equiHgt = getEquiHeight(equiHeight)
                if (equiHgt.trim().length() > 0) {
                    equiHgtfmt = equiHgt.trim().length() > 2 ? "0" + equiHgt + "00" : "0" + equiHgt.substring(0, 1) + "0" + equiHgt.substring(1) + "00";
                }
            }
            def typeCodeAttr = gvyTxtMsgFmt.doIt('typeCode', typeCode)
            def hgtAttr = gvyTxtMsgFmt.doIt('hgt', equiHgtfmt)
            def equiTypeCodeAttr = gvyTxtMsgFmt.doIt('equipTypeCode', _equiType)

            //TARE WEIGHT
            def equiTareKg = u.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg")
            def equiTareLB = equiTareKg != null ? Math.round(equiTareKg * 2.20462262) : ''
            println('Unit_equiTareKg :' + equiTareKg + '    Unit_equiTareLB ::' + equiTareLB)
            def tareWeightAttr = gvyTxtMsgFmt.doIt('tareWeight', equiTareLB)

            //STRENGTH CODE
            def equiStrengthCode = u.getFieldValue("unitPrimaryUe.ueEquipment.eqStrengthCode")
            def strengthAttr = gvyTxtMsgFmt.doIt('strength', equiStrengthCode)

            //OWNER
            def _equiOwner = u.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId")
            def ownerAttr = gvyTxtMsgFmt.doIt('owner', _equiOwner)

            //DMG_CODE
            def damageCode = u.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsDamageSeverity")
            damageCode = damageCode != null ? damageCode.getKey() : damageCode
            def dmgCodeFmt = damageCode.equals('MAJOR') ? 'H' : (damageCode.equals('MINOR') ? 'L' : '')
            def damageCodeAttr = gvyTxtMsgFmt.doIt('damageCode', dmgCodeFmt)

            //SRV
            def equiOperator = '';
            def eventId = event.getEvent().getEventTypeId();
            println("Event Triggered : "+eventId);
            if (eventId.equals('UNIT_LOAD')) {
                equiOperator = getSrvUnitLoad(u)
            } else {
                equiOperator = getSrv(unitObj, gvyBaseClass)
            }
            def srvAttr = gvyTxtMsgFmt.doIt('srv', equiOperator)

            //HAZFLAG OPEN/CLOSE - A4
            def gvyCmisTrade = gvyBaseClass.getGroovyClassInstance('GvyCmisTrade')
            def hazOpenCloseFlag = gvyCmisTrade.processTrade(unitObj, equiOperator)
            def hazOpenCloseFlagAttr = gvyTxtMsgFmt.doIt('hazOpenCloseFlag', hazOpenCloseFlag)

            //TEMP & TEMPERATURE MEASUREMENT UNIT
            def tempConvUnit = null
            def tempMeasurementUnit = null
            def tempReq = u.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempRequiredC");
            def tempMin = u.getFieldValue("ufvUnit.unitGoods.gdsReeferRqmnts.rfreqTempLimitMinC");
            def tempSetting = u.getFieldValue("unitActiveUfv.ufvFlexString07"); //A07
            tempSetting = tempSetting != null ? tempSetting : ''
            if (tempReq == null) {
                tempReq = tempMin
            }
            if (tempReq == null && tempSetting.equals('AMB')) {
                tempConvUnit = 'AMB'
                tempMeasurementUnit = ''
            } else {
                if (equiOperator.equals('MAT')) {
                    tempConvUnit = tempReq != null ? Math.round((tempReq * 9 / 5) + 32) : tempReq
                    tempMeasurementUnit = tempConvUnit != null ? 'F' : ''
                } else {
                    tempConvUnit = tempReq != null ? Math.round(tempReq) : tempReq
                    tempMeasurementUnit = tempConvUnit != null ? 'C' : ''
                }
            }
            def tempAttr = gvyTxtMsgFmt.doIt('temp', tempConvUnit)
            def tempMeasureUnitAttr = gvyTxtMsgFmt.doIt('tempMeasurementUnit', tempMeasurementUnit)

            /**
             * temp2, tempMeasurementUnit2, tempSetting fields for ALASKA
             */
            def tempConvUnit2 = null;
            def tempMeasurementUnit2 = null
            def tempReq2 = u.getFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempRequiredC");
            def tempSetting2 = u.getFieldValue("unitActiveUfv.ufvFlexString07"); //A07
            tempSetting2 = tempSetting2 != null ? tempSetting2 : ''

            if (tempReq2 == null && tempSetting2.equals('AMB')) {
                tempConvUnit2 = 'AMB'
                tempMeasurementUnit2 = ''
            } else {
                if (equiOperator.equals('MAT')) {
                    tempConvUnit2 = tempReq2 != null ? Math.round((tempReq2 * 9 / 5) + 32) : tempReq2
                    tempMeasurementUnit2 = tempConvUnit2 != null ? 'F' : ''
                } else {
                    tempConvUnit2 = tempReq != null ? Math.round(tempReq2) : tempReq2
                    tempMeasurementUnit2 = tempConvUnit2 != null ? 'C' : ''
                }
            }

            def tempAttr2 = gvyTxtMsgFmt.doIt('temp2', tempConvUnit2);
            def tempMeasureUnitAttr2 = gvyTxtMsgFmt.doIt('tempMeasurementUnit2', tempMeasurementUnit2);
            def tempSettingAttr = gvyTxtMsgFmt.doIt('tempSetting', tempSetting);


            equiFieldAttr = tareWeightAttr + typeCodeAttr + hgtAttr + strengthAttr + ownerAttr + damageCodeAttr + srvAttr + tempAttr + tempMeasureUnitAttr + tempAttr2 + equiTypeCodeAttr + hazOpenCloseFlagAttr + tempSettingAttr

            //println("Equipment : "+equiFieldAttr)

        } catch (Exception e) {
            e.printStackTrace()
        }
        return equiFieldAttr
    }

//Equipment Material,Height Code mapping
    public String TypeCodeProcessing(String equiType, String equiHeight, String equiMaterial) {
        def equiMat = equiMaterial.equals('STEEL') ? 'ST' : (equiMaterial.equals('ALUMINUM') ? 'AL' : 'XX')
        def equiTypeFmt = equiType.substring(0, 3)
        if (equiType.length() > 4) {
            equiMat = equiType.substring(4);
        }
        def equiHgt = getEquiHeight(equiHeight)
        def typeCode = equiTypeFmt + ' ' + equiHgt + equiMat
        return typeCode;
    }

    private String getEquiHeight(String equiHeight) {
        def hgt = ''
        if (equiHeight.startsWith('NOM')) {
            hgt = equiHeight.length() > 5 ? equiHeight.substring(3) : equiHeight.substring(3)
            return hgt
        }
        return hgt
    }

    public String getSrv(Object unit, Object gvyBaseClass) {
        def srv = ''
        def vesselLineOptr = ''
        try {
            def unitLineOperator = unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId")
            unitLineOperator = unitLineOperator != null ? unitLineOperator : ''

            vesselLineOptr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoVesselVisit.cvCvd.vvdBizu.bzuId")
            vesselLineOptr = vesselLineOptr != null ? vesselLineOptr : ''

            def intObCarrierMode = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCarrierMode")
            intObCarrierMode = intObCarrierMode != null ? intObCarrierMode.getKey() : ''

            def dObCarreirmode = unit.getFieldValue("unitRouting.rtgDeclaredCv.cvCarrierMode")
            dObCarreirmode = dObCarreirmode != null ? dObCarreirmode.getKey() : ''

            //Get Equi SRV
            def unitEquipment = unit.getUnitPrimaryUe()
            def ueEquipmentState = unitEquipment.getUeEquipmentState()
            def equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : ''

            def ObDeclaredVesClassType = unit.getFieldValue("unitRouting.rtgDeclaredCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            ObDeclaredVesClassType = ObDeclaredVesClassType != null ? ObDeclaredVesClassType.getKey() : ""

            def intObCarVesType = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType")
            intObCarVesType = intObCarVesType != null ? intObCarVesType.getKey() : ""

            def bookingNumber = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");

            //Equipment Deliver Order Object
            def gvyEdoObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEquipmentDeliveryOrder");
            def edo = bookingNumber != null ? gvyEdoObj.findEquipmentDeliveryOrder(bookingNumber) : null

            def isVessel = (intObCarrierMode.equals('VESSEL') || dObCarreirmode.equals('VESSEL')) ? true : false
            def isBarge = (ObDeclaredVesClassType.equals('BARGE') || intObCarVesType.equals('BARGE')) ? true : false
            def isLongHaul = (ObDeclaredVesClassType.equals('CELL') || intObCarVesType.equals('CELL')) ? true : false
            def isVesLineOperatorMat = vesselLineOptr.equals('MAT') ? true : false

            println("EqFlex01 :" + equipFlex01 + " isVessel:" + isVessel + "  isBarge:" + isBarge + "  isLongHaul:" + isLongHaul + "   isVesLineOperatorMat:" + isVesLineOperatorMat)

            if (equipFlex01.equals('MAT')) {
                srv = 'MAT'
            } else if (edo != null) {
                srv = gvyEdoObj.getEDOLineOperator(edo)
            } else if (equipFlex01.startsWith("CLI") && isVessel && isBarge) {
                srv = 'MAT'
            } else if (equipFlex01.startsWith("CLI") && isVessel && isLongHaul && isVesLineOperatorMat) {
                srv = 'MAT'
            } else if (equipFlex01.startsWith("CLI") && bookingNumber && isVesLineOperatorMat) {
                srv = 'MAT'
            } else if (equipFlex01.startsWith("CLI") && isVessel && isLongHaul && !isVesLineOperatorMat) {
                srv = unitLineOperator
            } else if (equipFlex01.startsWith("CLI") && !isVessel) {
                srv = unitLineOperator
            } else {
                srv = unitLineOperator
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return srv
    }

    public String getSrvUnitLoad(Object unit) {

        def unitEquipment = unit.getUnitPrimaryUe()
        def ueEquipmentState = unitEquipment.getUeEquipmentState()

        def unitLineOperator = unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId")
        println ("Operator Id : "+unitLineOperator);
        unitLineOperator = unitLineOperator != null ? unitLineOperator : ''

        def vesLineOptr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoVesselVisit.cvCvd.vvdBizu.bzuId")
        if (vesLineOptr==null)
        {
            vesLineOptr = unit.getFieldValue("unitActiveUfv.ufvActualObCv.carrierOperatorId");
        }
        println("Testing getSrvUnitLoad vesLineOptr :"+vesLineOptr);

        vesLineOptr = vesLineOptr != null ? vesLineOptr : (unitLineOperator.equals('MAT') ? 'MAT' : '')
        println ("vesLineOptr Id : "+vesLineOptr);
        vesLineOptr = !vesLineOptr.equals('MAT') ? 'CLI' : 'MAT'
        ueEquipmentState.setEqsFlexString01(vesLineOptr)
        return vesLineOptr;
    }

//Method Sets the Eq Cnt Srv if Blank
    public void setEqCntrSvr(Object event) {

        def unit = event.getEntity()
        def eventId = event.getEvent().getEventTypeId()
        try {
            //Get Equi SRV
            def unitEquipment = unit.getUnitPrimaryUe()
            def ueEquipmentState = unitEquipment.getUeEquipmentState()
            def equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : null
            println("Eq Srv Company : "+equipFlex01);
            if (equipFlex01 != null && !(eventId.equals('UNIT_DISCH_COMPLETE') || eventId.equals('UNIT_IN_GATE') ||
                    eventId.equals('UNIT_ROLL'))) {
                return;
            }

            def unitLineOperator = unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId")
            unitLineOperator = unitLineOperator != null ? unitLineOperator : ''

            def equiSrv = ''; def vesLineOptr = '';
            if (eventId.equals('UNIT_DISCH_COMPLETE')) {
                //Actual Vessle Line Optr
                vesLineOptr = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdBizu.bzuId")
            } else if (eventId.equals('UNIT_IN_GATE') || eventId.equals('UNIT_ROLL')) {
                //Ingate Bkg Line Operator
                vesLineOptr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoVesselVisit.cvCvd.vvdBizu.bzuId")
            }

            //verify and set EqSrv Cntr
            vesLineOptr = vesLineOptr != null ? vesLineOptr : (unitLineOperator.equals('MAT') ? 'MAT' : '')
            vesLineOptr = !vesLineOptr.equals('MAT') ? 'CLI' : 'MAT'
            ueEquipmentState.setEqsFlexString01(vesLineOptr)
            ueEquipmentState.getEqsFlexString01();

            println("eventId ::" + eventId + " Eq SRV :" + equipFlex01 + " vesLineOptr :" + vesLineOptr + "    unitLineOperator::" + unitLineOperator)

        } catch (Exception e) {
            e.printStacktrace()
        }
    }

    //Method Flips Mty Client Cntr EquiOperator from MAT to Cli Operator
    public void flipMtyCliCntrOperator(Object unit) {
        try {
            //Get Equi SRV
            def unitEquipment = unit.getUnitPrimaryUe()
            def ueEquipmentState = unitEquipment.getUeEquipmentState()
            def equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : ""
            if (equipFlex01 == null || equipFlex01.length() == 0 || !equipFlex01.startsWith("CLI")) {
                return;
            }

            def freightKind = unit.unitFreightKind.name;
            def equiOwner = unit.unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId
            def lineOperator = unit.unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId

            println("freightKind ::" + freightKind + " equiOwner:" + equiOwner + "  lineOperator:" + lineOperator)
            if ("MTY".equals(freightKind) && "MAT".equals(lineOperator)) {
                lineOperator = equiOwner != null ? equiOwner.substring(0, equiOwner.length() - 1) : lineOperator
                def bzuid = lineOperator != null ? com.navis.argo.business.reference.LineOperator.findLineOperatorById(lineOperator) : null
                if (bzuid != null)
                    unit.setUnitLineOperator(bzuid)
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}//Class Ends