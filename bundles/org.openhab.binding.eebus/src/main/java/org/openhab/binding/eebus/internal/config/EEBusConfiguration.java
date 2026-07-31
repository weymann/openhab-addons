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
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link EEBusConfiguration} class contains the configuration parameters of the
 * {@code eebus:oh-device} bridge Thing, i.e. of exactly one local SHIP/SPINE service
 * instance (one certificate/SKI, one mDNS-advertised presence on the network). Renamed from
 * {@code eebus:oh-device} by docs/ADR/024-oh-device-oh-entity-rename.md.
 *
 * <p>
 * <strong>No longer holds Use Case selection (docs/ADR/027-derive-local-use-cases-from-entities.md):</strong>
 * {@code eebus:oh-cs-device} is removed, and the {@code supportedUseCasesClient}/
 * {@code supportedUseCasesServer}/failsafe-seed fields that used to live here (shared between
 * {@code eebus:oh-device} and {@code eebus:oh-cs-device}) moved to
 * {@link org.openhab.binding.eebus.internal.config.EEBusOhEntityConfiguration}: the Bridge now
 * derives its local Use-Case set from its attached {@code eebus:oh-entity}/{@code eebus:oh-cs-entity}/
 * {@code eebus:oh-eg-entity} children instead of declaring it itself.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusConfiguration {

    /**
     * Configuration parameter key for {@link #port}, matching the {@code port}
     * config-description parameter in {@code thing-types.xml}. Used by {@code EEBusHandler} to
     * write an auto-assigned port back into the persisted configuration via
     * {@code editConfiguration()}/{@code updateConfiguration(Configuration)}.
     */
    public static final String PARAM_PORT = "port";

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

    /**
     * SKIs (40 lowercase hex characters each) this Bridge trusts as SHIP Nodes - the source of
     * truth {@code EEBusHandler#currentTrustedSkis()} reads to build {@code ShipCommunication}'s
     * trusted-SKI set. Editable only as Thing config now (docs/ADR/027-derive-local-use-cases-
     * from-entities.md Decision 6): the live, no-restart {@code trust(String)}/
     * {@code untrust(String)} Bridge Actions this used to also be mutable through
     * ({@code EEBusDeviceActions}) are removed - a trust change is just another config change,
     * and every config change now uniformly triggers a full rebuild, so the separate live-push
     * path added nothing once that was true. Replaces the per-{@code eebus:oh-entity}
     * {@code paired} Thing property docs/ADR/012-pairing-trust-property-and-actions.md introduced -
     * see docs/ADR/024-oh-device-oh-entity-rename.md for why trust moved to this, Device-scoped,
     * level instead of the Entity-scoped Thing it used to live on.
     */
    public List<String> trustedSkis = List.of();

    /**
     * WebSocket port the local SHIP server listens on, or {@code null} if none was explicitly
     * configured. When {@code null}, {@code EEBusHandler#initialize()} assigns a free port from
     * {@code EEBusPortPool} ({@value org.openhab.binding.eebus.internal.transport.EEBusPortPool#PORT_RANGE_START}-
     * {@value org.openhab.binding.eebus.internal.transport.EEBusPortPool#PORT_RANGE_END}) instead.
     */
    public @Nullable Integer port;

    /**
     * If {@code true} (default), this service actively dials trusted peers as soon as they
     * are discovered via mDNS, in addition to accepting their incoming connections - normal
     * SHIP behavior. If {@code false}, this service never dials out and only accepts
     * incoming connections. Intended as a diagnostic workaround for pairing two self-built
     * {@code eebus:oh-device} instances against each other: with both sides dialing out, they
     * can connect to each other at the same moment, and a bug in the embedded SHIP library's
     * simultaneous-connection ("double connection") handling can then abort the handshake
     * (see {@code TEST_PAIRING.md}, Test 2, "Known Bug Encountered"). Disabling this on one
     * of the two sides avoids that race by making that side purely passive. Not recommended
     * against a real third-party device, which may rely on openHAB dialing out.
     */
    public boolean connectToPeers = true;

    /**
     * If {@code true} (default), {@code EEBusHandler#resolveBindAddress} binds/announces the
     * local SHIP server on IPv4 instead of the IPv6-first preference the single-address fix
     * would otherwise use. Recommended standard setting: this works around a confirmed bug in
     * the embedded (pinned) SHIP library where opening a connection to an already-known IPv6
     * peer builds a malformed {@code wss://} URI (missing the {@code [...]} brackets an IPv6
     * host requires), which fails ("connect to null on port -1") and leads to a SPINE request
     * timing out shortly after - see the mDNS redial-storm fix notes (project memory,
     * 2026-08-21). Confirmed via a natural A/B comparison within a single live log: the same
     * peer pairing failed this way while on IPv6 and connected cleanly, with use-case data
     * flowing, once both sides were on IPv4. IPv4 addresses never need URI brackets, so forcing
     * IPv4 sidesteps the bug entirely, at the cost of losing the single-address fix's benefit
     * on a host with no usable IPv4 address (falls back through the normal preference order in
     * that case - see {@code EEBusHandler#resolveBindAddress}). Set to {@code false} to return
     * to the IPv6-first preference, e.g. to re-test after an upstream fix in the pinned SHIP
     * library. If the embedded library is ever fixed upstream and this is no longer needed,
     * remove this field, the matching {@code preferIpv4} parameter in {@code thing-types.xml},
     * and the {@code cfg.preferIpv4} branch at the top of {@code EEBusHandler#resolveBindAddress}.
     */
    public boolean preferIpv4 = true;

    /**
     * The SPINE {@code deviceType} this Bridge's shared local {@code Device} presents itself as
     * in detailed discovery (docs/ADR/044-configurable-devicetype.md). Default
     * {@code "EnergyManagementSystem"}, matching the value {@code EEBusHandler#startShipSpineLocked()}
     * hardcoded before that ADR. Independent of, and unrelated to, any child Thing's
     * {@code entityType} (docs/ADR/042-configurable-entitytype.md) - {@code deviceType}
     * classifies the {@code Device} as a whole (a different SPINE object), while
     * {@code entityType} classifies the one shared local {@code Entity} this Bridge builds;
     * {@code jeebus.spine}'s own {@code @AllowedEntityTypes} enforcement
     * (see {@code AbstractEEBusLimitEnergyGuardUseCase}) never reads {@code deviceType} at all.
     * Parsed via the generated {@code DeviceTypeEnumType.fromValue(...)}, falling back to
     * {@code EnergyManagementSystem} with a WARN log if the configured string does not match a
     * known constant - same pattern as {@code entityType}.
     */
    public String deviceType = "EnergyManagementSystem";
}
