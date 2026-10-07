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
 * The {@link EEBusOhEntityConfiguration} class contains the configuration parameters of the
 * {@code eebus:oh-entity} Thing, i.e. of exactly one SPINE Entity on a device trusted by the
 * parent Bridge.
 *
 * <p>
 * <strong>Also used by {@code eebus:oh-cs-entity}</strong> (docs/ADR/025-oh-cs-entity-static-
 * channels.md, revised by docs/ADR/027-derive-local-use-cases-from-entities.md): the additive
 * Controllable System counterpart under {@code eebus:oh-device} needs the same {@code ski}/
 * {@code entityAddress}/{@code shipId} fields, so it reuses this class rather than duplicating
 * it.
 * </p>
 *
 * <p>
 * <strong>Also used by {@code eebus:oh-eg-entity}</strong> (docs/ADR/026-oh-eg-entity-static-
 * channels.md): the alternative Entity type under {@code eebus:oh-device} for the EnergyGuard
 * (LPC/LPP Client role) case needs exactly the same fields too, so it reuses this class as well.
 * </p>
 *
 * <p>
 * <strong>Also used by {@code eebus:oh-mpc-entity}</strong> (docs/ADR/036-oh-mpc-entity-static-
 * channels.md): the alternative Entity type under {@code eebus:oh-device} dedicated to the MPC
 * Client role needs exactly the same fields too, so it reuses this class as well.
 * </p>
 *
 * <p>
 * <strong>{@link #entityType} (docs/ADR/042-configurable-entitytype.md):</strong> which SPINE
 * {@code entityType} value the parent Bridge's shared local Entity is built with, when this child
 * contributes a non-default value - see {@link #entityType}'s own javadoc and
 * {@code EEBusHandler#deriveLocalUseCases}.
 * </p>
 *
 * <p>
 * <strong>Local Use Case selection moved here from {@code EEBusConfiguration}
 * (docs/ADR/027-derive-local-use-cases-from-entities.md):</strong> {@link #supportedUseCasesClient}/
 * {@link #supportedUseCasesServer} are the plain {@code eebus:oh-entity} Thing's own free choice of
 * which local Use Cases this pairing contributes to the parent Bridge's SPINE service. They are
 * declared on this class but only exposed as {@code thing-types.xml} config-description parameters
 * for {@code eebus:oh-entity} - {@code eebus:oh-cs-entity} and {@code eebus:oh-eg-entity} imply a
 * fixed Use Case set instead (LPC+LPP Server / LPC+LPP Client respectively) and leave these two
 * fields at their {@code List.of()} default. {@link #failsafeConsumptionLimitSeedWatts}/
 * {@link #failsafeProductionLimitSeedWatts}/{@link #failsafeDurationMinimumSeedSeconds} are the
 * mirror image: only {@code eebus:oh-cs-entity} declares them (moved here from the removed
 * {@code eebus:oh-cs-device}), since it is the only remaining Thing type that unconditionally
 * implies LPC/LPP Server and therefore needs seed values for them.
 * </p>
 *
 * <p>
 * <strong>Renamed and re-scoped (docs/ADR/024-oh-device-oh-entity-rename.md):</strong> this class
 * was {@link org.openhab.binding.eebus.internal.config.EEBusOhEntityConfiguration
 * EEBusOhPeerConfiguration} and represented an entire paired device. Trust is no longer granted
 * by configuring/creating this Thing at all - it now lives entirely on the parent Bridge's own
 * {@code trustedSkis} Thing config ({@code EEBusHandler}; the live {@code trust()}/
 * {@code untrust()} Thing Actions this used to also be mutable through are removed,
 * docs/ADR/027-derive-local-use-cases-from-entities.md Decision 6). {@link #ski} here only
 * identifies *which* already-trusted device this
 * Entity lives on; {@link #entityAddress} identifies *which* Entity on that device. This
 * supersedes docs/ADR/012-pairing-trust-property-and-actions.md's model, where creating/
 * configuring this Thing's predecessor was tied to a pairing property and Thing Actions of its
 * own.
 * </p>
 *
 * <p>
 * <strong>Origin (CONCEPT.md §4.5, §7(15)/ADR-020):</strong> split out of the former single
 * {@code eebus:peer} Thing type - this class takes over the (now Entity-scoped) channel/metadata
 * responsibilities previously described for {@code eebus:peer} when configured under
 * {@code eebus:service}. The passive, real-device-record half of that former type now lives in
 * {@code EEBusHwDeviceConfiguration} ({@code eebus:hw-device}, bridgeless, no {@link #shipId}
 * since it never performs a handshake).
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusOhEntityConfiguration {

    /**
     * Subject Key Identifier (SKI) of the remote device this Entity lives on, 40 lowercase hex
     * characters. Identifies which already-trusted device to look for this Entity on - it does
     * not, by itself, grant trust (see class javadoc); this Thing stays offline until the parent
     * Bridge actually trusts this SKI.
     *
     * <p>
     * <strong>Optional (2026-08-26 revision):</strong> if left blank, {@code EEBusOhEntityHandler
     * #ensureSkiConfigured} auto-assigns it once the parent Bridge trusts exactly one device -
     * with zero or more than one trusted device, it must be chosen manually, either as free text
     * or from the parent Bridge's currently trusted SKIs (offered via {@code
     * EEBusSkiOptionProvider} for an already-existing Thing - see its class javadoc "Known
     * limitation" for why this dropdown cannot be populated during the "Add Thing" wizard
     * itself, which is why this field is optional rather than required). See CONCEPT.md
     * §4.4/§4.5 for why there is no automatic link between an {@code eebus:hw-device} and this
     * Thing yet.
     * </p>
     */
    public String ski = "";

    /**
     * SPINE address of this Entity on the device identified by {@link #ski} (as shown by device
     * discovery/{@code NodeManagement} data). Recorded for reference - see
     * docs/ADR/024-oh-device-oh-entity-rename.md "Out of scope": the transport-layer resolver
     * ({@code EEBusHandler#ohEntityHandlerForSki}) does not yet use this to route data to a
     * specific Entity when more than one {@code eebus:oh-entity} Thing shares the same
     * {@link #ski} - all such Things currently observe the same device-level events.
     */
    public String entityAddress = "";

    /**
     * SHIP-ID of the remote device. Empty until learned during the first successful
     * handshake; the handler persists it here via {@code updateConfiguration()} once
     * known, so it does not need to be re-learned on every restart.
     */
    public String shipId = "";

    /**
     * {@code eebus:oh-entity} only. Use case abbreviations (see CONCEPT.md §5.4.1, e.g.
     * {@code "LPC"}, {@code "MPC"}) that openHAB should try to detect/consume on this Entity
     * (client/consumer role, CONCEPT.md §5.4), contributed to the parent Bridge's local Use-Case
     * set - see docs/ADR/027-derive-local-use-cases-from-entities.md. Not yet wired up beyond
     * that contribution - see CONCEPT.md §7 items 3+8. Left at its default by
     * {@code eebus:oh-cs-entity}/{@code eebus:oh-eg-entity}, which imply a fixed Use Case set
     * instead (their {@code thing-types.xml} config-descriptions do not declare this parameter).
     */
    public List<String> supportedUseCasesClient = List.of();

    /**
     * {@code eebus:oh-entity} only. Use case abbreviations that openHAB should offer to the
     * network itself on this Entity (server/provider role, CONCEPT.md §5.5), fed via
     * {@code eebus} Item/Rule metadata (CONCEPT.md §4.2), contributed to the parent Bridge's
     * local Use-Case set - see docs/ADR/027-derive-local-use-cases-from-entities.md. Not yet
     * wired up beyond that contribution - see CONCEPT.md §7 items 6-8. Left at its default by
     * {@code eebus:oh-cs-entity}/{@code eebus:oh-eg-entity}, which imply a fixed Use Case set
     * instead (their {@code thing-types.xml} config-descriptions do not declare this parameter).
     */
    public List<String> supportedUseCasesServer = List.of();

    /**
     * {@code eebus:oh-cs-entity} only (docs/ADR/025-oh-cs-entity-static-channels.md, moved here
     * from the removed {@code eebus:oh-cs-device} by docs/ADR/027-derive-local-use-cases-from-
     * entities.md) - the seed value for the LPC failsafe consumption limit (Watts), used as the
     * parent Bridge's own {@code FailsafeConsumptionActivePowerLimit} at every startup, until
     * (and unless) a paired Energy Guard writes a different value over EEBus. Not persisted
     * across restarts by openHAB - every restart re-seeds from this Thing-config value, by
     * design (only EEBus, not openHAB, is meant to change the effective running value - see
     * ADR-022/ADR-023). Ignored by {@code eebus:oh-entity}/{@code eebus:oh-eg-entity} (their
     * {@code thing-types.xml} config-descriptions do not declare this parameter).
     */
    public double failsafeConsumptionLimitSeedWatts;

    /** {@code eebus:oh-cs-entity} only - same as {@link #failsafeConsumptionLimitSeedWatts}, for LPP. */
    public double failsafeProductionLimitSeedWatts;

    /**
     * {@code eebus:oh-cs-entity} only - the seed value for {@code FailsafeDurationMinimum}
     * (seconds), shared by LPC and LPP (CONCEPT.md §4.2 data-point table). Defaults to
     * {@code 120}, matching
     * {@code AbstractEEBusLimitControllableSystemUseCase#DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS}.
     */
    public long failsafeDurationMinimumSeedSeconds = 120;

    /**
     * SPINE {@code entityType} to build the parent Bridge's shared local Entity with, if this
     * child's value differs from the default (docs/ADR/042-configurable-entitytype.md). Defaults
     * to {@code "CEM"}, matching this binding's previous hardcoded behaviour. Each convenience
     * Thing type's {@code thing-types.xml} config-description restricts the dropdown to that
     * Thing's own spec-permitted subset ({@code eebus:oh-eg-entity}: CEM/GridGuard; {@code
     * eebus:oh-cs-entity}: CEM/EVSE/Inverter/SmartEnergyAppliance/SubMeterElectricity; {@code
     * eebus:oh-mpc-entity}/{@code eebus:oh-entity}: the full SPINE {@code EntityTypeEnumType} set,
     * since neither actor's spec restricts it) - but this field itself is not validated against
     * sibling children or the actual Use Case combination ending up on the shared Entity; see
     * {@code EEBusHandler#deriveLocalUseCases} and the ADR's "Consequences" for why that check is
     * deliberately deferred.
     */
    public String entityType = "CEM";

    /**
     * {@code eebus:oh-hems-entity} only (docs/ADR/053-hems-convenience-entity.md): SKI of the CLS
     * gateway, the Energy Guard partner of the HEMS's Controllable System Entity. Blank for every
     * other Thing type.
     */
    public String gatewaySki = "";

    /**
     * {@code eebus:oh-hems-entity} only: SKI of the wallbox. If blank, the HEMS builds no
     * wallbox Energy Guard Entity.
     */
    public String wallboxSki = "";

    /**
     * {@code eebus:oh-hems-entity} only: SKI of the heat pump. If blank, the HEMS builds no heat
     * pump Energy Guard Entity.
     */
    public String heatPumpSki = "";

    /**
     * {@code eebus:oh-hems-entity} only: SPINE {@code entityType} of the HEMS's Energy Guard
     * Entities. Spec-permitted for the Energy Guard actor: {@code CEM} or {@code GridGuard}
     * (docs/ADR/042-configurable-entitytype.md). Default {@code GridGuard}, as the evcc HEMS
     * presents it.
     */
    public String egEntityType = "GridGuard";
}
