/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.argo.business.model.GeneralReference
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.util.BizFailure
import com.navis.framework.util.CarinaUtils
import org.apache.commons.lang.StringUtils
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 Open FTP connection, send files and close connection.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 07/17/2015
 *
 * Date: 07/17/2015: 5:41 PM
 * JIRA: CSDV-3024
 * SFDC: 00138337
 * Called from: Gate Configuration
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonAncFtpAdaptor extends AbstractExtensionCallback {
    MatsonAncFtpAdaptor() {
        LOGGER.setLevel(Level.INFO);
    }
    /**
     * Open the Ftp Connection
     *
     * @param inMlbx
     * @throws com.navis.framework.util.BizFailure
     */
    public openConnection(GeneralReference inGeneralReference) throws BizFailure {
        _ftpClient = new FTPClient();
        _generalReference = inGeneralReference;
        String mailBoxAddr = inGeneralReference.getRefValue1();
        String userId = inGeneralReference.getRefValue3();
        String pwd = inGeneralReference.getRefValue4();
        Long timeoutValue = new Long(inGeneralReference.getRefValue5());

        try {
            _ftpClient.setDefaultTimeout(timeoutValue.intValue());

            boolean isAddWithPort = false;
            //communication address

            if (mailBoxAddr.contains(PORT_DELIMITER)) {
                isAddWithPort = true;
                String[] ftpPortAddress = mailBoxAddr.split(PORT_DELIMITER);

                if (ftpPortAddress.length == 2) {
                    String address = ftpPortAddress[0].trim()

                    if (StringUtils.isNotEmpty(address)) {
                        String ftpAddr = address;
                        String port = ftpPortAddress[1].trim();

                        if (StringUtils.isNumeric(port)) {
                            int ftpPort = Integer.parseInt(port);
                            _ftpClient.connect(ftpAddr, ftpPort);
                        }
                    }
                }
            }

            if (!isAddWithPort) {
                _ftpClient.connect(mailBoxAddr);
            }
            LOGGER.info("connect--" + _ftpClient.getReplyCode() + "-" + _ftpClient.getReplyString());
            if (!FTPReply.isPositiveCompletion(_ftpClient.getReplyCode())) {
                _ftpClient.disconnect();
                String param = "Unable to Connect to Host " + mailBoxAddr + " for the following reason :" + _ftpClient.getReplyCode() +
                        _ftpClient.getReplyString();
                LOGGER.error(param);
                throw BizFailure.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, param);
            }
            if (!_ftpClient.login(userId, pwd)) {
                _ftpClient.disconnect();
                String param = "User Name : " + userId + " or Password is Invalid";
                LOGGER.error(param);
                throw BizFailure.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, param);
            } else {
                _ftpClient.enterLocalPassiveMode();
            }

            LOGGER.info("login--" + _ftpClient.getReplyCode() + "-" + _ftpClient.getReplyString());
            LOGGER.info("data connection mode--" + _ftpClient.getDataConnectionMode());
        } catch (Exception inEx) {
            String exception = CarinaUtils.getStackTrace(inEx);
            LOGGER.error("Unable to Open Connection to Host " + mailBoxAddr + " with user name " + userId +
                    " for the following reason " + exception);
            //throw BizFailure.wrap(e);
            if (inEx instanceof BizFailure) {
                throw (BizFailure) inEx;
            }
            //for avoiding Socket time out error (caused by the value for FTP_DEFAULT_TIMEOUT setting).
            if (inEx instanceof SocketTimeoutException) {
                String param = "Request timed out. Retry with an increased value for FTP_DEFAULT_TIMEOUT setting";
                LOGGER.error(param);
                throw BizFailure.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, param);
            } else {
                throw BizFailure.wrap(inEx);
            }
        }
    }

    /**
     * Send the file to mailbox folder
     *
     * @param inInputStream
     * @param inFileName
     * @return success
     * @throws BizFailure
     */
    public boolean sendDocument(String inFileName, InputStream inInputStream) throws BizFailure {
        boolean success = false;
        String mailBoxAddr = _generalReference.getRefValue1();
        String communicationFolder = _generalReference.getRefValue2();
        String userId = _generalReference.getRefValue3();
        Long soTimeoutValue = new Long(_generalReference.getRefValue6());
        try {
            _ftpClient.changeWorkingDirectory(communicationFolder);
            LOGGER.info("cwd--" + _ftpClient.getReplyCode() + "-" + _ftpClient.getReplyString());
            if (_ftpClient.getReplyCode() == 501) {
                String param = "Given directory path " + communicationFolder + " is invalid - use proper path separator";
                LOGGER.error(param);
                throw BizFailure.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, param);
            }
            if (_ftpClient.getReplyCode() == 550) {
                String param = "Failed to change directory: " + communicationFolder;
                LOGGER.error(param);
                throw BizFailure.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, param);
            }

            _ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            LOGGER.info("type--" + _ftpClient.getReplyCode() + "-" + _ftpClient.getReplyString());

            //mode.
            try {
                _ftpClient.setSoTimeout(soTimeoutValue.intValue());

                success = _ftpClient.storeFile(inFileName, inInputStream);

                if (!success) {
                    LOGGER.info("Sending Failed, Resending the file");
                    _ftpClient.enterLocalActiveMode();
                    success = _ftpClient.storeFile(inFileName, inInputStream);
                }
            } catch (Exception ftpEx) {
                LOGGER.info("Exception occured during file sending, so trying to send again in Active mode : " + ftpEx);
                _ftpClient.enterLocalActiveMode();
                success = _ftpClient.storeFile(inFileName, inInputStream);
            }
            LOGGER.info("send(storeFile)--" + _ftpClient.getReplyCode() + "-" + _ftpClient.getReplyString());
        } catch (Exception inEx) {
            String exception = CarinaUtils.getStackTrace(inEx);
            LOGGER.error("Unable to send file " + inFileName + " to mailbox folder " + communicationFolder + " on Host " + communicationFolder +
                    " for the following reason " + exception);
            if (inEx instanceof BizFailure) {
                throw (BizFailure) inEx;
            }
            String param = "Can not send file " + inFileName + " to Host " + communicationFolder + " for the following reason: " + inEx.getMessage();
            LOGGER.error(param);
            throw BizFailure.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, param);
        } finally {
            if (inInputStream != null) {
                try {
                    inInputStream.close();
                } catch (IOException ex) {
                    // nothing we can do
                    LOGGER.error("Unexpected Error: Unable to close the inputstream " + ex);
                }
            }
        }
        return success;
    }

    /**
     * Close the Ftp Connection
     *
     * @throws BizFailure
     */
    public void closeConnection() {
        if (_ftpClient != null) {
            try {
                if (_ftpClient.isConnected()) {
                    _ftpClient.logout();
                    _ftpClient.disconnect();
                    LOGGER.info("logout--" + _ftpClient.getReplyCode() + "-" + _ftpClient.getReplyString());
                }
            } catch (Exception inEx) {
                String exception = CarinaUtils.getStackTrace(inEx);
                LOGGER.error("Unable to Close Connection for Host " + _generalReference.getRefValue1() + " for the following reason " + exception);
            }
        } else {
            LOGGER.debug("No need to close connection since ftpClient is not initialized...");
        }
    }

    private FTPClient _ftpClient;
    private static String PORT_DELIMITER = ":";
    private GeneralReference _generalReference;
    private static Logger LOGGER = Logger.getLogger(MatsonAncFtpAdaptor.class);
}