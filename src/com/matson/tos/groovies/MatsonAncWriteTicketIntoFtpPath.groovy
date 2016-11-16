/*
 * Copyright (c) 2016 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.model.GeneralReference
import com.navis.extension.invocation.dynamiccode.IExtension
import com.navis.extension.invocation.dynamiccode.IExtensionClassProvider
import com.navis.extension.portal.IExtensionBizFacade
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.context.PortalApplicationContext
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/**
 * Open FTP connection, move the tickets to FTP path and close connection.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 02/09/2016
 *
 * Date: 02/09/2016: 5:41 PM
 * JIRA: CSDV-3512
 * SFDC: 00150390
 * Called from: Groovy Job
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 *
 *
 */
public class MatsonAncWriteTicketIntoFtpPath extends GroovyApi {

    /**
     * Print document based on the configuration docTypeId parameter
     *
     * @param inOutDao
     */
    public void execute(Map parameters) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncWriteTicketIntoFtpPath execute Stared.");
        moveFilesToFTPPath();
        LOGGER.info(" MatsonAncWriteTicketIntoFtpPath execute Completed.");
    }

    @Override
    void registerOverridableError(String inFailureMessage) {
        super.registerOverridableError(inFailureMessage)
    }

    private void moveFilesToFTPPath() {
        List<GeneralReference> localPathGeneralReferenceList = GeneralReference.findAllEntriesById("MATSON", "DOCUMENTS", "LOCAL_PATH");
        def matsonAncFtpAdaptor = getLibrary("MatsonAncFtpAdaptor");
        for (GeneralReference reference : localPathGeneralReferenceList) {
            try {
                String id3 = reference.getRefId3();
                if (id3 == null) {
                    LOGGER.error(" MatsonAncWriteTicketIntoFtpPath, Ref Id3 value is null for General Reference:" + reference.toString());
                    continue;
                }
                String filePath = reference.getRefValue1();
                if (filePath == null) {
                    LOGGER.error(" MatsonAncWriteTicketIntoFtpPath, Ref Value1 is null for General Reference:" + reference.toString());
                    continue;
                }
                GeneralReference ftpGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "FTP", "INFO", id3);
                if (ftpGeneralReference == null) {
                    LOGGER.error("Couldn't find the FTP General Reference for Type:MATSON, ID-1:FTP,ID-2:INFO and ID-3:" + id3);
                    throw new Exception("Couldn't find the FTP General Reference for Type:MATSON, ID-1:FTP,ID-2:INFO and ID-3:" + id3);
                }
                matsonAncFtpAdaptor.openConnection(ftpGeneralReference);
                File parentFile = new File(filePath);
                File[] files = parentFile.listFiles()
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            matsonAncFtpAdaptor.sendDocument(file);
                        }
                    }
                }
            } finally {
                matsonAncFtpAdaptor.closeConnection();
            }
        }
    }

    private Object getLibrary(String inName) throws BizViolation {
        IExtension inPlugin = findLibrary(inName);
        // get from class provider
        return getProvider().getExtensionClassInstance(inPlugin);
    }

    @Nullable
    protected static IExtension findLibrary(String inLibraryName) throws BizViolation {
        UserContext uc = ContextHelper.getThreadUserContext();
        IExtensionBizFacade extFacade = (IExtensionBizFacade) PortalApplicationContext.getBean(IExtensionBizFacade.BEAN_ID);
        MessageCollector mc = ContextHelper.getThreadMessageCollector();
        IExtension extension = extFacade.findLibrary(uc, mc, inLibraryName);
        if (mc.hasError()) {
            throw (BizViolation) mc.getMessages(MessageLevel.SEVERE).get(0);
        }
        return extension;
    }

    private IExtensionClassProvider getProvider() {
        if (_provider == null) {
            _provider = (IExtensionClassProvider) PortalApplicationContext.getBean(IExtensionClassProvider.BEAN_ID);
        }
        return _provider;
    }

    private IExtensionClassProvider _provider;
    private Logger LOGGER = Logger.getLogger(MatsonAncWriteTicketIntoFtpPath.class);
}