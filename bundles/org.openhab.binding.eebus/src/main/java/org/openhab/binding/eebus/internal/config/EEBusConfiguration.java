/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.eebus.internal.config;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link EEBusConfiguration} class contains the configuration parameters of the
 * {@code eebus:service} bridge Thing, i.e. of exactly one local SHIP/SPINE service
 * instance (one certificate/SKI, one mDNS-advertised presence on the network).
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusConfiguration {

    /** 5-character vendor code assigned by the EEBUS Initiative, e.g. {@code "12345"}. */
    public String vendorCode = "";

    /** Human-readable device brand / manufacturer name. */
    public String deviceBrand = "";

    /** Human-readable device model name. */
    public String deviceModel = "";

    /** Device serial number, used together with brand/model to build the SHIP-ID. */
    public String serialNumber = "";

    /**
     * mDNS service instance label ("Anzeigename für Discovery"), e.g.
     * {@code "openHAB EnergyManager"}. This is what other EEBUS devices show as the
     * name of this Bridge when they discover it — see CONCEPT.md §5.3.
     */
    public String mdnsServiceInstance = "";

    /** WebSocket port the local SHIP server listens on. */
    public int port = 4711;

    /**
     * If {@code true}, the SHIP server accepts the first incoming connection of any
     * device within a short time window after starting, without requiring the SKI to
     * be pre-trusted. See CONCEPT.md §6 decision 4 — kept simple for v1
     * (accept/reject/pre-trust only, no PIN pairing).
     */
    public boolean autoAcceptEnabled = false;

    /**
     * Use case abbreviations (see CONCEPT.md §5.4.1, e.g. {@code "LPC"}, {@code "MPC"})
     * that openHAB should try to detect/consume on paired peers (client/consumer role,
     * CONCEPT.md §5.4). Not yet wired up - see CONCEPT.md §7 items 3+8.
     */
    public List<String> supportedUseCasesClient = List.of();

    /**
     * Use case abbreviations that openHAB should offer to the network itself (server/
     * provider role, CONCEPT.md §5.5), fed via {@code eebus} Item/Rule metadata
     * (CONCEPT.md §4.2). Not yet wired up - see CONCEPT.md §7 items 6-8.
     */
    public List<String> supportedUseCasesServer = List.of();
}
