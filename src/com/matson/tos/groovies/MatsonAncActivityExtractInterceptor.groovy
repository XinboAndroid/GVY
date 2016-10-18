/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

import com.navis.argo.util.XmlUtil
import com.navis.external.edi.entity.AbstractEdiExtractInterceptor
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.Element

/**
 Set UNLOC code for Port of load.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 09/22/2015
 *
 * Date: 09/22/2015: 5:41 PM
 * JIRA: CSDV-
 * SFDC: 00145621
 * Called from: Edi Session extract code extension for activity message
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonAncActivityExtractInterceptor extends AbstractEdiExtractInterceptor {
    @Override
    public Element beforeEdiMap(Map inParams) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MatsonAncActivityExtractInterceptor, Started beforeEdiMap Method.");
        if (inParams == null) {
            LOGGER.error("MatsonAncActivityExtractInterceptor inParams is null.");
            return null;
        }
        Element xmlTransaction = (Element) inParams.get("XML_TRANSACTION");
        Event event = (Event) inParams.get("ENTITY");
        if (xmlTransaction == null) {
            LOGGER.error("MatsonAncActivityExtractInterceptor, xmlTransaction element is null.");
            return null;
        }
        if (event == null) {
            LOGGER.error("MatsonAncActivityExtractInterceptor, event entity is null.");
            return null;
        }

        Unit unit = Unit.hydrate(event.getEventAppliedToGkey());
        if (Unit == null) {
            LOGGER.error(
                    "MatsonAncActivityExtractInterceptor, Unit not found for gkey" + event.getEventAppliedToGkey());
            return null;
        }
        Element tranElement = changePOL(inParams, unit);
        LOGGER.info("MatsonAncActivityExtractInterceptor, completed beforeEdiMap Method.");
        return tranElement;
    }
    private Logger LOGGER = Logger.getLogger(MatsonAncActivityExtractInterceptor.class);

    private Element changePOL(Map inParams, Unit inUnit) {
        Routing routing = inUnit.getUnitRouting();
        Element xmlTransaction = (Element) inParams.get("XML_TRANSACTION");
        if (xmlTransaction == null || routing == null || routing.getRtgPOL() == null) {
            return null;
        }
        String unLocId = routing.getRtgPOL().getPointUnlocId();
        if (unLocId == null) {
            return null;
        }
        Element containerElement = xmlTransaction.getChild("ediContainer", XmlUtil.ARGO_NAMESPACE);
        if (containerElement != null) {
            Element originalLoadPortElement = containerElement.getChild("loadPort", XmlUtil.ARGO_NAMESPACE);
            if (originalLoadPortElement != null) {
                Element portCodesElement = originalLoadPortElement.getChild("portCodes", XmlUtil.ARGO_NAMESPACE);
                if (portCodesElement != null) {
                    portCodesElement.setAttribute("unLocCode", unLocId, XmlUtil.ARGO_NAMESPACE);
                    return xmlTransaction;
                }
            }
        }
    }
}