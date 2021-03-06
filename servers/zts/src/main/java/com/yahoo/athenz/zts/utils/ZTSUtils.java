/**
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.athenz.zts.utils;

import java.util.List;

import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.athenz.auth.PrivateKeyStore;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.common.metrics.Metric;
import com.yahoo.athenz.common.server.cert.CertSigner;
import com.yahoo.athenz.zts.Identity;
import com.yahoo.athenz.zts.ZTSConsts;
import com.yahoo.athenz.zts.cert.X509CertRecord;

public class ZTSUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZTSUtils.class);

    public static final String ZTS_DEFAULT_EXCLUDED_CIPHER_SUITES = "SSL_RSA_WITH_DES_CBC_SHA,"
            + "SSL_DHE_RSA_WITH_DES_CBC_SHA,SSL_DHE_DSS_WITH_DES_CBC_SHA,"
            + "SSL_RSA_EXPORT_WITH_RC4_40_MD5,SSL_RSA_EXPORT_WITH_DES40_CBC_SHA,"
            + "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA,SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA";
    public static final String ZTS_DEFAULT_EXCLUDED_PROTOCOLS = "SSLv2,SSLv3";
    public static final String ZTS_CERT_DNS_SUFFIX =
            System.getProperty(ZTSConsts.ZTS_PROP_CERT_DNS_SUFFIX, ZTSConsts.ZTS_CERT_DNS_SUFFIX);
    
    private static String CA_X509_CERTIFICATE = null;
    
    public static int retrieveConfigSetting(String property, int defaultValue) {
        
        int settingValue;
        try {
            String propValue = System.getProperty(property);
            if (propValue == null) {
                return defaultValue;
            }
            
            settingValue = Integer.parseInt(propValue);
            
            if (settingValue <= 0) {
                LOGGER.error("Invalid " + property + " value: " + propValue +
                        ", defaulting to " + defaultValue + " seconds");
                settingValue = defaultValue;
            }
        } catch (Exception ex) {
            LOGGER.error("Invalid " + property + " value, defaulting to " +
                    defaultValue + " seconds: " + ex.getMessage());
            settingValue = defaultValue;
        }
        
        return settingValue;
    }
    
    public static SslContextFactory createSSLContextObject(String[] clientProtocols) {
        return createSSLContextObject(clientProtocols, null);
    }
    
    public static SslContextFactory createSSLContextObject(String[] clientProtocols, PrivateKeyStore privateKeyStore) {
        
        String keyStorePath = System.getProperty(ZTSConsts.ZTS_PROP_KEYSTORE_PATH);
        String keyStorePasswordAppName = System.getProperty(ZTSConsts.ZTS_PROP_KEYSTORE_PASSWORD_APPNAME);
        String keyStorePassword = System.getProperty(ZTSConsts.ZTS_PROP_KEYSTORE_PASSWORD);
        String keyStoreType = System.getProperty(ZTSConsts.ZTS_PROP_KEYSTORE_TYPE, "PKCS12");
        String keyManagerPassword = System.getProperty(ZTSConsts.ZTS_PROP_KEYMANAGER_PASSWORD);
        String keyManagerPasswordAppName = System.getProperty(ZTSConsts.ZTS_PROP_KEYMANAGER_PASSWORD_APPNAME);

        String trustStorePath = System.getProperty(ZTSConsts.ZTS_PROP_TRUSTSTORE_PATH);
        String trustStorePassword = System.getProperty(ZTSConsts.ZTS_PROP_TRUSTSTORE_PASSWORD);
        String trustStorePasswordAppName = System.getProperty(ZTSConsts.ZTS_PROP_TRUSTSTORE_PASSWORD_APPNAME);

        String trustStoreType = System.getProperty(ZTSConsts.ZTS_PROP_TRUSTSTORE_TYPE, "PKCS12");
        String excludedCipherSuites = System.getProperty(ZTSConsts.ZTS_PROP_EXCLUDED_CIPHER_SUITES,
                ZTS_DEFAULT_EXCLUDED_CIPHER_SUITES);
        String excludedProtocols = System.getProperty(ZTSConsts.ZTS_PROP_EXCLUDED_PROTOCOLS,
                ZTS_DEFAULT_EXCLUDED_PROTOCOLS);
        Boolean wantClientAuth = Boolean.parseBoolean(System.getProperty(ZTSConsts.ZTS_PROP_WANT_CLIENT_CERT, "false"));
        
        SslContextFactory sslContextFactory = new SslContextFactory();
        if (keyStorePath != null) {
            LOGGER.info("createSSLContextObject: using SSL KeyStore path: " + keyStorePath);
            sslContextFactory.setKeyStorePath(keyStorePath);
        }
        if (keyStorePassword != null) {
            if (null != privateKeyStore) {
                keyStorePassword = privateKeyStore.getApplicationSecret(keyStorePasswordAppName, keyStorePassword);
            }
            sslContextFactory.setKeyStorePassword(keyStorePassword);
        }
        sslContextFactory.setKeyStoreType(keyStoreType);

        if (keyManagerPassword != null) {
            if (null != privateKeyStore) {
                keyManagerPassword = privateKeyStore.getApplicationSecret(keyManagerPasswordAppName, keyManagerPassword);
            }
            sslContextFactory.setKeyManagerPassword(keyManagerPassword);
        }
        if (trustStorePath != null) {
            LOGGER.info("createSSLContextObject: using SSL TrustStore path: " + trustStorePath);
            sslContextFactory.setTrustStorePath(trustStorePath);
        }
        if (trustStorePassword != null) {
            if (null != privateKeyStore) {
                trustStorePassword = privateKeyStore.getApplicationSecret(trustStorePasswordAppName, trustStorePassword);
            }
            sslContextFactory.setTrustStorePassword(trustStorePassword);
        }
        sslContextFactory.setTrustStoreType(trustStoreType);

        if (excludedCipherSuites.length() != 0) {
            sslContextFactory.setExcludeCipherSuites(excludedCipherSuites.split(","));
        }
        
        if (excludedProtocols.length() != 0) {
            sslContextFactory.setExcludeProtocols(excludedProtocols.split(","));
        }

        sslContextFactory.setWantClientAuth(wantClientAuth);
        if (clientProtocols != null) {
            sslContextFactory.setIncludeProtocols(clientProtocols);
        }

        return sslContextFactory;
    }
    
    public static final boolean emitMonmetricError(int errorCode, String caller,
            String domainName, Metric metric) {

        if (errorCode < 1) {
            return false;
        }
        if (caller == null || caller.isEmpty()) {
            return false;
        }

        // Set 3 error metrics:
        // (1) cumulative "ERROR" (of all zts request and error types)
        // (2) cumulative granular zts request and error type (eg- "getdomainlist_error_400")
        // (3) cumulative error type (of all zts requests) (eg- "error_404")
        String errCode = Integer.toString(errorCode);
        metric.increment("ERROR");
        if (domainName != null) {
            metric.increment(caller.toLowerCase() + "_error_" + errCode, domainName);
        } else {
            metric.increment(caller.toLowerCase() + "_error_" + errCode);
        }
        metric.increment("error_" + errCode);

        return true;
    }
    
    public static boolean verifyCertificateRequest(PKCS10CertificationRequest certReq,
            final String domain, final String service, X509CertRecord certRecord) {
        
        // verify that it contains the right common name
        // and the certificate matches to what we have
        // registered in ZMS

        final String cn = domain + "." + service;
        if (!validateCertReqCommonName(certReq, cn)) {
            LOGGER.error("validateCertificateRequest: unable to validate PKCS10 cert request common name");
            return false;
        }

        // verify we don't have invalid dnsnames in the csr
        
        if (!validateCertReqDNSNames(certReq, domain, service)) {
            LOGGER.error("validateCertificateRequest: unable to validate PKCS10 cert request DNS Name");
            return false;
        }
        
        // if we have an instance id then we have to make sure the
        // athenz instance id fields are identical
        
        if (certRecord != null) {
            
            // validate the service name matches first
            
            if (!cn.equals(certRecord.getService())) {
                LOGGER.error("verifyCertificateRequest: unable to validate cn: {} vs. cert record data: {}",
                        cn, certRecord.getService());
                return false;
            }
            
            // then validate instance ids
            
            if (!validateCertReqInstanceId(certReq, certRecord.getInstanceId())) {
                LOGGER.error("verifyCertificateRequest: unable to validate PKCS10 cert request instance id");
                return false;
            }
        }
        
        return true;
    }
    
    public static boolean validateCertReqCommonName(PKCS10CertificationRequest certReq, String cn) {
        
        String cnCertReq = null;
        try {
            cnCertReq = Crypto.extractX509CSRCommonName(certReq);
        } catch (Exception ex) {
            
            // we want to catch all the exceptions here as we want to
            // handle all the errors and not let container to return
            // standard server error
            
            LOGGER.error("validateCertReqCommonName: unable to extract csr cn: "
                    + ex.getMessage());
        }
        
        if (cnCertReq == null) {
            LOGGER.error("validateCertReqCommonName - unable to extract csr cn: "
                    + certReq.toString());
            return false;
        }

        if (!cnCertReq.equalsIgnoreCase(cn)) {
            LOGGER.error("validateCertReqCommonName - cn mismatch: "
                    + cnCertReq + " vs. " + cn);
            return false;
        }

        return true;
    }
    
    static boolean validateCertReqDNSNames(PKCS10CertificationRequest certReq, final String domain,
            final String service) {
        
        // if no dns names in the CSR then we're ok
        
        List<String> dnsNames = Crypto.extractX509CSRDnsNames(certReq);
        if (dnsNames.isEmpty()) {
            return true;
        }
        
        // the only two formats we're allowed to have in the CSR are:
        // 1) <service>.<domain-with-dashes>.<provider-dns-suffix>
        // 2) <service>.<domain-with-dashes>.instanceid.athenz.<provider-dns-suffix>
        
        final String prefix = service + "." + domain.replace('.', '-') + ".";
        for (String dnsName : dnsNames) {
            if (dnsName.startsWith(prefix) && dnsName.endsWith(ZTS_CERT_DNS_SUFFIX)) {
                continue;
            }
            if (dnsName.indexOf(ZTSConsts.ZTS_CERT_INSTANCE_ID) != -1) {
                continue;
            }
            LOGGER.error("validateServiceCertReqDNSNames - Invalid dnsName SAN entry: {}", dnsName);
            return false;
        }

        return true;
    }
    
    public static String extractCertReqInstanceId(PKCS10CertificationRequest certReq) {
        List<String> dnsNames = Crypto.extractX509CSRDnsNames(certReq);
        String reqInstanceId = null;
        for (String dnsName : dnsNames) {
            int idx = dnsName.indexOf(ZTSConsts.ZTS_CERT_INSTANCE_ID);
            if (idx != -1) {
                reqInstanceId = dnsName.substring(0, idx);
                break;
            }
        }
        return reqInstanceId;
    }
    
    public static boolean validateCertReqInstanceId(PKCS10CertificationRequest certReq, String instanceId) {
        final String reqInstanceId = extractCertReqInstanceId(certReq);
        if (reqInstanceId == null) {
            return false;
        }
        return reqInstanceId.equals(instanceId);
    }
    
    public static Identity generateIdentity(CertSigner certSigner, String csr, String cn,
            String certUsage, int expiryTime) {
        
        // generate a certificate for this certificate request

        String pemCert = certSigner.generateX509Certificate(csr, certUsage, expiryTime);
        if (pemCert == null || pemCert.isEmpty()) {
            LOGGER.error("generateIdentity: CertSigner was unable to generate X509 certificate");
            return null;
        }
        
        if (CA_X509_CERTIFICATE == null) {
            synchronized (ZTSUtils.class) {
                if (CA_X509_CERTIFICATE == null) {
                    CA_X509_CERTIFICATE = certSigner.getCACertificate();
                }
            }
        }
        
        return new Identity().setName(cn).setCertificate(pemCert).setCaCertBundle(CA_X509_CERTIFICATE);
    }


}
