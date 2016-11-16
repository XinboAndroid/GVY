/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.webservice.ArgoWebServicesFacade
import com.navis.argo.webservice.IArgoWebService
import com.navis.external.argo.AbstractGroovyWSCodeExtension
import com.navis.framework.portal.UserContext
import com.navis.framework.util.scope.ScopeCoordinates
import org.apache.commons.lang.StringEscapeUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

class MatsonAncGateWebServiceCall extends AbstractGroovyWSCodeExtension {

    public String execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MatsonAncGateWebServiceCall,execute method Stared.");
        if (inParams == null) {
            LOGGER.error("Null Parameters received!");
            return "Null Parameters received!";
        }
        if (!inParams.containsKey("xml-request")) {
            LOGGER.error("No key found with the name: xml-request");
            return "No key found with the name: xml-request";
        }
        String request = inParams.get("xml-request");
        ScopeCoordinates scope = ContextHelper.getThreadUserContext().getScopeCoordinate();
        UserContext uc = ContextHelper.getThreadUserContext();
        IArgoWebService ws = new ArgoWebServicesFacade(uc);
        LOGGER.info("MatsonAncGateWebServiceCall, request string:" + request);
        LOGGER.info("MatsonAncGateWebServiceCall, User Id:" + ContextHelper.getThreadUserId());
        LOGGER.info("MatsonAncGateWebServiceCall, Scope:" + scope.getBusinessCoords());
        String returnVal = ws.basicInvoke(scope.getBusinessCoords(), StringEscapeUtils.unescapeXml(request));
        LOGGER.info("MatsonAncGateWebServiceCall, retured Value:" + returnVal);
        LOGGER.info("MatsonAncGateWebServiceCall,execute method Completed.");
        return returnVal;
    }
    private static Logger LOGGER = Logger.getLogger(MatsonAncGateWebServiceCall.class);
}