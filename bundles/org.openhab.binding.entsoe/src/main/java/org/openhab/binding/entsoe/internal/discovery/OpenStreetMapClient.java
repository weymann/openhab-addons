/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.entsoe.internal.discovery;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.entsoe.internal.AreaCode;
import org.openhab.binding.entsoe.internal.exception.EntsoEConfigurationException;
import org.openhab.binding.entsoe.internal.exception.EntsoEResponseException;
import org.openhab.binding.entsoe.internal.exception.EntsoEUnexpectedException;
import org.openhab.core.io.net.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Miika Jukka - Initial contribution
 *
 */
@NonNullByDefault
public class OpenStreetMapClient {

    private final Logger logger = LoggerFactory.getLogger(OpenStreetMapClient.class);

    private final int TIMEOUT = 5000;

    private DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

    public AreaCode doGetRequest(OpenStreetMapRequest request)
            throws EntsoEResponseException, EntsoEUnexpectedException, EntsoEConfigurationException {

        try {
            logger.debug("Sending OpenStreetMapRequest GET request with parameters: {}", request);
            String url = request.toUrl();
            String responseText = HttpUtil.executeUrl("GET", url, TIMEOUT);
            if (responseText == null) {
                logger.error("GET request failed and returned null for request url: {}", url);
                throw new EntsoEResponseException("Request failed");
            }
            logger.debug("{}", responseText);
            return parseXmlResponse(responseText);
        } catch (IOException e) {
            throw new EntsoEResponseException(e);
        } catch (ParserConfigurationException e) {
            throw new EntsoEResponseException("XML parser configuration error " + e);
        } catch (SAXException e) {
            throw new EntsoEResponseException("XML parser SAX error " + e);
        }
    }

    private AreaCode parseXmlResponse(String responseText)
            throws ParserConfigurationException, SAXException, IOException, EntsoEResponseException {

        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(new InputSource(new StringReader(responseText)));
        document.getDocumentElement().normalize();

        // Check for error
        if (document.getDocumentElement().getNodeName().equals("error")) {
            NodeList reasonOfRejection = document.getElementsByTagName("error");
            Node reasonNode = reasonOfRejection.item(0);
            Element reasonElement = (Element) reasonNode;
            String reasonCode = reasonElement.getElementsByTagName("code").item(0).getTextContent();
            String reasonText = reasonElement.getElementsByTagName("message").item(0).getTextContent();
            throw new EntsoEResponseException(
                    String.format("Request failed with API response: Code %s, Text %s", reasonCode, reasonText));
        }
        // Check if first element is valid "reversegeocode"
        if (document.getDocumentElement().getNodeName().equals("reversegeocode")) {
            // Get child nodes "addressparts"
            NodeList listOfReverseGeoCodes = document.getElementsByTagName("addressparts");
            Node addressparts = listOfReverseGeoCodes.item(0);
            NodeList listOfAddressparts = addressparts.getChildNodes();

            Node CountryNode = listOfAddressparts.item(0);
            Node CountryCodeNode = listOfAddressparts.item(1);

            logger.debug("country: {} country_code {}", CountryNode.getTextContent(), CountryCodeNode.getTextContent());

            AreaCode areaCode = AreaCode.valueOfCountryCode(CountryCodeNode.getTextContent().toUpperCase());
            if (areaCode != null) {
                return areaCode;
            }
            throw new EntsoEResponseException(String.format("Unknown areacode"));
        }

        throw new EntsoEResponseException(String.format("Unknown response"));
    }
}
