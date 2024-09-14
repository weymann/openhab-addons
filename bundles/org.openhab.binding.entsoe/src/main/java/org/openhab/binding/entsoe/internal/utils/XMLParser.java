/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.entsoe.internal.utils;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.entsoe.internal.client.Request;
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
public class XMLParser {

    private static final Logger logger = LoggerFactory.getLogger(XMLParser.class);
    private static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    private static final String PERIOD_TAG = "Period";
    private static final String DURATION_TAG = "resolution";
    private static final String POINT_TAG = "Point";
    private static final String DESIRED_DURATION = "PT60M";

    public TreeMap<Instant, Double> doGetRequest(Request request, int timeout)
            throws EntsoEResponseException, EntsoEUnexpectedException, EntsoEConfigurationException {
        try {
            logger.debug("Sending GET request with parameters: {}", request);
            String url = request.toUrl();
            String responseText = HttpUtil.executeUrl("GET", url, timeout);
            if (responseText == null) {
                logger.error("GET request failed and returned null for request url: {}", url);
                throw new EntsoEResponseException("Request failed");
            } else {
                return parseXmlResponse(responseText);
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Authentication challenge without WWW-Authenticate header")) {
                throw new EntsoEConfigurationException("Authentication failed. Please check your security token");
            }
            throw new EntsoEResponseException(e);
        } catch (ParserConfigurationException | SAXException e) {
            throw new EntsoEUnexpectedException(e);
        }
    }

    public static TreeMap<Instant, Double> parseXmlResponse(String responseText) throws ParserConfigurationException,
            SAXException, IOException, EntsoEResponseException, EntsoEUnexpectedException {
        logger.debug("{}", responseText);
        Document d = getDocument(responseText);
        Map<String, TreeMap<Instant, Double>> periods = getPeriods(d);
        System.out.println(periods);
        TreeMap<Instant, Double> response = periods.get(DESIRED_DURATION);
        if (response == null) {
            response = new TreeMap<>();
        }
        return response;
    }

    private static Document getDocument(String xmlString)
            throws EntsoEResponseException, IOException, ParserConfigurationException, SAXException {
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(new InputSource(new StringReader(xmlString)));
        document.getDocumentElement().normalize();
        // Check for rejection
        if (document.getDocumentElement().getNodeName().equals("Acknowledgement_MarketDocument")) {
            NodeList reasonOfRejection = document.getElementsByTagName("Reason");
            Node reasonNode = reasonOfRejection.item(0);
            Element reasonElement = (Element) reasonNode;
            String reasonCode = reasonElement.getElementsByTagName("code").item(0).getTextContent();
            String reasonText = reasonElement.getElementsByTagName("text").item(0).getTextContent();
            throw new EntsoEResponseException(
                    String.format("Request failed with API response: Code %s, Text %s", reasonCode, reasonText));
        }
        return document;
    }

    private static Map<String, TreeMap<Instant, Double>> getPeriods(Document document) {
        Map<String, TreeMap<Instant, Double>> periods = new HashMap<>();
        NodeList listOfPeriods = document.getElementsByTagName(PERIOD_TAG);
        for (int i = 0; i < listOfPeriods.getLength(); i++) {
            // loop over periods
            // calculate start time and duration
            Node timeSeriesNode = listOfPeriods.item(i);
            if (timeSeriesNode.getNodeType() == Node.ELEMENT_NODE) {
                TreeMap<Instant, Double> prices = new TreeMap<>();
                Element timeSeriesElement = (Element) timeSeriesNode;
                Instant start = getStartTimestamp(timeSeriesElement);
                String duration = "unknown";
                NodeList periodNodes = timeSeriesElement.getChildNodes();
                for (int j = 0; j < periodNodes.getLength(); j++) {
                    Node periodNode = periodNodes.item(j);
                    if (periodNode.getNodeName().equals(DURATION_TAG)) {
                        duration = periodNode.getTextContent();
                    }
                }
                Duration priceDuration = Duration.parse(duration);
                // calculate prices
                NodeList listOfPoints = timeSeriesElement.getElementsByTagName("Point");
                System.out.println("Number of points: " + listOfPoints.getLength());
                for (int p = 0; p < listOfPoints.getLength(); p++) {
                    Node pointNode = listOfPoints.item(p);
                    if (pointNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element pointElement = (Element) pointNode;
                        String positionString = pointElement.getElementsByTagName("position").item(0).getTextContent();
                        int position = Integer.parseInt(positionString) - 1;
                        Instant priceStartTime = start.plus(position * priceDuration.toMinutes(), ChronoUnit.MINUTES);
                        String price = pointElement.getElementsByTagName("price.amount").item(0).getTextContent();
                        Double priceAsDouble = Double.parseDouble(price);
                        prices.put(priceStartTime, priceAsDouble);
                    }
                }
                periods.put(duration, prices);
            }
        }
        return periods;
    }

    private static Instant getStartTimestamp(Element elem) {
        NodeList listOfTimeInterval = elem.getElementsByTagName("timeInterval");
        Node startTimeNode = listOfTimeInterval.item(0);
        Element startTimeElement = (Element) startTimeNode;
        String startTime = startTimeElement.getElementsByTagName("start").item(0).getTextContent();
        ZonedDateTime zdt = ZonedDateTime.parse(startTime);
        System.out.println("Start Time zdt " + zdt);
        System.out.println("Start Time ins " + zdt.toInstant());
        return zdt.toInstant();
    }
}
