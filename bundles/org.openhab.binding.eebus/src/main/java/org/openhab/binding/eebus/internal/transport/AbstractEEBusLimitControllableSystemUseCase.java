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
package org.openhab.binding.eebus.internal.transport;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openhab.core.items.Metadata;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
import org.openmuc.jeebus.spine.api.DataValidationException;
import org.openmuc.jeebus.spine.api.Device;
import org.openmuc.jeebus.spine.api.Entity;
import org.openmuc.jeebus.spine.api.Feature;
import org.openmuc.jeebus.spine.api.FeatureWrapper;
import org.openmuc.jeebus.spine.api.NodeManagement;
import org.openmuc.jeebus.spine.api.PresenceIndication;
import org.openmuc.jeebus.spine.api.RequestResult;
import org.openmuc.jeebus.spine.api.UseCasePartner;
import org.openmuc.jeebus.spine.spi.AllowedEntityTypes;
import org.openmuc.jeebus.spine.spi.FeatureRequirement;
import org.openmuc.jeebus.spine.spi.Inject;
import org.openmuc.jeebus.spine.spi.UseCase;
import org.openmuc.jeebus.spine.utils.datatypes.ScaledNumberWrapper;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.DeviceConfigurationFeature;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.KeyValueDescriptionListDataFunction;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.KeyValueInitialData;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.KeyValueListDataFunction;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.RunningKeyValue;
import org.openmuc.jeebus.spine.utils.features.devicediagnosis.DeviceDiagnosisFeature;
import org.openmuc.jeebus.spine.utils.features.devicediagnosis.HeartbeatDataFunction;
import org.openmuc.jeebus.spine.utils.features.loadcontrol.LimitDescriptionFunction;
import org.openmuc.jeebus.spine.utils.features.loadcontrol.LimitListDataFunction;
import org.openmuc.jeebus.spine.utils.features.loadcontrol.LoadControlFeature;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyNameEnumType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueTypeType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueValueType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceDiagnosisHeartbeatDataType;
import org.openmuc.jeebus.spine.xsd.v1.EnergyDirectionEnumType;
import org.openmuc.jeebus.spine.xsd.v1.EntityTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FunctionEnumType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlCategoryEnumType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitDataType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.NodeManagementBindingRequestCallType;
import org.openmuc.jeebus.spine.xsd.v1.RoleType;
import org.openmuc.jeebus.spine.xsd.v1.ScaledNumberType;
import org.openmuc.jeebus.spine.xsd.v1.TimePeriodType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared implementation of the LPC/LPP "Controllable System" (Server-role) use case - see
 * {@link EEBusLpcServerUseCase} (Limitation of Power Consumption) and
 * {@link EEBusLppServerUseCase} (Limitation of Power Production), which are thin subclasses
 * supplying the direction-specific constants. CONCEPT.md §5.4.2/§7: implements all three
 * mandatory scenarios for the Controllable System actor (Table 2 of
 * {@code EEBus_UC_TS_LimitationOfPowerConsumption_V1.0.0_public.pdf}, confirmed structurally
 * identical for LPP) - scenario 4 ("Constraints") is only Recommended for this actor and is
 * deliberately not implemented.
 *
 * <p>
 * <strong>Wire-format actor strings</strong> ("EnergyGuard"/"ControllableSystem", not the
 * human-readable catalog names "Energy Guard"/"Controllable System") verified against
 * {@code EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf}: "in the LPC Use Case, the 'EnergyGuard'
 * (Client Actor) hosts a Server Feature to provide its own heartbeat to the
 * 'ControllableSystem' (Server Actor)" - this quote also confirms the mutual-heartbeat design
 * below (both actors expose their own {@code DeviceDiagnosis} Heartbeat).
 * </p>
 *
 * <p>
 * <strong>Scenario 1 (Limit):</strong> a {@code LoadControl} Server feature with a single limit
 * entry ({@code MAX_VALUE_LIMIT}, {@code OBLIGATION}). LPC and LPP share one local
 * {@code LoadControl} feature (CONCEPT.md §5.4.2, confirmed against a real Hager Energy S10
 * discovery capture), so each subclass contributes its own entry keyed by a distinct
 * {@link #getLimitId()} - see docs/ADR/018-lpc-lpp-limitid-assignment.md; before that ADR both
 * directions hardcoded the same {@code limitId=0}, so one direction's entry silently overwrote
 * the other's in the peer-visible list (jeebus.spine's {@code LimitDescriptionFunction}/
 * {@code LimitListDataFunction} key their list-data store by {@code limitId}). The Energy
 * Guard's writes are observed via {@code LimitListDataFunction#addUseCaseWriteDataListener} -
 * this also fires on the framework's own automatic deactivation when a limit's
 * {@code timePeriod} expires (verified in {@code LimitListDataFunction} source:
 * {@code writeData()} calls {@code scheduleStartAndExpiration}, which re-fires the same
 * listeners on expiry) - so this class does not need to reimplement duration-expiry timing
 * itself. Because the write listener and the peer-visible list entry are both shared between
 * LPC and LPP, {@link #onLimitWritten} filters by {@code limitId} and {@link #onStateChanged}
 * always publishes to this instance's own captured {@link #limitDataIndex} - see
 * docs/ADR/019-isolate-lpc-lpp-server-write-handling.md. Both {@link #onStateChanged} and
 * {@link #onLimitWritten} publish through the shared {@link #publishLimitState} - needed because
 * a value-only write that leaves the FSM state unchanged (e.g. {@code limitValue} changed while
 * {@code limitActive} stays ON) does not on its own invoke {@link #onStateChanged}, see
 * docs/ADR/032-republish-limitvalue-only-writes.md.
 * </p>
 *
 * <p>
 * <strong>Scenario 2 (Failsafe values):</strong> <em>two</em> {@code DeviceConfiguration}
 * key/value entries - {@code FailsafeConsumptionActivePowerLimit}/
 * {@code FailsafeProductionActivePowerLimit} (subclass-specific) and
 * {@code FailsafeDurationMinimum} (shared) - both writable, wired via the
 * {@link KeyValueInitialData}/{@link RunningKeyValue} helper API (the "raw" path of directly
 * calling {@code addData}/{@code updateData} on the list functions is not meant to be used
 * directly per the source, which manages key IDs and write-permission bookkeeping itself).
 * The current values are additionally mirrored, read-only, onto the tracked Energy Guard's
 * paired oh-entity as {@code failsafe-limit-value}/{@code failsafe-duration-minimum} Channels -
 * by explicit user decision there is deliberately no path from openHAB (Item, metadata, or
 * Rule) back into these values, only the Energy Guard writing over EEBus can change them - see
 * docs/ADR/022-controllable-system-failsafe-status-channel-and-startup-sync.md.
 * </p>
 *
 * <p>
 * <strong>Scenario 3 (Heartbeat):</strong> mutual. This entity's own outgoing heartbeat is
 * fully handled by {@link HeartbeatDataFunction#startHeartbeat()} (self-perpetuating, verified
 * in its source - no polling loop needed here). The Energy Guard's incoming heartbeat is
 * watched by detecting the partner via {@code NodeManagement#addUseCaseListener} (exactly the
 * pattern demonstrated in jeebus.spine's demo {@code ExampleUseCase}) and subscribing to its
 * {@code DeviceDiagnosis} feature; every notification resets
 * {@link EEBusLimitControlStateMachine}'s Heartbeat watchdog.
 * </p>
 *
 * <p>
 * <strong>Pragmatic simplification:</strong> only the first detected Energy Guard partner is
 * tracked - the LPC/LPP spec does not clearly define multi-Energy-Guard behavior either, and
 * v1's motivating scenario (CONCEPT.md §1) has exactly one.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
// LPC TS §3.2.2.1.1 permits CEM/Compressor/EVSE/HeatPumpAppliance/Inverter/SmartEnergyAppliance/
// SubMeterElectricity for the Controllable System actor; LPP TS §3.2.2.1.1 permits a narrower set
// (no Compressor/HeatPumpAppliance). Since this class binds both LPC and LPP simultaneously on
// the same shared Entity, only the intersection is actually legal - see
// docs/ADR/042-configurable-entitytype.md's Context/Decision sections, which already established
// this set for oh-cs-entity's config dropdown but left this runtime-enforcing annotation at its
// old CEM-only value (fixed 2026-09-04 alongside the analogous EnergyGuard/MPC/MGCP gaps).
@AllowedEntityTypes({ EntityTypeEnumType.CEM, EntityTypeEnumType.EVSE, EntityTypeEnumType.INVERTER,
        EntityTypeEnumType.SMART_ENERGY_APPLIANCE, EntityTypeEnumType.SUB_METER_ELECTRICITY })
public abstract class AbstractEEBusLimitControllableSystemUseCase implements UseCase {

    private static final String ACTOR_ENERGY_GUARD = "EnergyGuard";
    private static final String ACTOR_CONTROLLABLE_SYSTEM = "ControllableSystem";

    /** Data point shared by both LPC and LPP (CONCEPT.md §4.2 data-point table). */
    static final String DATA_POINT_FAILSAFE_DURATION = "failsafeDurationMinimum";
    static final String DATA_POINT_STATE = "state";

    /**
     * Pragmatic default (2 minutes) - the spec leaves the exact value to the CS; the Energy
     * Guard may write a different one at runtime (scenario 2).
     */
    // Package-external visibility deliberate: EEBusHandler (internal.handler) passes this as
    // the initialFailsafeDurationMinimumSeconds constructor arg for the checkbox-driven
    // eebus:oh-device path, so the default lives in exactly one place - see docs/ADR/023-cs-
    // service-convenience-bridge.md.
    public static final long DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS = 120;

    /**
     * Bounded backoff (seconds) for {@link #resolveEnergyGuardOhEntityHandler}'s retries -
     * see docs/ADR/030-energy-guard-resolution-retry-and-byhost-disambiguation.md. Deliberately
     * more generous than ADR-029's 250/500/750 ms {@code startShipSpine()} retry: that retry
     * only has to outlast a previous generation's teardown tail, while this one has to outlast
     * mDNS resolution actually completing, which is inherently less predictable - sized from
     * the ~8 s gap actually observed live between a failed resolution and the peer's mDNS
     * record settling (2026-08-27 retest, see project memory), with headroom above that for a
     * slower network.
     */
    private static final int[] ENERGY_GUARD_RESOLUTION_RETRY_DELAYS_SECONDS = { 2, 4, 8, 16, 30 };

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final EEBusMetadataService metadataService;
    private final String ohServiceId;
    private final Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Inject
    private @Nullable Entity entity;

    private @Nullable FeatureAddressType address;
    private @Nullable Device device;
    private @Nullable EEBusLimitControlStateMachine stateMachine;
    private @Nullable LimitListDataFunction limitListDataFunction;
    /**
     * The paired {@code eebus:oh-entity} handler for the currently tracked Energy Guard partner
     * (see {@link #onEnergyGuardFound}), resolved via {@link #ohEntityHandlerResolver} - or
     * {@code null} if no Energy Guard has been found yet, or its {@code communicationAddress}
     * could not be resolved to a paired oh-entity. Used by {@link #onStateChanged} to mirror the
     * confirmed limit status onto that peer's dynamic {@code limit-active}/{@code limit-value}
     * Channel - see docs/ADR/021-controllable-system-limit-status-channel.md.
     */
    private @Nullable EEBusOhEntityHandler energyGuardOhEntityHandler;
    /**
     * The {@code communicationAddress} of the most recent partner {@link #onEnergyGuardFound}
     * saw - lets a pending {@link #resolveEnergyGuardOhEntityHandler} retry recognize it has
     * been superseded by a newer discovery notification (e.g. a reconnect that changed the
     * peer's live {@code communicationAddress}) and drop itself instead of possibly clobbering
     * {@link #energyGuardOhEntityHandler} with a stale resolution after the fact. See docs/ADR/
     * 030-energy-guard-resolution-retry-and-byhost-disambiguation.md.
     */
    private volatile @Nullable String latestEnergyGuardCommunicationAddress;
    /**
     * List index this instance's own {@code LoadControlLimitData} entry was assigned by
     * {@code addData()} in {@link #setupLoadControl}, or {@code -1} if not yet assigned (or the
     * initial {@code addData()} failed). {@code LimitListDataFunction#updateData(int, ...)} is a
     * literal list index, not a {@code limitId} lookup - see docs/ADR/019-isolate-lpc-lpp-server-
     * write-handling.md - so {@link #onStateChanged} must always publish to this instance's own
     * captured index, never a hardcoded one, since LPC and LPP share one list on the same Entity.
     */
    private volatile int limitDataIndex = -1;
    private @Nullable ScaledNumberType lastWrittenLimitValue;
    /**
     * Mirrors the {@code timePeriod.endTime} of the most recent write that carried one (see
     * {@link #onLimitWritten}), in seconds - or {@code null} if the most recent write carried no
     * {@code timePeriod} at all (per [LPC-004], meaning the limit is currently unbounded). Per
     * LimitationOfPowerConsumption TS V1.0.0 §3.1.8.2, this SPINE field is always a relative
     * duration, never an absolute timestamp - see docs/ADR/033-lpc-lpp-limit-duration-channel.md.
     */
    private @Nullable Long lastWrittenLimitDurationSeconds;
    /**
     * Set once by the constructor from {@code initialFailsafeDurationMinimumSeconds} (the
     * {@code eebus:oh-device} checkbox path passes {@link #DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS};
     * {@code eebus:oh-cs-device} passes its own Thing-config seed - see docs/ADR/023-cs-service-
     * convenience-bridge.md) - then only ever overwritten by a real Energy Guard's write
     * ({@link #onFailsafeDurationWritten}), never by openHAB itself.
     */
    private volatile long failsafeDurationMinimumSeconds;
    /**
     * Mirrors the last value the Energy Guard wrote to the failsafe limit DeviceConfiguration key
     * ({@link #onFailsafeLimitWritten}), so {@link #publishFailsafeStatus} can republish the current
     * value on demand (e.g. once a peer resolves) without waiting for the next write - see
     * docs/ADR/022-controllable-system-failsafe-status-channel-and-startup-sync.md. Set once by
     * the constructor from {@code initialFailsafeLimitWatts} ({@code eebus:oh-device} passes
     * {@code 0.0}; {@code eebus:oh-cs-device} passes its own Thing-config seed - see docs/ADR/023-
     * cs-service-convenience-bridge.md), then mirrored into {@link #setupDeviceConfiguration}'s
     * initial SPINE {@code withValue(...)} so the wire-visible value and this tracking field never
     * start out of sync.
     */
    private volatile double lastFailsafeLimitWatts;
    /**
     * {@code device+entity} identity (see {@link #entityKey}) of the peer Entity that most
     * recently bound our local {@code LoadControl} feature, or {@code null} if none has bound
     * yet since the last {@link #setup()} (reset there so a reconnect - a fresh SPINE Device/
     * FeatureImpl generation, see {@link #setup()}'s javadoc note - starts this gate fresh
     * rather than carrying over a previous connection's now-stale binding). See {@link
     * #onFeatureBound}/{@link #maybeSubscribeToEnergyGuardHeartbeat} - ADR-041/IG section 3.8.
     */
    private volatile @Nullable String loadControlBoundEntityKey;
    /** Same as {@link #loadControlBoundEntityKey}, for the {@code DeviceConfiguration} feature. */
    private volatile @Nullable String deviceConfigurationBoundEntityKey;
    /**
     * The Energy Guard partner's {@code DeviceDiagnosis} (Heartbeat) feature address, captured
     * by {@link #onEnergyGuardFound} once a partner is discovered - or {@code null} if no
     * partner has been found yet, or the Heartbeat subscription has already been sent for the
     * current partner ({@link #maybeSubscribeToEnergyGuardHeartbeat} clears it on send, both to
     * avoid a duplicate subscription and because a stale address must not survive into the next
     * connection generation).
     */
    private volatile @Nullable FeatureAddressType pendingHeartbeatSubscriptionAddress;

    /**
     * @param metadataService the service used to resolve {@code eebus} Item metadata
     * @param ohServiceId the offering {@code eebus:oh-device}/{@code eebus:oh-cs-device} Thing's ID,
     *            passed through to every {@link EEBusMetadataService#find} call (CONCEPT.md §4.5)
     * @param ohEntityHandlerResolver resolves a SPINE {@code communicationAddress} to the paired
     *            {@code eebus:oh-entity} Thing's handler, if any - used by
     *            {@link #onEnergyGuardFound} to mirror confirmed limit status onto that peer's
     *            dynamic Channel, see docs/ADR/021-controllable-system-limit-status-channel.md.
     * @param initialFailsafeLimitWatts the Watts value to seed {@link #lastFailsafeLimitWatts} and
     *            the SPINE-visible failsafe limit with at startup - {@code 0.0} for the
     *            checkbox-driven {@code eebus:oh-device} path (unchanged pre-existing default), the
     *            Thing-config seed for {@code eebus:oh-cs-device} (docs/ADR/023-cs-service-
     *            convenience-bridge.md)
     * @param initialFailsafeDurationMinimumSeconds the seconds value to seed
     *            {@link #failsafeDurationMinimumSeconds} with at startup - see
     *            {@code initialFailsafeLimitWatts} above for the same {@code eebus:oh-device}
     *            vs. {@code eebus:oh-cs-device} distinction
     */
    protected AbstractEEBusLimitControllableSystemUseCase(EEBusMetadataService metadataService, String ohServiceId,
            Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver, double initialFailsafeLimitWatts,
            long initialFailsafeDurationMinimumSeconds) {
        this.metadataService = metadataService;
        this.ohServiceId = ohServiceId;
        this.ohEntityHandlerResolver = ohEntityHandlerResolver;
        this.lastFailsafeLimitWatts = initialFailsafeLimitWatts;
        this.failsafeDurationMinimumSeconds = initialFailsafeDurationMinimumSeconds;
    }

    /** @return the metadata short code used for Item lookups, e.g. {@code "LPC"} */
    protected abstract String getShortCode();

    /**
     * @return the SPINE use case name, e.g. {@code "limitationOfPowerConsumption"} - lowerCamelCase,
     *         confirmed against a real Hager Energy S10's discovery JSON (see
     *         docs/ADR/011-usecasename-lowercamelcase.md), not the PascalCase this binding used
     *         before that fix.
     */
    protected abstract String getUseCaseName();

    /** @return {@code "consumptionLimit"}/{@code "productionLimit"} - the scenario-1 data point. */
    protected abstract String getLimitDataPoint();

    /** @return {@code "failsafeConsumptionLimit"}/{@code "failsafeProductionLimit"}. */
    protected abstract String getFailsafeLimitDataPoint();

    /**
     * @return the {@code limitId} this subclass's {@code LoadControlLimitDescriptionData}/
     *         {@code LoadControlLimitData} entry is published under on the shared local
     *         {@code LoadControl} feature - {@code 0} for LPC, {@code 1} for LPP. Must be
     *         distinct between LPC and LPP (both attach to the same feature, see class javadoc)
     *         and stable across releases (a Client-role peer resolving this by
     *         {@link #getLimitDirection()}, see docs/ADR/018-lpc-lpp-limitid-assignment.md,
     *         relies on it not changing).
     */
    protected abstract long getLimitId();

    protected abstract EnergyDirectionEnumType getLimitDirection();

    protected abstract DeviceConfigurationKeyNameEnumType getFailsafeLimitKeyName();

    @Override
    public String getActor() {
        return ACTOR_CONTROLLABLE_SYSTEM;
    }

    @Override
    public String getName() {
        return getUseCaseName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    // UseCase (jeebus.spine) is an unannotated legacy interface (no package-info.java null
    // defaults) - its getScenarioSupport()/getFeatureRequirements() are therefore unconstrained.
    // @NonNullByDefault({}) opts these two overrides out of this class's default non-null
    // constraint so the return type matches the super method exactly (otherwise ecj reports
    // "mismatching null constraints" since it would otherwise infer List<@NonNull Long>/
    // Set<@NonNull FeatureRequirement> against the unconstrained super signature).
    @Override
    @NonNullByDefault({})
    public List<Long> getScenarioSupport() {
        return List.of(1L, 2L, 3L);
    }

    @Override
    public FeatureAddressType getAddress() {
        FeatureAddressType currentAddress = address;
        if (currentAddress == null) {
            throw new IllegalStateException("getAddress() called before setup()");
        }
        return currentAddress;
    }

    @Override
    @NonNullByDefault({})
    public Set<FeatureRequirement> getFeatureRequirements(EntityTypeEnumType entityType) {
        return Set.of(FeatureRequirement.GENERIC_CLIENT,
                new FeatureRequirement(FeatureTypeEnumType.LOAD_CONTROL, RoleType.SERVER,
                        LimitDescriptionFunction.class, LimitListDataFunction.class),
                new FeatureRequirement(FeatureTypeEnumType.DEVICE_CONFIGURATION, RoleType.SERVER,
                        KeyValueDescriptionListDataFunction.class, KeyValueListDataFunction.class),
                new FeatureRequirement(FeatureTypeEnumType.DEVICE_DIAGNOSIS, RoleType.SERVER,
                        HeartbeatDataFunction.class));
    }

    @Override
    public void setup() {
        Entity localEntity = this.entity;
        if (localEntity == null) {
            throw new IllegalStateException("Entity was not injected before setup()");
        }
        this.address = new FeatureAddressType().withDevice(localEntity.getStaticAddress().getDevice())
                .withEntity(localEntity.getStaticAddress().getEntity());
        this.device = localEntity.getDevice();
        // ADR-041: a fresh setup() means a fresh SPINE Device/FeatureImpl generation (new
        // connection) - any binding tracked against the previous generation's now-discarded
        // FeatureImpl no longer reflects reality, so this gate must not carry it over.
        this.loadControlBoundEntityKey = null;
        this.deviceConfigurationBoundEntityKey = null;
        this.pendingHeartbeatSubscriptionAddress = null;

        setupLoadControl(localEntity);
        setupDeviceConfiguration(localEntity);
        registerHeartbeatSubscriptionGate(localEntity);
        setupDeviceDiagnosis(localEntity);

        EEBusLimitControlStateMachine newStateMachine = new EEBusLimitControlStateMachine(scheduler,
                () -> failsafeDurationMinimumSeconds, this::onStateChanged);
        this.stateMachine = newStateMachine;
        // Publish the freshly reset state immediately - without this, the LPC/LPP.state metadata Item,
        // the paired oh-entity's limit-active/limit-value Channel (ADR-021), and the new
        // failsafe-limit-value/failsafe-duration-minimum Channel below all keep showing whatever was
        // last published before this restart until the first real transition (Heartbeat timeout or a
        // fresh Energy Guard write) - even though the SPINE-visible LoadControlLimitData itself is
        // already safely reset by setupLoadControl above. See docs/ADR/022-controllable-system-
        // failsafe-status-channel-and-startup-sync.md.
        onStateChanged(newStateMachine.getState());
        publishFailsafeStatus();

        localEntity.getDevice().getNodeManagement().addUseCaseListener(this::onEnergyGuardFound, getName(),
                ACTOR_ENERGY_GUARD,
                Map.of(1L, PresenceIndication.MANDATORY, 2L, PresenceIndication.MANDATORY, 3L,
                        PresenceIndication.MANDATORY),
                Set.of(new CommunicationPartnerFeatureRequirement(FeatureTypeEnumType.DEVICE_DIAGNOSIS, Map.of(
                        FunctionEnumType.DEVICE_DIAGNOSIS_HEARTBEAT_DATA, Map.of(3L, PresenceIndication.MANDATORY)))));
    }

    private Feature findFeature(Entity localEntity, FeatureTypeEnumType type) {
        return localEntity.getFeatures().stream().filter(f -> f.getType() == type && f.getRole() == RoleType.SERVER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        type + " server feature missing - Entity#addUseCase() should have added it from "
                                + "getFeatureRequirements()"));
    }

    /**
     * Looks up the given feature and returns its already-attached {@link FeatureWrapper}, i.e.
     * {@code rawFeature.getFeatureWrapper(wrapperClass)} - deliberately <strong>not</strong>
     * {@code FeatureInformationService.getInstance().createFeatureWrapper(rawFeature)}, which
     * would create a second, disconnected wrapper instance. jeebus.spine's {@code FeatureImpl}
     * only calls {@code updateFunction(...)} on the one canonical wrapper it created itself in
     * {@code setType()} (see {@code Feature#getFeatureWrapper()}) - a throwaway wrapper created
     * via {@code createFeatureWrapper()} never has its function fields populated, which is what
     * caused {@code KeyValueInitialData.addToFeature()} to fail with "descriptionFunction is not
     * set in feature" the one place this was actually exercised
     * ({@link #setupDeviceConfiguration}). Found while diagnosing that failure; jeebus.spine
     * itself is out of scope to change here (project rule: no changes to jeebus.ship/jeebus.spine
     * without prior human approval), so this fixes it from the caller side instead.
     *
     * @param localEntity the entity to look the feature up on
     * @param type the feature type to look up
     * @param wrapperClass the expected wrapper type
     * @return the feature's canonical wrapper
     * @throws IllegalStateException if the feature is missing, or has no wrapper of the expected
     *             type attached (should not happen for any {@code FeatureTypeEnumType} that
     *             {@code FeatureInformationService} knows about, see its {@code cache} of known
     *             feature types)
     */
    private <T extends FeatureWrapper> T findFeatureWrapper(Entity localEntity, FeatureTypeEnumType type,
            Class<T> wrapperClass) {
        Feature rawFeature = findFeature(localEntity, type);
        @Nullable
        T wrapper = rawFeature.getFeatureWrapper(wrapperClass);
        if (wrapper == null) {
            throw new IllegalStateException(
                    type + " feature has no " + wrapperClass.getSimpleName() + " wrapper attached");
        }
        return wrapper;
    }

    private void setupLoadControl(Entity localEntity) {
        LoadControlFeature loadControlFeature = findFeatureWrapper(localEntity, FeatureTypeEnumType.LOAD_CONTROL,
                LoadControlFeature.class);
        LimitDescriptionFunction descriptionFunction = loadControlFeature.addLimitDescriptionFunction();
        LimitListDataFunction limitFunction = loadControlFeature.addLimitListDataFunction();
        this.limitListDataFunction = limitFunction;

        try {
            descriptionFunction.addData(new LoadControlLimitDescriptionDataType().withLimitId(getLimitId())
                    .withLimitType(LoadControlLimitTypeEnumType.MAX_VALUE_LIMIT.value())
                    .withLimitCategory(LoadControlCategoryEnumType.OBLIGATION.value())
                    .withLimitDirection(getLimitDirection().value()).withUnit("W"));
            // Capture our own index - see limitDataIndex javadoc for why this must not be discarded.
            this.limitDataIndex = limitFunction.addData(new LoadControlLimitDataType().withLimitId(getLimitId())
                    .withIsLimitChangeable(true).withIsLimitActive(false));
        } catch (DataValidationException e) {
            logger.warn("Failed to register {} limit description/initial data", getShortCode(), e);
        }

        limitFunction.addUseCaseWriteDataListener((data, updateType, idx) -> onLimitWritten(data));
    }

    private void onLimitWritten(LoadControlLimitDataType data) {
        // LPC and LPP share one LimitListDataFunction (class javadoc), so this listener also
        // fires for writes addressed to the other direction's limitId - ignore those (ADR-019
        // Defect 1: this used to be unfiltered, causing a write to one direction to also
        // transition the other direction's state machine).
        if (!Objects.equals(data.getLimitId(), getLimitId())) {
            return;
        }
        // ADR-033: timePeriod.endTime is a relative duration, not an absolute timestamp (per
        // LimitationOfPowerConsumption TS V1.0.0 §3.1.8.2/[LPC-004]) - track it alongside the
        // value/active state it was written together with. Absent means this write did not carry
        // a duration of validity (unbounded), not "leave the previous one alone".
        this.lastWrittenLimitDurationSeconds = parseLimitDuration(data.getTimePeriod());
        boolean hasValue = data.getValue() != null;
        if (hasValue) {
            this.lastWrittenLimitValue = data.getValue();
            double watts = new ScaledNumberWrapper(data.getValue()).toDouble();
            metadataService.find(ohServiceId, getShortCode(), getLimitDataPoint()).ifPresent(metadata -> metadataService
                    .sendCommand(EEBusMetadataService.itemNameOf(metadata), new QuantityType<>(watts, Units.WATT)));
        }
        boolean active = Boolean.TRUE.equals(data.getIsLimitActive()) && hasValue;
        EEBusLimitControlStateMachine machine = this.stateMachine;
        if (machine != null) {
            EEBusLimitControlState previousState = machine.getState();
            machine.onLimitWritten(active);
            // ADR-032: transitionTo() is edge-triggered, so a write that leaves the FSM state
            // unchanged (e.g. limitValue changed while limitActive stays ON) never invokes
            // onStateChanged, which normally republishes the value to SPINE and the peer's
            // Channel. Publish explicitly in that case so the new value is not silently dropped.
            if (hasValue && machine.getState() == previousState) {
                publishLimitState(active, data.getValue(), this.lastWrittenLimitDurationSeconds);
            }
        }
    }

    /**
     * Parses a written {@code timePeriod}'s {@code endTime} into whole seconds, or {@code null}
     * if {@code timePeriod}/{@code endTime} is absent (per [LPC-004], meaning the limit is
     * currently unbounded). Per LimitationOfPowerConsumption TS V1.0.0 §3.1.8.2, this field is
     * always a relative duration in this Use Case - never an absolute timestamp - so the parsed
     * value is used as-is, with no {@code now}-relative derivation (see docs/ADR/033-lpc-lpp-
     * limit-duration-channel.md).
     *
     * @param timePeriod the written {@code LoadControlLimitDataType#getTimePeriod()}, possibly
     *            {@code null}
     * @return the remaining duration in seconds, or {@code null} if absent/unparseable
     */
    private @Nullable Long parseLimitDuration(@Nullable TimePeriodType timePeriod) {
        if (timePeriod == null) {
            return null;
        }
        String endTime = timePeriod.getEndTime();
        if (endTime == null || endTime.isBlank()) {
            return null;
        }
        try {
            return durationToSeconds(DatatypeFactory.newDefaultInstance().newDuration(endTime));
        } catch (IllegalArgumentException e) {
            logger.warn("{} received unparseable limit timePeriod.endTime '{}', ignoring", getShortCode(), endTime, e);
            return null;
        }
    }

    private void setupDeviceConfiguration(Entity localEntity) {
        DeviceConfigurationFeature deviceConfigurationFeature = findFeatureWrapper(localEntity,
                FeatureTypeEnumType.DEVICE_CONFIGURATION, DeviceConfigurationFeature.class);
        deviceConfigurationFeature.addKeyValueDescriptionListDataFunction();
        deviceConfigurationFeature.addKeyValueListDataFunction();

        try {
            RunningKeyValue failsafeLimit = new KeyValueInitialData()
                    .withDescription(new DeviceConfigurationKeyValueDescriptionDataType()
                            .withKeyName(getFailsafeLimitKeyName().value())
                            .withValueType(DeviceConfigurationKeyValueTypeType.SCALED_NUMBER).withUnit("W"))
                    .withData(new DeviceConfigurationKeyValueDataType().withIsValueChangeable(true)
                            .withValue(new DeviceConfigurationKeyValueValueType()
                                    // Seeded from the constructor param (see docs/ADR/023-cs-service-
                                    // convenience-bridge.md), not a hardcoded 0.0 - keeps the SPINE-
                                    // visible initial value and lastFailsafeLimitWatts in sync from
                                    // the very first publish.
                                    .withScaledNumber(new ScaledNumberWrapper(lastFailsafeLimitWatts).toXsdType())))
                    .addToFeature(deviceConfigurationFeature);
            failsafeLimit.setWriteDataAllowed(true);
            failsafeLimit.addWriteDataListener((data, updateType) -> onFailsafeLimitWritten(data));

            RunningKeyValue failsafeDuration = new KeyValueInitialData()
                    .withDescription(new DeviceConfigurationKeyValueDescriptionDataType()
                            .withKeyName(DeviceConfigurationKeyNameEnumType.FAILSAFE_DURATION_MINIMUM.value())
                            .withValueType(DeviceConfigurationKeyValueTypeType.DURATION))
                    .withData(new DeviceConfigurationKeyValueDataType().withIsValueChangeable(true)
                            .withValue(new DeviceConfigurationKeyValueValueType()
                                    .withDuration(secondsToDuration(failsafeDurationMinimumSeconds))))
                    .addToFeature(deviceConfigurationFeature);
            failsafeDuration.setWriteDataAllowed(true);
            failsafeDuration.addWriteDataListener((data, updateType) -> onFailsafeDurationWritten(data));
        } catch (DataValidationException e) {
            logger.warn("Failed to register {} failsafe DeviceConfiguration key/values", getShortCode(), e);
        }
    }

    private void onFailsafeLimitWritten(DeviceConfigurationKeyValueDataType data) {
        DeviceConfigurationKeyValueValueType value = data.getValue();
        if (value == null || value.getScaledNumber() == null) {
            return;
        }
        double watts = new ScaledNumberWrapper(value.getScaledNumber()).toDouble();
        this.lastFailsafeLimitWatts = watts;
        metadataService.find(ohServiceId, getShortCode(), getFailsafeLimitDataPoint())
                .ifPresent(metadata -> metadataService.sendCommand(EEBusMetadataService.itemNameOf(metadata),
                        new QuantityType<>(watts, Units.WATT)));
        publishFailsafeStatus();
    }

    private void onFailsafeDurationWritten(DeviceConfigurationKeyValueDataType data) {
        DeviceConfigurationKeyValueValueType value = data.getValue();
        if (value == null || value.getDuration() == null) {
            return;
        }
        long seconds = durationToSeconds(value.getDuration());
        this.failsafeDurationMinimumSeconds = seconds;
        metadataService.find(ohServiceId, getShortCode(), DATA_POINT_FAILSAFE_DURATION)
                .ifPresent(metadata -> metadataService.sendCommand(EEBusMetadataService.itemNameOf(metadata),
                        new QuantityType<>(seconds, Units.SECOND)));
        publishFailsafeStatus();
    }

    /**
     * Pushes the currently known failsafe limit/duration to the tracked Energy Guard's paired oh-entity
     * Channel, if one is resolved yet - no-op otherwise (mirrors the null-check pattern
     * {@link #onStateChanged} already uses for {@link #energyGuardOhEntityHandler}). See
     * docs/ADR/022-controllable-system-failsafe-status-channel-and-startup-sync.md.
     */
    private void publishFailsafeStatus() {
        EEBusOhEntityHandler peerHandler = this.energyGuardOhEntityHandler;
        if (peerHandler != null) {
            peerHandler.applyFailsafeStatus(getShortCode(), lastFailsafeLimitWatts, failsafeDurationMinimumSeconds);
        }
    }

    private void setupDeviceDiagnosis(Entity localEntity) {
        DeviceDiagnosisFeature deviceDiagnosisFeature = findFeatureWrapper(localEntity,
                FeatureTypeEnumType.DEVICE_DIAGNOSIS, DeviceDiagnosisFeature.class);
        // "at least every 60 seconds" per LPC-005/006 (identical for LPP) - HeartbeatDataFunction
        // self-perpetuates from here on, no polling loop needed (verified in its source).
        deviceDiagnosisFeature.addHeartBeatDataFunction(60);
        deviceDiagnosisFeature.startHeartbeat();
    }

    /**
     * Registers a {@link org.openmuc.jeebus.spine.spi.BindingListener} on this Entity's local
     * {@code LoadControl} and {@code DeviceConfiguration} server features, so {@link
     * #onFeatureBound} learns about an accepted incoming binding request the moment it happens -
     * this is the jeebus.spine SPI this gate is built on ({@code Feature#addBindingListener},
     * already public API; no jeebus.spine/jeebus.ship change). See {@link #maybeSubscribeToEnergyGuardHeartbeat}.
     *
     * @param localEntity the entity whose {@code LoadControl}/{@code DeviceConfiguration}
     *            features to watch
     */
    private void registerHeartbeatSubscriptionGate(Entity localEntity) {
        findFeature(localEntity, FeatureTypeEnumType.LOAD_CONTROL)
                .addBindingListener(request -> onFeatureBound(request, true));
        findFeature(localEntity, FeatureTypeEnumType.DEVICE_CONFIGURATION)
                .addBindingListener(request -> onFeatureBound(request, false));
    }

    /**
     * ADR-041/EEBUS LPC Implementation Guideline section 3.8 ("Behaviour of Controllable System
     * in case of multiple Energy Guard instances on a connected device"): a Controllable System
     * SHALL NOT send its {@code DeviceDiagnosis} (Heartbeat) subscription request to an Energy
     * Guard Entity until it has received binding requests for <strong>both</strong> {@code
     * LoadControl} and {@code DeviceConfiguration} from that same Entity. Confirmed necessary
     * (real Hager Energy S10 never subscribes without it - see ADR-039) but, as of the
     * 2026-09-03 14:49 retest, confirmed <strong>not sufficient by itself</strong> to make the
     * S10 actually subscribe; this gate exists so our own Controllable System role (used by the
     * local simulated {@code oh-cs-device} Thing) enforces the same precondition, instead of
     * subscribing unconditionally as it did before - see the project memory entry cross-
     * referenced from ADR-041 for the full investigation this responds to.
     *
     * <p>
     * Called from {@link #onFeatureBound} (a new binding arrived) and from {@link
     * #onEnergyGuardFound} (a new partner was discovered) so either ordering - bindings
     * before discovery, or discovery before bindings - is handled the same way. Records the
     * bound Entity's {@code device+entity} identity (see {@link #entityKey}) per feature; once
     * both match each other <strong>and</strong> the pending partner's {@code DeviceDiagnosis}
     * address, the subscription is sent and {@link #pendingHeartbeatSubscriptionAddress} is
     * cleared (subscribe-once guard - this method is safe to call repeatedly/redundantly).
     *
     * @param request the accepted binding request; {@code request.getClientAddress()} identifies
     *            the peer Entity that just bound the feature
     * @param isLoadControl {@code true} if this fired for the {@code LoadControl} feature,
     *            {@code false} for {@code DeviceConfiguration}
     */
    private void onFeatureBound(NodeManagementBindingRequestCallType.BindingRequest request, boolean isLoadControl) {
        FeatureAddressType clientAddress = request.getClientAddress();
        if (clientAddress == null) {
            return;
        }
        String key = entityKey(clientAddress);
        if (isLoadControl) {
            this.loadControlBoundEntityKey = key;
            logger.debug("{} LoadControl bound by {} (ADR-041 Heartbeat subscription gate)", getShortCode(), key);
        } else {
            this.deviceConfigurationBoundEntityKey = key;
            logger.debug("{} DeviceConfiguration bound by {} (ADR-041 Heartbeat subscription gate)", getShortCode(),
                    key);
        }
        maybeSubscribeToEnergyGuardHeartbeat();
    }

    /**
     * Sends the {@code DeviceDiagnosis} (Heartbeat) subscription request for {@link
     * #pendingHeartbeatSubscriptionAddress} once - but only once - this Entity has bound both
     * {@code LoadControl} and {@code DeviceConfiguration} (ADR-041, see {@link #onFeatureBound}).
     * No-op if the gate is not yet satisfied, or if there is no pending partner to subscribe to.
     */
    private void maybeSubscribeToEnergyGuardHeartbeat() {
        FeatureAddressType heartbeatAddress = this.pendingHeartbeatSubscriptionAddress;
        if (heartbeatAddress == null) {
            return;
        }
        String loadControlKey = this.loadControlBoundEntityKey;
        String deviceConfigurationKey = this.deviceConfigurationBoundEntityKey;
        if (loadControlKey == null || deviceConfigurationKey == null
                || !loadControlKey.equals(deviceConfigurationKey)) {
            return;
        }
        if (!loadControlKey.equals(entityKey(heartbeatAddress))) {
            // Bound by a different Entity than the one we're about to subscribe to - not (yet) a
            // match for this partner. Pragmatic: this class only tracks one partner at a time
            // (see class javadoc), so this is expected to resolve once the same Entity's
            // bindings and UseCasePartner discovery catch up with each other.
            return;
        }
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        this.pendingHeartbeatSubscriptionAddress = null;
        NodeManagement nodeManagement = localDevice.getNodeManagement();
        nodeManagement.requestSubscription(heartbeatAddress, FeatureTypeEnumType.DEVICE_DIAGNOSIS,
                notification -> onHeartbeatNotification(notification)).handle((result, ex) -> {
                    if (ex != null) {
                        logger.warn("Failed to subscribe to Energy Guard Heartbeat for {}", getShortCode(), ex);
                    }
                    return null;
                });
    }

    /** @return a {@code device+entity} identity string for {@code address}, ignoring the feature number. */
    private static String entityKey(FeatureAddressType address) {
        return address.getDevice() + "#" + address.getEntity();
    }

    private void onEnergyGuardFound(List<UseCasePartner> partners) {
        if (partners.isEmpty()) {
            return;
        }
        // Pragmatic: only the first detected Energy Guard is tracked, see class javadoc.
        UseCasePartner partner = partners.get(0);
        // Resolved independently of the DeviceDiagnosis/Heartbeat check below - a peer without a
        // DeviceDiagnosis feature (seen on a real Hager Energy S10, see TEST_PAIRING.md) should
        // still get its limit-active/limit-value Channel updated - see docs/ADR/021-controllable-
        // system-limit-status-channel.md. Resolution retries with backoff instead of giving up
        // after one miss - see docs/ADR/030-energy-guard-resolution-retry-and-byhost-
        // disambiguation.md and #resolveEnergyGuardOhEntityHandler's javadoc.
        this.latestEnergyGuardCommunicationAddress = partner.getCommunicationAddress();
        resolveEnergyGuardOhEntityHandler(partner, 0);

        FeatureAddressType heartbeatAddress = partner.getCompleteFeatureAddress(FeatureTypeEnumType.DEVICE_DIAGNOSIS);
        if (heartbeatAddress == null) {
            logger.warn("{} partner at {} has no DeviceDiagnosis feature address, cannot watch its Heartbeat",
                    getShortCode(), partner.getCommunicationAddress());
            return;
        }
        // ADR-041/IG section 3.8: do not subscribe yet - wait until this Entity has bound both
        // LoadControl and DeviceConfiguration first (see #maybeSubscribeToEnergyGuardHeartbeat).
        this.pendingHeartbeatSubscriptionAddress = heartbeatAddress;
        maybeSubscribeToEnergyGuardHeartbeat();
    }

    /**
     * Resolves {@code partner}'s {@code communicationAddress} to its paired {@code eebus:oh-entity}
     * handler and, on success, pushes the currently known status onto it (the same immediate-push
     * behavior {@link #onEnergyGuardFound} always had - see docs/ADR/022-controllable-system-
     * failsafe-status-channel-and-startup-sync.md). On failure, retries with backoff instead of
     * giving up permanently - see docs/ADR/030-energy-guard-resolution-retry-and-byhost-
     * disambiguation.md.
     * <p>
     * Needed because SPINE's own {@code UseCasePartner} discovery notification that triggers
     * {@link #onEnergyGuardFound} is not reliably re-delivered once this Bridge's own
     * {@link EEBusMdnsBrowser} catches up - found live 2026-08-27 (see project memory):
     * {@code EEBusHandler#ohEntityHandlerForCommunicationAddress} failed because the browser had
     * not yet resolved the peer's mDNS record at the moment discovery fired, several seconds
     * before it actually did. So this method, not SPINE, is what has to retry.
     * </p>
     *
     * @param partner the discovered partner, captured once by {@link #onEnergyGuardFound} -
     *            {@code communicationAddress} does not change between retries, only whether the
     *            resolver can already answer for it
     * @param attempt 0 for the first (immediate, synchronous) attempt, incremented for each
     *            scheduled retry - indexes {@link #ENERGY_GUARD_RESOLUTION_RETRY_DELAYS_SECONDS}
     */
    private void resolveEnergyGuardOhEntityHandler(UseCasePartner partner, int attempt) {
        if (!partner.getCommunicationAddress().equals(this.latestEnergyGuardCommunicationAddress)) {
            // A newer onEnergyGuardFound call superseded this one while a retry was pending -
            // stop retrying for a partner we no longer believe is current, rather than risking a
            // stale resolution overwriting a newer (possibly already-successful) one.
            logger.debug(
                    "{}: Energy Guard partner at {} was superseded by a newer discovery notification - "
                            + "dropping this pending resolution (attempt {})",
                    getShortCode(), partner.getCommunicationAddress(), attempt);
            return;
        }
        Optional<EEBusOhEntityHandler> resolved = ohEntityHandlerResolver.apply(partner.getCommunicationAddress());
        if (resolved.isPresent()) {
            EEBusOhEntityHandler resolvedHandler = resolved.get();
            this.energyGuardOhEntityHandler = resolvedHandler;
            logger.debug("{}: Energy Guard partner at {} resolved to {} (attempt {})", getShortCode(),
                    partner.getCommunicationAddress(), resolvedHandler.getThing().getUID(), attempt);
            // Push the currently known status immediately, rather than waiting for the next Heartbeat-
            // timeout transition or Energy Guard write - closes the same startup/reconnect staleness gap
            // as the setup() publish above, for the case where this peer resolves after status was
            // already known. See docs/ADR/022-controllable-system-failsafe-status-channel-and-startup-
            // sync.md.
            EEBusLimitControlStateMachine machine = this.stateMachine;
            if (machine != null) {
                onStateChanged(machine.getState());
            }
            publishFailsafeStatus();
            return;
        }
        if (attempt >= ENERGY_GUARD_RESOLUTION_RETRY_DELAYS_SECONDS.length) {
            logger.debug(
                    "{}: Energy Guard partner at {} could not be resolved to a paired eebus:oh-entity handler "
                            + "after {} attempts - its limit-active/limit-value Channel will not be updated",
                    getShortCode(), partner.getCommunicationAddress(), attempt + 1);
            return;
        }
        int delaySeconds = ENERGY_GUARD_RESOLUTION_RETRY_DELAYS_SECONDS[attempt];
        logger.debug(
                "{}: Energy Guard partner at {} could not be resolved to a paired eebus:oh-entity handler yet "
                        + "(attempt {}) - retrying in {} s",
                getShortCode(), partner.getCommunicationAddress(), attempt, delaySeconds);
        try {
            scheduler.schedule(() -> resolveEnergyGuardOhEntityHandler(partner, attempt + 1), delaySeconds,
                    TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            // This use case was close()d (scheduler.shutdownNow()) while a retry was pending -
            // nothing left to update, safe to drop silently.
        }
    }

    /**
     * Rearms {@link EEBusLimitControlStateMachine}'s Heartbeat watchdog and fires the paired
     * Energy Guard's {@code heartbeat} trigger Channel (docs/ADR/045-controllable-system-
     * heartbeat-channel.md) - one call per Heartbeat notification received from that peer's
     * {@code DeviceDiagnosis} feature.
     *
     * @param notification the raw SPINE notification, carrying the
     *            {@code DeviceDiagnosisHeartbeatData} payload (heartbeatCounter/timestamp) this
     *            method forwards to {@link EEBusOhEntityHandler#triggerHeartbeat} - previously
     *            ignored entirely
     */
    private void onHeartbeatNotification(RequestResult notification) {
        EEBusLimitControlStateMachine machine = this.stateMachine;
        if (machine != null) {
            machine.onHeartbeatReceived();
        }
        EEBusOhEntityHandler peerHandler = this.energyGuardOhEntityHandler;
        if (peerHandler != null) {
            DeviceDiagnosisHeartbeatDataType heartbeatData = notification.getCmd().getDeviceDiagnosisHeartbeatData();
            peerHandler.triggerHeartbeat(getShortCode(),
                    heartbeatData != null ? heartbeatData.getHeartbeatCounter() : null);
        }
    }

    private void onStateChanged(EEBusLimitControlState newState) {
        // Shared by the SPINE-feature publish and the paired oh-entity's dynamic Channel
        // (ADR-021) inside publishLimitState() - both must agree on the same active/value pair
        // for a given state transition.
        boolean active = newState == EEBusLimitControlState.LIMITED;
        publishLimitState(active, lastWrittenLimitValue, lastWrittenLimitDurationSeconds);

        Optional<Metadata> metadata = metadataService.find(ohServiceId, getShortCode(), DATA_POINT_STATE);
        if (metadata.isPresent()) {
            metadataService.updateState(EEBusMetadataService.itemNameOf(metadata.get()),
                    new StringType(newState.name()));
        }

        // ADR-046: the state machine itself was previously only observable indirectly (via the
        // metadata Item above, which requires tagging first, or inferred from limit-active/
        // failsafe-* Channels) - mirror it directly onto the paired oh-entity's Channel too, same
        // resolved-peer tolerance as publishLimitState above.
        EEBusOhEntityHandler stateChannelPeerHandler = this.energyGuardOhEntityHandler;
        if (stateChannelPeerHandler != null) {
            stateChannelPeerHandler.applyLimitControlState(getShortCode(), newState);
        }
    }

    /**
     * Publishes the given active/value/duration triple into the locally exposed SPINE {@code
     * LoadControlLimitListData} entry and mirrors it onto the paired Energy Guard's {@code
     * eebus:oh-entity} dynamic Channel (ADR-021). Extracted so both {@link #onStateChanged}
     * (every FSM transition) and {@link #onLimitWritten} (a value-only write that leaves the FSM
     * state unchanged - see docs/ADR/032-republish-limitvalue-only-writes.md) publish through the
     * same logic instead of drifting apart.
     *
     * @param active the {@code isLimitActive} value to publish
     * @param value the {@code value} to publish, or {@code null} if none has been written yet
     *            (published as {@code 0} W, matching the pre-ADR-032 default)
     * @param durationSeconds the {@code timePeriod.endTime} to publish, in seconds, or
     *            {@code null} if the limit currently has no duration of validity - see
     *            docs/ADR/033-lpc-lpp-limit-duration-channel.md
     */
    private void publishLimitState(boolean active, @Nullable ScaledNumberType value, @Nullable Long durationSeconds) {
        LimitListDataFunction function = this.limitListDataFunction;
        if (function != null) {
            // LPC-009: the CS SHALL report the limit as activated/deactivated according to its
            // own state (only "activated" while LIMITED), independently of what was last written.
            // Must publish to OUR OWN index, never a hardcoded one - LPC and LPP share this function's
            // list (class javadoc), and updateData(idx, ...) is a literal list position, not a limitId
            // lookup (ADR-019 Defect 2: a hardcoded 0 here used to let one direction silently overwrite
            // the other direction's peer-visible entry on every state change).
            int idx = this.limitDataIndex;
            if (idx < 0) {
                logger.warn("Cannot publish {} limit state (active={}) - list index not yet assigned", getShortCode(),
                        active);
            } else {
                try {
                    function.updateData(idx, new LoadControlLimitDataType().withLimitId(getLimitId())
                            .withIsLimitChangeable(true).withIsLimitActive(active)
                            .withValue(value != null ? value : new ScaledNumberWrapper(0.0).toXsdType())
                            // [LPC-004]/ADR-033: SHALL be set if the limit has a duration of
                            // validity (greater than zero seconds), SHALL be absent otherwise -
                            // never publish an empty TimePeriodType.
                            .withTimePeriod(durationSeconds != null
                                    ? new TimePeriodType().withEndTime(secondsToDuration(durationSeconds).toString())
                                    : null));
                } catch (DataValidationException e) {
                    logger.warn("Failed to publish {} limit state (active={})", getShortCode(), active, e);
                }
            }
        }

        // ADR-021: mirror the confirmed status onto the paired eebus:oh-entity's dynamic Channel
        // too - the same lpc#limit-active/lpc#limit-value/lpc#limit-duration triple this CS
        // itself received (this in-process mirror is unaffected by docs/ADR/031-remove-
        // energyguard-monitoring-channels.md, which only removed the EnergyGuard's own SPINE
        // subscribe-and-populate-its-own-Channel path, a different mechanism). Gives a per-peer
        // confirmation that this specific Energy Guard's write was actually received and applied,
        // without requiring the user to tag an Item first.
        EEBusOhEntityHandler peerHandler = this.energyGuardOhEntityHandler;
        if (peerHandler != null) {
            double watts = value != null ? new ScaledNumberWrapper(value).toDouble() : 0.0;
            peerHandler.applyLimitStatus(getShortCode(), active, watts, durationSeconds);
        }
    }

    private static Duration secondsToDuration(long seconds) {
        return DatatypeFactory.newDefaultInstance().newDuration(true, null, null, null, null, null,
                BigDecimal.valueOf(seconds));
    }

    private static long durationToSeconds(Duration duration) {
        return duration.getTimeInMillis(new Date(0)) / 1000;
    }

    @Override
    public void close() {
        EEBusLimitControlStateMachine machine = this.stateMachine;
        if (machine != null) {
            machine.close();
        }
        scheduler.shutdownNow();
    }
}
