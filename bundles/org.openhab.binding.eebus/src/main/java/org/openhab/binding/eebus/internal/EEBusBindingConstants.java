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
package org.openhab.binding.eebus.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link EEBusBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusBindingConstants {

    private static final String BINDING_ID = "eebus";

    // List of all Thing Type UIDs
    /**
     * Bridge Thing: one local SHIP/SPINE service instance ("SHIP Node" in SHIP terminology).
     * Renamed from {@code service} by docs/ADR/024-oh-device-oh-entity-rename.md to match SHIP's
     * own vocabulary and to distinguish it from openHAB-simulated concepts.
     */
    public static final ThingTypeUID THING_TYPE_OH_DEVICE = new ThingTypeUID(BINDING_ID, "oh-device");
    // THING_TYPE_OH_CS_DEVICE ("oh-cs-device", docs/ADR/023-cs-service-convenience-bridge.md) is
    // removed by docs/ADR/027-derive-local-use-cases-from-entities.md: a Bridge's local Use-Case
    // set is now derived from its attached Entity children (see EEBusHandler#deriveLocalUseCases)
    // instead of being fixed by a dedicated Bridge Thing type - see that ADR for the full
    // rationale, and docs/ADR/023's own file for its supersession note.
    /**
     * A "real" EEBus device seen on the network, identified by its SKI. Bridgeless (top-level) -
     * carries no channels and does not establish trust by itself. Formerly a child of a now-removed
     * {@code eebus:network} anchor Bridge, merged away since it added nothing beyond being a
     * mandatory, always-ONLINE parent - see CONCEPT.md §4.5/§7(15) and
     * docs/ADR/020-merge-network-peer-things.md. Renamed from {@code eebus-peer} by
     * docs/ADR/024-oh-device-oh-entity-rename.md.
     */
    public static final ThingTypeUID THING_TYPE_HW_DEVICE = new ThingTypeUID(BINDING_ID, "hw-device");
    /**
     * One SPINE Entity on a device trusted by the parent {@link #THING_TYPE_OH_DEVICE} Bridge.
     * Also carries this Bridge's own free choice of local Use Cases
     * ({@code supportedUseCasesClient}/{@code supportedUseCasesServer}, moved here from the
     * Bridge by docs/ADR/027-derive-local-use-cases-from-entities.md - the Bridge derives its
     * local Use-Case set from its attached children instead of declaring it itself). Trust
     * itself is granted/revoked on the parent Bridge's own {@code trustedSkis} config, not by
     * configuring or creating this Thing - see docs/ADR/024-oh-device-oh-entity-rename.md, which
     * supersedes docs/ADR/012-pairing-trust-property-and-actions.md's Entity-Thing-level trust
     * model. Renamed and re-scoped from {@code oh-peer} (which used to represent a whole paired
     * device, not just one Entity on it) by the same ADR.
     */
    public static final ThingTypeUID THING_TYPE_OH_ENTITY = new ThingTypeUID(BINDING_ID, "oh-entity");
    /**
     * One SPINE Entity on a device trusted by the parent {@link #THING_TYPE_OH_DEVICE} Bridge -
     * unconditionally contributes LPC+LPP Server to the Bridge's derived local Use-Case set
     * (docs/ADR/027-derive-local-use-cases-from-entities.md), seeded from its own failsafe
     * config fields rather than a checkbox. No longer exclusive under a dedicated Bridge type
     * (the former {@code eebus:oh-cs-device} is removed) - additive alongside
     * {@link #THING_TYPE_OH_ENTITY} under the same {@link #THING_TYPE_OH_DEVICE}, the same
     * relationship {@link #THING_TYPE_OH_EG_ENTITY} already had. Its {@code lpc}/{@code lpp}
     * Channel Groups (limit-active/limit-value/failsafe-limit-value/failsafe-duration-minimum)
     * are declared statically via {@code <channel-groups>} in thing-types.xml instead of being
     * created dynamically on first EEBus event, so they are visible in the Main UI immediately
     * after this Thing is created - appropriate here because this Thing's mere presence
     * guarantees LPC+LPP Server is active, unlike the generic, use-case-agnostic
     * {@link #THING_TYPE_OH_ENTITY}. Served by the very same {@code EEBusOhEntityHandler}/
     * {@code EEBusOhEntityConfiguration} classes as {@link #THING_TYPE_OH_ENTITY} (same
     * {@code ski}/{@code entityAddress}/{@code shipId} config, same trust-status derivation) -
     * {@code ensureChannel} simply finds these Channels already present and is a no-op for them,
     * no handler-side special-casing needed.
     */
    public static final ThingTypeUID THING_TYPE_OH_CS_ENTITY = new ThingTypeUID(BINDING_ID, "oh-cs-entity");
    /**
     * One SPINE Entity on a device trusted by the parent {@link #THING_TYPE_OH_DEVICE} Bridge -
     * an alternative to {@link #THING_TYPE_OH_ENTITY} intended for the EnergyGuard (LPC/LPP
     * Client role) case: its {@code lpc}/{@code lpp} Channel Groups (limit-active/limit-value/
     * failsafe-limit-value/failsafe-duration-minimum) are declared statically via
     * {@code <channel-groups>} in thing-types.xml instead of being created dynamically on first
     * EEBus event, so they are visible in the Main UI immediately after this Thing is created.
     * Like {@link #THING_TYPE_OH_CS_ENTITY}, no dedicated convenience Bridge here
     * (docs/ADR/026-oh-eg-entity-static-channels.md - explicit user decision, AskUserQuestion):
     * additive under the generic {@link #THING_TYPE_OH_DEVICE} instead. Its mere presence
     * unconditionally contributes LPC+LPP Client to the Bridge's derived local Use-Case set
     * (docs/ADR/027-derive-local-use-cases-from-entities.md) - no config needed to activate it,
     * unlike the checkbox-driven {@link #THING_TYPE_OH_ENTITY}. Not exclusive:
     * {@link #THING_TYPE_OH_ENTITY} remains equally valid under {@link #THING_TYPE_OH_DEVICE}
     * for any other/mixed use-case combination. Served by the very same
     * {@code EEBusOhEntityHandler}/{@code EEBusOhEntityConfiguration} classes as
     * {@link #THING_TYPE_OH_ENTITY}/{@link #THING_TYPE_OH_CS_ENTITY}.
     */
    public static final ThingTypeUID THING_TYPE_OH_EG_ENTITY = new ThingTypeUID(BINDING_ID, "oh-eg-entity");

    /**
     * One SPINE Entity on a device trusted by the parent {@link #THING_TYPE_OH_DEVICE} Bridge -
     * an alternative to {@link #THING_TYPE_OH_ENTITY} dedicated to the MPC Client role ("Monitoring
     * Appliance", CONCEPT.md §5.4.1), the receiving side that reads a paired peer's total power
     * measurement (docs/ADR/036-oh-mpc-entity-static-channels.md). Its {@code mpc} Channel Group
     * is declared statically instead of created dynamically on first measurement resolution, and
     * its mere presence unconditionally contributes MPC Client to the Bridge's derived local
     * Use-Case set (docs/ADR/027-derive-local-use-cases-from-entities.md) - no config needed,
     * unlike the checkbox-driven {@link #THING_TYPE_OH_ENTITY}. Not exclusive:
     * {@link #THING_TYPE_OH_ENTITY}/{@link #THING_TYPE_OH_CS_ENTITY}/{@link #THING_TYPE_OH_EG_ENTITY}
     * remain equally valid under {@link #THING_TYPE_OH_DEVICE}. Served by the very same
     * {@code EEBusOhEntityHandler}/{@code EEBusOhEntityConfiguration} classes as
     * {@link #THING_TYPE_OH_ENTITY}.
     */
    public static final ThingTypeUID THING_TYPE_OH_MPC_ENTITY = new ThingTypeUID(BINDING_ID, "oh-mpc-entity");

    /**
     * Convenience Thing for a complete HEMS (docs/ADR/053-hems-convenience-entity.md): under
     * {@link #THING_TYPE_OH_DEVICE} it makes the Bridge build several local SPINE Entities - a
     * Monitoring Entity (MPC/MGCP Client), a Controllable System Entity (LPC/LPP Server, limit
     * received from the CLS gateway) and one Energy Guard Entity (LPC/LPP Client) each for a
     * wallbox and a heat pump, so both can receive their own limit. At most one per Bridge.
     * Served by the same {@code EEBusOhEntityHandler}/{@code EEBusOhEntityConfiguration} classes as
     * the other Entity Thing types.
     */
    public static final ThingTypeUID THING_TYPE_OH_HEMS_ENTITY = new ThingTypeUID(BINDING_ID, "oh-hems-entity");

    /**
     * Channel Group prefix of the HEMS Energy Guard that feeds the wallbox
     * (Channel Groups {@code wallbox-lpc}/{@code wallbox-lpp}), docs/ADR/053.
     */
    public static final String HEMS_PREFIX_WALLBOX = "wallbox";

    /** Channel Group prefix of the HEMS Energy Guard that feeds the heat pump, docs/ADR/053. */
    public static final String HEMS_PREFIX_HEAT_PUMP = "heatpump";

    /** {@link ChannelTypeUID} of the read-only text Channels of the HEMS EV groups, docs/ADR/054. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_EV_TEXT = new ChannelTypeUID(BINDING_ID, "ev-text");

    /** {@link ChannelTypeUID} of the read-only switch Channels of the HEMS EV groups, docs/ADR/054. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_EV_SWITCH = new ChannelTypeUID(BINDING_ID, "ev-switch");

    /** {@link ChannelTypeUID} of the EV charging power Channel, docs/ADR/054. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_EV_POWER = new ChannelTypeUID(BINDING_ID, "ev-power");

    /** {@link ChannelTypeUID} of the EV charging current Channels, docs/ADR/054. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_EV_CURRENT = new ChannelTypeUID(BINDING_ID, "ev-current");

    /** {@link ChannelTypeUID} of the EV charged energy Channel, docs/ADR/054. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_EV_ENERGY = new ChannelTypeUID(BINDING_ID, "ev-energy");

    /**
     * mDNS service type for SHIP 7.3.2 device announcements. Shared between
     * {@code EEBusMdnsBrowser} (runtime session bookkeeping, scoped to an active
     * {@code eebus:oh-device}) and {@code EEBusMdnsDiscoveryParticipant} (Inbox population,
     * binding-scoped) - see ADR-003.
     */
    public static final String SERVICE_TYPE_SHIP_MDNS = "_ship._tcp.local.";

    /**
     * Thing property key an {@code eebus:oh-device} Bridge publishes its own SKI under, once its
     * SHIP server is up ({@code EEBusHandler#startShipSpine}). Also read by
     * {@code EEBusMdnsDiscoveryParticipant} to recognize and exclude a Bridge's own mDNS
     * self-announcement from the Inbox - see ADR-008.
     */
    public static final String PROPERTY_LOCAL_SKI = "localSki";

    /**
     * Channel Group ID for MPC (Monitoring of Power Consumption) Client-role Channels, created
     * dynamically on a trusted {@code eebus:oh-entity} Thing once MPC is detected for that Entity -
     * see {@code EEBusOhEntityHandler#applyMpcPower} and docs/ADR/014-dynamic-client-role-
     * channels.md.
     */
    public static final String CHANNEL_GROUP_MPC = "mpc";

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: total active power, MPC Scenario 1. */
    public static final String CHANNEL_MPC_POWER = "power";

    /** {@link ChannelTypeUID} matching the {@code mpc-power} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_POWER = new ChannelTypeUID(BINDING_ID, "mpc-power");

    /**
     * Fifteen additional MPC Client-role Channel IDs/{@link ChannelTypeUID}s
     * (docs/ADR/037-mpc-additional-datapoints.md), alongside {@link #CHANNEL_MPC_POWER}. Twelve
     * are resolved by {@code EEBusMpcClientUseCase} from a peer's {@code ScopeType}, exactly like
     * {@link #CHANNEL_MPC_POWER} already is; the three {@code _A_B}/{@code _B_C}/{@code _C_A}
     * phase-to-phase voltage ones are permanent stubs - {@code jeebus.spine}'s
     * {@code ScopeTypeEnumType} has no distinct value for them, so no resolution is ever
     * attempted; they exist only statically, on {@code eebus:oh-mpc-entity}.
     */
    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase A active power, MPC Scenario 1 (AC_POWER_A). */
    public static final String CHANNEL_MPC_POWER_PHASE_A = "power-phase-a";

    /** {@link ChannelTypeUID} matching the {@code mpc-power-phase-a} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_POWER_PHASE_A = new ChannelTypeUID(BINDING_ID,
            "mpc-power-phase-a");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase B active power, MPC Scenario 1 (AC_POWER_B). */
    public static final String CHANNEL_MPC_POWER_PHASE_B = "power-phase-b";

    /** {@link ChannelTypeUID} matching the {@code mpc-power-phase-b} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_POWER_PHASE_B = new ChannelTypeUID(BINDING_ID,
            "mpc-power-phase-b");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase C active power, MPC Scenario 1 (AC_POWER_C). */
    public static final String CHANNEL_MPC_POWER_PHASE_C = "power-phase-c";

    /** {@link ChannelTypeUID} matching the {@code mpc-power-phase-c} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_POWER_PHASE_C = new ChannelTypeUID(BINDING_ID,
            "mpc-power-phase-c");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: total consumed energy, MPC Scenario 2 (AC_ENERGY_CONSUMED). */
    public static final String CHANNEL_MPC_ENERGY_CONSUMED = "energy-consumed";

    /** {@link ChannelTypeUID} matching the {@code mpc-energy-consumed} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_ENERGY_CONSUMED = new ChannelTypeUID(BINDING_ID,
            "mpc-energy-consumed");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: total produced energy, MPC Scenario 2 (AC_ENERGY_PRODUCED). */
    public static final String CHANNEL_MPC_ENERGY_PRODUCED = "energy-produced";

    /** {@link ChannelTypeUID} matching the {@code mpc-energy-produced} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_ENERGY_PRODUCED = new ChannelTypeUID(BINDING_ID,
            "mpc-energy-produced");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase A AC current, MPC Scenario 3 (AC_CURRENT_A). */
    public static final String CHANNEL_MPC_CURRENT_PHASE_A = "current-phase-a";

    /** {@link ChannelTypeUID} matching the {@code mpc-current-phase-a} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_A = new ChannelTypeUID(BINDING_ID,
            "mpc-current-phase-a");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase B AC current, MPC Scenario 3 (AC_CURRENT_B). */
    public static final String CHANNEL_MPC_CURRENT_PHASE_B = "current-phase-b";

    /** {@link ChannelTypeUID} matching the {@code mpc-current-phase-b} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_B = new ChannelTypeUID(BINDING_ID,
            "mpc-current-phase-b");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase C AC current, MPC Scenario 3 (AC_CURRENT_C). */
    public static final String CHANNEL_MPC_CURRENT_PHASE_C = "current-phase-c";

    /** {@link ChannelTypeUID} matching the {@code mpc-current-phase-c} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_C = new ChannelTypeUID(BINDING_ID,
            "mpc-current-phase-c");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase A-neutral AC voltage, MPC Scenario 4 (AC_VOLTAGE_A). */
    public static final String CHANNEL_MPC_VOLTAGE_PHASE_A = "voltage-phase-a";

    /** {@link ChannelTypeUID} matching the {@code mpc-voltage-phase-a} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_VOLTAGE_PHASE_A = new ChannelTypeUID(BINDING_ID,
            "mpc-voltage-phase-a");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase B-neutral AC voltage, MPC Scenario 4 (AC_VOLTAGE_B). */
    public static final String CHANNEL_MPC_VOLTAGE_PHASE_B = "voltage-phase-b";

    /** {@link ChannelTypeUID} matching the {@code mpc-voltage-phase-b} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_VOLTAGE_PHASE_B = new ChannelTypeUID(BINDING_ID,
            "mpc-voltage-phase-b");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: phase C-neutral AC voltage, MPC Scenario 4 (AC_VOLTAGE_C). */
    public static final String CHANNEL_MPC_VOLTAGE_PHASE_C = "voltage-phase-c";

    /** {@link ChannelTypeUID} matching the {@code mpc-voltage-phase-c} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_VOLTAGE_PHASE_C = new ChannelTypeUID(BINDING_ID,
            "mpc-voltage-phase-c");

    /**
     * Channel ID within {@value #CHANNEL_GROUP_MPC}: phase A-B AC voltage, MPC Scenario 4 - stub, never populated
     * (docs/ADR/037-mpc-additional-datapoints.md: no distinct ScopeType exists in this jeebus.spine version).
     */
    public static final String CHANNEL_MPC_VOLTAGE_A_B = "voltage-a-b";

    /** {@link ChannelTypeUID} matching the {@code mpc-voltage-a-b} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_VOLTAGE_A_B = new ChannelTypeUID(BINDING_ID,
            "mpc-voltage-a-b");

    /**
     * Channel ID within {@value #CHANNEL_GROUP_MPC}: phase B-C AC voltage, MPC Scenario 4 - stub, never populated (see
     * CHANNEL_MPC_VOLTAGE_A_B).
     */
    public static final String CHANNEL_MPC_VOLTAGE_B_C = "voltage-b-c";

    /** {@link ChannelTypeUID} matching the {@code mpc-voltage-b-c} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_VOLTAGE_B_C = new ChannelTypeUID(BINDING_ID,
            "mpc-voltage-b-c");

    /**
     * Channel ID within {@value #CHANNEL_GROUP_MPC}: phase C-A AC voltage, MPC Scenario 4 - stub, never populated (see
     * CHANNEL_MPC_VOLTAGE_A_B).
     */
    public static final String CHANNEL_MPC_VOLTAGE_C_A = "voltage-c-a";

    /** {@link ChannelTypeUID} matching the {@code mpc-voltage-c-a} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_VOLTAGE_C_A = new ChannelTypeUID(BINDING_ID,
            "mpc-voltage-c-a");

    /** Channel ID within {@value #CHANNEL_GROUP_MPC}: grid AC frequency, MPC Scenario 5 (AC_FREQUENCY_GRID). */
    public static final String CHANNEL_MPC_FREQUENCY = "frequency";

    /** {@link ChannelTypeUID} matching the {@code mpc-frequency} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MPC_FREQUENCY = new ChannelTypeUID(BINDING_ID, "mpc-frequency");

    /**
     * Thing property key prefix convention for recording that a Client-role use case was
     * detected for a trusted {@code eebus:oh-entity} Entity - value is the actor role the Entity
     * plays for that use case ({@code "server"}/{@code "client"}), see
     * {@code EEBusOhEntityHandler#recordDetectedUseCase} and
     * docs/changes/dynamic-client-role-channels/proposal.md "Open Questions".
     */
    public static final String USE_CASE_KEY_MPC = "mpc";

    /**
     * Channel Group ID for MGCP (Monitoring of Grid Connection Point) Client-role Channels,
     * created dynamically on a trusted {@code eebus:oh-entity} Thing once MGCP is detected for
     * that Entity - see {@code EEBusOhEntityHandler#applyMgcpMeasurement} and
     * docs/ADR/040-mgcp-client-usecase.md. Mirrors {@value #CHANNEL_GROUP_MPC}'s shape.
     */
    public static final String CHANNEL_GROUP_MGCP = "mgcp";

    /**
     * Channel ID within {@value #CHANNEL_GROUP_MGCP}: total active power at the Grid Connection
     * Point, MGCP Scenario 2 (Mandatory, [MGCP-021], {@code ScopeTypeEnumType.AC_POWER_TOTAL} -
     * the same scope value MPC's own {@link #CHANNEL_MPC_POWER} resolves via).
     */
    public static final String CHANNEL_MGCP_TOTAL_ACTIVE_POWER = "total-active-power";

    /** {@link ChannelTypeUID} matching the {@code mgcp-total-active-power} {@code channel-type} in thing-types.xml. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_MGCP_TOTAL_ACTIVE_POWER = new ChannelTypeUID(BINDING_ID,
            "mgcp-total-active-power");

    /**
     * Thing property key prefix convention for recording that MGCP was detected for a trusted
     * {@code eebus:oh-entity} Entity - see {@link #USE_CASE_KEY_MPC} for the shared convention.
     */
    public static final String USE_CASE_KEY_MGCP = "mgcp";

    /**
     * Channel Group IDs for LPC/LPP Client-role Channels, created dynamically on a trusted
     * {@code eebus:oh-entity} Thing once LPC/LPP is detected for that Entity - see
     * {@code EEBusOhEntityHandler#applyLimitStatus} and
     * docs/ADR/015-lpc-lpp-client-role-channels.md.
     */
    public static final String CHANNEL_GROUP_LPC = "lpc";

    /** @see #CHANNEL_GROUP_LPC */
    public static final String CHANNEL_GROUP_LPP = "lpp";

    /**
     * Channel IDs shared by {@value #CHANNEL_GROUP_LPC}/{@value #CHANNEL_GROUP_LPP} - LPC/LPP are
     * structurally identical (CONCEPT.md §5.4.2), so the same Channel IDs/types are instantiated
     * under either Channel Group.
     */
    public static final String CHANNEL_LIMIT_ACTIVE = "limit-active";

    /** @see #CHANNEL_LIMIT_ACTIVE */
    public static final String CHANNEL_LIMIT_VALUE = "limit-value";

    /**
     * @see #CHANNEL_LIMIT_ACTIVE
     * @see #CHANNEL_LIMIT_VALUE
     */
    public static final String CHANNEL_LIMIT_DURATION = "limit-duration";

    /** {@link ChannelTypeUID} matching the {@code limit-active} {@code channel-type}. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_LIMIT_ACTIVE = new ChannelTypeUID(BINDING_ID, "limit-active");

    /** {@link ChannelTypeUID} matching the {@code limit-value} {@code channel-type}. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_LIMIT_VALUE = new ChannelTypeUID(BINDING_ID, "limit-value");

    /** {@link ChannelTypeUID} matching the {@code limit-duration} {@code channel-type}. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_LIMIT_DURATION = new ChannelTypeUID(BINDING_ID,
            "limit-duration");

    /**
     * Read-only Channel ID shared by {@value #CHANNEL_GROUP_LPC}/{@value #CHANNEL_GROUP_LPP} showing the
     * failsafe limit value the paired Energy Guard has configured over EEBus (Scenario 2) - see
     * docs/ADR/022-controllable-system-failsafe-status-channel-and-startup-sync.md. Deliberately no
     * corresponding write path from openHAB.
     */
    public static final String CHANNEL_FAILSAFE_LIMIT_VALUE = "failsafe-limit-value";

    /** @see #CHANNEL_FAILSAFE_LIMIT_VALUE */
    public static final String CHANNEL_FAILSAFE_DURATION_MINIMUM = "failsafe-duration-minimum";

    /** {@link ChannelTypeUID} matching the {@code failsafe-limit-value} {@code channel-type}. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_FAILSAFE_LIMIT_VALUE = new ChannelTypeUID(BINDING_ID,
            "failsafe-limit-value");

    /** {@link ChannelTypeUID} matching the {@code failsafe-duration-minimum} {@code channel-type}. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_FAILSAFE_DURATION_MINIMUM = new ChannelTypeUID(BINDING_ID,
            "failsafe-duration-minimum");

    /**
     * Read-only Channel ID shared by {@value #CHANNEL_GROUP_LPC}/{@value #CHANNEL_GROUP_LPP} -
     * the Controllable System state machine's current state, encoded as a plain {@code Number}
     * ({@link org.openhab.binding.eebus.internal.transport.EEBusLimitControlState#ordinal()}) -
     * see {@code EEBusOhEntityHandler#applyLimitControlState}.
     */
    public static final String CHANNEL_STATE = "state";

    /** {@link ChannelTypeUID} matching the {@code state} {@code channel-type}. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_STATE = new ChannelTypeUID(BINDING_ID, "state");

    /**
     * Trigger Channel ID shared by {@value #CHANNEL_GROUP_LPC}/{@value #CHANNEL_GROUP_LPP} - fires
     * once per Heartbeat received from the paired Energy Guard's {@code DeviceDiagnosis} feature.
     */
    public static final String CHANNEL_HEARTBEAT = "heartbeat";

    /** {@link ChannelTypeUID} matching the {@code heartbeat} {@code channel-type}. */
    public static final ChannelTypeUID CHANNEL_TYPE_UID_HEARTBEAT = new ChannelTypeUID(BINDING_ID, "heartbeat");

    /** @see #USE_CASE_KEY_MPC */
    public static final String USE_CASE_KEY_LPC = "lpc";

    /** @see #USE_CASE_KEY_MPC */
    public static final String USE_CASE_KEY_LPP = "lpp";
}
