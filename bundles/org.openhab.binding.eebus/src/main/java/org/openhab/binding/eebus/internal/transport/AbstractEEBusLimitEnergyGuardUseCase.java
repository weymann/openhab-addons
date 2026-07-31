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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.xml.datatype.DatatypeFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.types.State;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
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
import org.openmuc.jeebus.spine.utils.features.devicediagnosis.DeviceDiagnosisFeature;
import org.openmuc.jeebus.spine.utils.features.devicediagnosis.HeartbeatDataFunction;
import org.openmuc.jeebus.spine.xsd.v1.CmdType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDescriptionListDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueListDataType;
import org.openmuc.jeebus.spine.xsd.v1.ElectricalConnectionParameterDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.ElectricalConnectionParameterDescriptionListDataType;
import org.openmuc.jeebus.spine.xsd.v1.ElectricalConnectionPermittedValueSetDataType;
import org.openmuc.jeebus.spine.xsd.v1.ElectricalConnectionPermittedValueSetListDataType;
import org.openmuc.jeebus.spine.xsd.v1.EnergyDirectionEnumType;
import org.openmuc.jeebus.spine.xsd.v1.EntityTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FunctionEnumType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitConstraintsListDataType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitDataType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitDescriptionListDataType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitListDataType;
import org.openmuc.jeebus.spine.xsd.v1.RoleType;
import org.openmuc.jeebus.spine.xsd.v1.ScaledNumberType;
import org.openmuc.jeebus.spine.xsd.v1.TimePeriodType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared Client-role ("EnergyGuard" actor) implementation for LPC (Limitation of Power
 * Consumption) and LPP (Limitation of Power Production) - the tagged-Item-driven write path
 * that lets openHAB (acting as EnergyGuard) command a real peer's LoadControl limit.
 * {@link EEBusLpcClientUseCase}/{@link EEBusLppClientUseCase} are thin subclasses supplying only
 * {@link #getShortCode()}/{@link #getUseCaseName()}/{@link #getLimitDirection()}, mirroring the
 * existing {@code AbstractEEBusLimitControllableSystemUseCase} + {@code
 * EEBusLpcServerUseCase}/{@code EEBusLppServerUseCase} split.
 *
 * <p>
 * <strong>No monitoring Channel on this (EnergyGuard/Client) side</strong>
 * (docs/ADR/031-remove-energyguard-monitoring-channels.md, revising
 * docs/ADR/015-lpc-lpp-client-role-channels.md and
 * docs/ADR/026-oh-eg-entity-static-channels.md): this class only ever <em>writes</em> a limit,
 * sourced from a tagged Item (see {@link #registerWriteListenersOnce}) - it does not read the
 * peer's LoadControl status back into a Channel. That value is already fully owned by the
 * tagged Item that drives the write; mirroring it back onto a read-only Channel on the same
 * Thing would just duplicate data openHAB already has. The Server-role (ControllableSystem)
 * side is the mirror image and legitimately keeps its Channels:
 * {@link AbstractEEBusLimitControllableSystemUseCase} <em>receives</em> a write from a remote
 * peer it has no local Item for, so the Channel is the only place to see that value (see its
 * own class javadoc, docs/ADR/021-controllable-system-limit-status-channel.md/
 * docs/ADR/022-controllable-system-failsafe-status-channel-and-startup-sync.md). An earlier
 * version of this class also subscribed to and displayed the peer's own reported status via a
 * Channel (docs/ADR/014-dynamic-client-role-channels.md's mechanism, applied here by ADR-015
 * and later statically declared on {@code eebus:oh-eg-entity} by ADR-026/ADR-027) - ADR-031
 * removes that read/subscribe path entirely as unnecessary scope; the write path below is
 * unaffected and unchanged.
 * </p>
 *
 * <p>
 * <strong>limitId resolution</strong> (docs/ADR/018-lpc-lpp-limitid-assignment.md): a peer's
 * {@code limitId} is not guaranteed to follow this binding's own Server-role convention (LPC=0,
 * LPP=1, see {@link AbstractEEBusLimitControllableSystemUseCase#getLimitId()}), and the SPINE
 * spec ({@code EEBus_SPINE_TS_LoadControl.xsd}'s
 * {@code LoadControlLimitDescriptionListDataSelectorsType}) explicitly supports disambiguating
 * limit entries by {@code limitDirection} instead. {@link #resolveLimitId} therefore reads the
 * peer's {@code loadControlLimitDescriptionListData} once per partner and matches the entry
 * whose {@code limitDirection} equals {@link #getLimitDirection()} - structurally identical to
 * {@link EEBusMpcClientUseCase#resolvePowerMeasurementId}'s read-then-filter pattern for
 * {@code measurementId}. Unlike that method, there is <strong>no fallback to a sole
 * entry</strong>: if the read fails, or no entry matches this use case's direction, the write
 * path is not enabled for that peer - guessing an ID here is exactly the failure mode this ADR
 * closes (before it, both LPC and LPP silently assumed {@code limitId=0}, so toggling one
 * direction's write-path Item also flipped the other direction's write on the same peer).
 * </p>
 *
 * <p>
 * <strong>Wire-format actor strings</strong> ({@code "EnergyGuard"}/{@code "ControllableSystem"})
 * reused, not re-derived, from {@link AbstractEEBusLimitControllableSystemUseCase}'s own javadoc
 * (which cites {@code EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf}).
 * </p>
 *
 * <p>
 * <strong>Fan-out write path</strong> (docs/ADR/047-fanout-energyguard-writes-to-all-partners.md):
 * every found {@link UseCasePartner} is bound and limitId-resolved individually (a peer's
 * {@code limitId} is not guaranteed to match another peer's - see "limitId resolution" below),
 * but the tagged Item lookup happens exactly once, against this Bridge's single
 * {@code eebus:oh-eg-entity} child Thing (the {@code writeSourceHandlerResolver} passed into the
 * constructor - resolved by Thing type, not by SKI). Every Item change is then fanned out
 * identically (same {@code isLimitActive}/{@code value}/{@code timePeriod}) to <em>every</em>
 * partner currently in {@link #partnerBindings}, each with its own resolved {@code limitId} -
 * {@link #sendLimitWriteToAllPartners}. The older {@code ohEntityHandlerResolver} (identical
 * pattern to {@link EEBusMpcClientUseCase}, resolving a partner's {@code communicationAddress} to
 * its paired per-SKI {@code eebus:oh-entity} Thing) is kept only for
 * {@link EEBusOhEntityHandler#recordDetectedUseCase} - backward compatible with installations
 * that still configure one {@code eebus:oh-eg-entity} Thing per SKI, per ADR-047's migration
 * note - and is no longer on the write path itself.
 * </p>
 *
 * <p>
 * <strong>Scenario 3 (Heartbeat):</strong> mutual, per the same primary-source quote cited on
 * {@link AbstractEEBusLimitControllableSystemUseCase}'s own class javadoc: "in the LPC Use Case,
 * the 'EnergyGuard' (Client Actor) hosts a Server Feature to provide its own heartbeat to the
 * 'ControllableSystem' (Server Actor)". {@link #setupDeviceDiagnosis} exposes exactly that local
 * {@code DeviceDiagnosis} Server feature - {@link HeartbeatDataFunction#startHeartbeat()} is
 * self-perpetuating once called (verified in its source, identical to the Controllable-System
 * side), so no polling loop is needed here either (docs/ADR/035-energy-guard-outgoing-
 * heartbeat.md). Unlike the Controllable-System side, this class does not watch the peer's own
 * incoming Heartbeat - there is no {@code EEBusLimitControlStateMachine}-style watchdog on the
 * Client/EnergyGuard actor, since the write path below is not gated by the peer's Heartbeat
 * status; only the outgoing half of the mutual requirement was missing, and only the outgoing
 * half is added here.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@AllowedEntityTypes({ EntityTypeEnumType.CEM, EntityTypeEnumType.GRID_GUARD })
public abstract class AbstractEEBusLimitEnergyGuardUseCase implements UseCase {

    private static final String ACTOR_ENERGY_GUARD = "EnergyGuard";
    private static final String ACTOR_CONTROLLABLE_SYSTEM = "ControllableSystem";

    /**
     * Data point names for the Client-role write path (CONCEPT.md, tag-syntax design session
     * 2026-08-21) - matched via {@link EEBusMetadataService#findByTag}, e.g. Item tag
     * {@code "eebus:oh-entity:150d06a965:e554d8b410:LPC:limitActive"}. Both are optional and
     * independent: either can be tagged alone, in which case the other half of the write always
     * uses the default noted on {@link #toActive}/{@link #toWatts}.
     */
    private static final String DATA_POINT_LIMIT_ACTIVE = "limitActive";
    private static final String DATA_POINT_LIMIT_VALUE = "limitValue";
    /**
     * Optional third write-path data point (docs/ADR/033-lpc-lpp-limit-duration-channel.md) -
     * lets an EnergyGuard specify {@code timePeriod.endTime} (a relative duration, per
     * LimitationOfPowerConsumption TS V1.0.0 §3.1.8.2/[LPC-004]) alongside {@code isLimitActive}/
     * {@code value}. Untagged (the default) means every write omits {@code timePeriod} entirely,
     * i.e. an unbounded limit - unchanged behavior from before this constant existed.
     */
    private static final String DATA_POINT_LIMIT_DURATION = "limitDuration";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver;
    private final Supplier<Optional<EEBusOhEntityHandler>> writeSourceHandlerResolver;
    private final EEBusMetadataService metadataService;

    /**
     * Unregister actions for every {@link EEBusMetadataService#registerItemStateListener} call
     * made by {@link #registerWriteListenersOnce}, run by {@link #close()} - mirrors the
     * unregister-on-close contract documented on
     * {@link EEBusMetadataService#unregisterItemStateListener}.
     */
    private final List<Runnable> writeListenerCleanup = new CopyOnWriteArrayList<>();

    /**
     * Communication addresses ({@link UseCasePartner#getCommunicationAddress()}) already fully
     * processed by {@link #onUseCasePartnersFound} (i.e. this Bridge's {@code eebus:oh-eg-entity}
     * write-source Thing was found and the partner handed to
     * {@link #resolveLimitIdAndRegisterPartnerBinding}).
     *
     * <p>
     * <strong>2026-08-21 fix</strong>: {@code onUseCasePartnersFound}'s own javadoc precedent in
     * {@link EEBusMpcClientUseCase} documents that jeebus.spine "calls
     * {@code onUseCasePartnersFound(List)} whenever the set of matching peers changes" - i.e. it
     * is not one-shot, and re-invocations pass the current full partner list again, including
     * partners already seen. Before this fix, every invocation unconditionally re-ran the
     * subscribe/write-listener setup (a brand new, independent
     * {@code NodeManagement#requestSubscription}, and a brand new
     * {@link EEBusMetadataService#registerItemStateListener} on top of any still-registered from
     * a previous invocation, since {@link #writeListenerCleanup} is only drained by
     * {@link #close()}) for every partner in the list, already-seen or not. Observed live as
     * N-fold duplicate {@code applyLimitStatus} log lines for a single real SPINE notification
     * (that status-read/subscribe path was later removed entirely, ADR-031 - the observation
     * still motivates this guard, since the same unguarded re-registration would equally
     * multiply outbound SPINE limit writes per single Item toggle, one per duplicate listener,
     * each independently calling {@link #sendLimitWrite}). This set makes partner processing
     * idempotent: a partner already
     * subscribed is skipped on subsequent notifications instead of being re-subscribed. A
     * partner that failed to resolve (see the {@code writeSourceHandler.isEmpty()} branch below) is
     * deliberately <em>not</em> added here, so a later notification - e.g. once mDNS has since
     * discovered it - can still succeed.
     * </p>
     */
    private final Set<String> subscribedPartners = ConcurrentHashMap.newKeySet();

    /**
     * A single partner's resolved write target (docs/ADR/047-fanout-energyguard-writes-to-all-
     * partners.md): {@code featureAddress} is the peer's {@code LoadControl} feature,
     * {@code limitId} is <em>this</em> use case's resolved id for that peer specifically - two
     * peers are not guaranteed to share the same {@code limitId} even though they always receive
     * the same {@code isLimitActive}/{@code value}/{@code timePeriod} (see class javadoc "limitId
     * resolution").
     */
    private record PartnerBinding(FeatureAddressType featureAddress, long limitId) {
    }

    /**
     * Every currently bound partner's {@link PartnerBinding}, keyed by
     * {@link UseCasePartner#getCommunicationAddress()} - populated by
     * {@link #resolveLimitIdAndRegisterPartnerBinding}, drained by {@link #close()}, and consumed
     * by {@link #sendLimitWriteToAllPartners} on every write-source Item change. See docs/ADR/047:
     * a partner that disappears (e.g. its Bridge goes offline) is not yet actively removed from
     * this map - out of scope for this ADR, tracked as a follow-up.
     */
    private final Map<String, PartnerBinding> partnerBindings = new ConcurrentHashMap<>();

    /**
     * Guards {@link #registerWriteListenersOnce} so the write-source Thing's tagged Items are
     * subscribed exactly once, no matter how many partners are found (docs/ADR/047) - an
     * {@link AtomicBoolean} rather than a plain flag since partner resolution completes on
     * asynchronous SPINE callback threads, potentially concurrently for more than one partner.
     */
    private final AtomicBoolean writeListenersRegistered = new AtomicBoolean();

    @Inject
    private @Nullable Entity entity;

    private @Nullable FeatureAddressType address;
    private @Nullable Device device;

    /**
     * @param ohEntityHandlerResolver resolves a SPINE {@code communicationAddress} to the paired
     *            {@code eebus:oh-entity} Thing's {@link EEBusOhEntityHandler}, if any - used only
     *            for {@link EEBusOhEntityHandler#recordDetectedUseCase} (backward compatibility
     *            with pre-ADR-047 per-SKI Things), no longer required for the write path itself -
     *            see class javadoc "Fan-out write path" and docs/ADR/047-fanout-energyguard-
     *            writes-to-all-partners.md.
     * @param writeSourceHandlerResolver resolves this Bridge's single {@code eebus:oh-eg-entity}
     *            child Thing, independent of any partner's SKI (docs/ADR/047) - its tagged Items
     *            are the one source of truth fanned out to every partner in
     *            {@link #partnerBindings}.
     * @param metadataService resolves Client-role write-path Item tags to Items and bridges
     *            their state changes to this use case - see {@link #registerWriteListenersOnce}.
     */
    protected AbstractEEBusLimitEnergyGuardUseCase(
            Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver,
            Supplier<Optional<EEBusOhEntityHandler>> writeSourceHandlerResolver, EEBusMetadataService metadataService) {
        this.ohEntityHandlerResolver = ohEntityHandlerResolver;
        this.writeSourceHandlerResolver = writeSourceHandlerResolver;
        this.metadataService = metadataService;
    }

    /**
     * @return the Channel Group ID this use case populates, e.g. {@code "lpc"}/{@code "lpp"} -
     *         doubles as the use-case-detection Thing property key
     *         ({@link EEBusOhEntityHandler#recordDetectedUseCase}), since
     *         {@link org.openhab.binding.eebus.internal.EEBusBindingConstants#CHANNEL_GROUP_LPC}/
     *         {@code USE_CASE_KEY_LPC} (and the LPP equivalents) are the same string by
     *         convention.
     */
    protected abstract String getShortCode();

    /** @return the SPINE use case name, e.g. {@code "limitationOfPowerConsumption"}. */
    protected abstract String getUseCaseName();

    /**
     * @return the SPINE {@link EnergyDirectionEnumType} this use case reads/writes -
     *         {@code CONSUME} for LPC, {@code PRODUCE} for LPP. Used by {@link #resolveLimitId}
     *         to pick this use case's entry out of the peer's
     *         {@code loadControlLimitDescriptionListData} - see class javadoc "limitId
     *         resolution" and docs/ADR/018-lpc-lpp-limitid-assignment.md.
     */
    protected abstract EnergyDirectionEnumType getLimitDirection();

    @Override
    public String getActor() {
        return ACTOR_ENERGY_GUARD;
    }

    @Override
    public String getName() {
        return getUseCaseName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    // UseCase (jeebus.spine) is an unannotated legacy interface - see the identical note on
    // EEBusMpcClientUseCase#getScenarioSupport().
    //
    // Must include Scenario 3 (Heartbeat) alongside Scenario 1 (the actual limit-setting
    // scenario), even though this method's own name suggests only the "main" scenario belongs
    // here: this actor always hosts a live outgoing Heartbeat (DeviceDiagnosis SERVER +
    // HeartbeatDataFunction, see getFeatureRequirements() below and ADR-035), but a peer is only
    // expected to consume a Scenario the use case's own NodeManagement.UseCaseData declares as
    // supported (Table 12 of the LPC/LPP TS marks the "read" of deviceDiagnosisHeartbeatData as
    // the only possible operation - the peer polls it, we don't push it - so if the peer never
    // sees Scenario 3 advertised here, it has no reason to ever poll). Confirmed root cause,
    // 2026-09-03, via a real Hager Energy S10: declaring only [1L] meant our own
    // nodeManagementUseCaseData reply told the S10 we do not support Scenario 3, so it correctly
    // never once read our heartbeat feature in 15+ hours of connection lifetime (verified via
    // full-log grep - zero deviceDiagnosisHeartbeatData reads from the S10, ever), leaving its
    // LPC state machine stuck believing no heartbeat exists and rejecting every limit write with
    // SPINE Error 7 (COMMAND_REJECTED) regardless of connection age. See
    // docs/ADR/038-energy-guard-declare-heartbeat-scenario-support.md.
    @Override
    @NonNullByDefault({})
    public List<Long> getScenarioSupport() {
        return List.of(1L, 3L);
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
        // Same reasoning as EEBusMpcClientUseCase: no local LoadControl feature is needed since
        // NodeManagement#requestRead/requestSubscription can target any remote feature address
        // directly. DeviceDiagnosis SERVER is different - Scenario 3 requires this actor to also
        // host its own outgoing Heartbeat locally (see class javadoc "Scenario 3 (Heartbeat)"
        // and docs/ADR/035-energy-guard-outgoing-heartbeat.md), mirroring
        // AbstractEEBusLimitControllableSystemUseCase#getFeatureRequirements's identical entry.
        return Set.of(FeatureRequirement.GENERIC_CLIENT, new FeatureRequirement(FeatureTypeEnumType.DEVICE_DIAGNOSIS,
                RoleType.SERVER, HeartbeatDataFunction.class));
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
        setupDeviceDiagnosis(localEntity);

        localEntity.getDevice().getNodeManagement().addUseCaseListener(this::onUseCasePartnersFound, getName(),
                ACTOR_CONTROLLABLE_SYSTEM, Map.of(1L, PresenceIndication.MANDATORY),
                Set.of(new CommunicationPartnerFeatureRequirement(FeatureTypeEnumType.LOAD_CONTROL,
                        Map.of(FunctionEnumType.LOAD_CONTROL_LIMIT_LIST_DATA, Map.of(1L, PresenceIndication.MANDATORY),
                                FunctionEnumType.LOAD_CONTROL_LIMIT_DESCRIPTION_LIST_DATA,
                                Map.of(1L, PresenceIndication.MANDATORY)))));
    }

    /**
     * Exposes this Entity's own outgoing Heartbeat (Scenario 3, see class javadoc) - the
     * "EnergyGuard" (Client Actor) side of the mutual {@code DeviceDiagnosis} Heartbeat
     * mechanism. Exact mirror of
     * {@link AbstractEEBusLimitControllableSystemUseCase}'s identically named private method
     * (not shared - each Use Case class is otherwise self-contained, per this codebase's
     * existing convention). See docs/ADR/035-energy-guard-outgoing-heartbeat.md, including why
     * sharing the local Entity (CEM or GridGuard, see docs/ADR/042-configurable-entitytype.md
     * and its 2026-09-04 update) with an active Controllable-System-role Use Case is safe
     * (idempotent feature/function get-or-add, self-rescheduling Heartbeat timer).
     */
    private void setupDeviceDiagnosis(Entity localEntity) {
        DeviceDiagnosisFeature deviceDiagnosisFeature = findFeatureWrapper(localEntity,
                FeatureTypeEnumType.DEVICE_DIAGNOSIS, DeviceDiagnosisFeature.class);
        // "at least every 60 seconds" per LPC-005/006 (identical for LPP) - HeartbeatDataFunction
        // self-perpetuates from here on, no polling loop needed (verified in its source).
        deviceDiagnosisFeature.addHeartBeatDataFunction(60);
        deviceDiagnosisFeature.startHeartbeat();
    }

    private Feature findFeature(Entity localEntity, FeatureTypeEnumType type) {
        return localEntity.getFeatures().stream().filter(f -> f.getType() == type && f.getRole() == RoleType.SERVER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        type + " server feature missing - Entity#addUseCase() should have added it from "
                                + "getFeatureRequirements()"));
    }

    /**
     * ADR-044: this Entity's own {@code GENERIC}/{@code CLIENT} feature (required via
     * {@link FeatureRequirement#GENERIC_CLIENT}, see {@link #getFeatureRequirements}) - used as
     * the caller of {@link Feature#requestBind} for the {@code LoadControl}/
     * {@code DeviceConfiguration} binds below, instead of the local {@code NodeManagement}
     * feature, so the resulting bind's {@code clientAddress} carries this Entity's own address
     * (entity=1 for a typical single-entity device) rather than {@code NodeManagement}'s
     * canonical device-level address (entity=0). {@code Feature#requestBind} always routes the
     * request to the peer's {@code NodeManagement} regardless of which local feature it's called
     * on ({@code FeatureImpl#requestBind} looks up the destination from the target
     * {@code address}'s device, not from the caller) - only the embedded {@code clientAddress}
     * identity changes. See eebus-lpc-heartbeat-root-cause-found-2026-09-04.md project memory
     * (device-bridge notes, not part of this repo) for the full chain of evidence: without this,
     * a peer implementing EEBUS LPC Implementation Guideline section 3.8 (subscribe to this
     * Entity's Heartbeat only once it has seen both binds from the *same* Entity) can never see
     * a match, because the two binds' clientAddress used to report entity=0 while the peer's own
     * Heartbeat-partner address correctly reports entity=1 - permanently failing that Entity
     * identity comparison and silently withholding the Heartbeat subscription forever.
     */
    private Feature findGenericClientFeature(Entity localEntity) {
        return localEntity.getFeatures().stream()
                .filter(f -> f.getType() == FeatureTypeEnumType.GENERIC && f.getRole() == RoleType.CLIENT).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "GENERIC client feature missing - Entity#addUseCase() should have added it from "
                                + "getFeatureRequirements()"));
    }

    /**
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

    private void onUseCasePartnersFound(List<UseCasePartner> partners) {
        for (UseCasePartner partner : partners) {
            FeatureAddressType featureAddress = partner.getCompleteFeatureAddress(FeatureTypeEnumType.LOAD_CONTROL);
            if (featureAddress == null) {
                logger.debug("{} partner at {} has no LoadControl feature address, skipping", getShortCode(),
                        partner.getCommunicationAddress());
                continue;
            }
            // docs/ADR/047: the write path no longer requires a per-SKI eebus:oh-entity Thing for
            // this specific partner - it requires this Bridge's single eebus:oh-eg-entity Thing to
            // exist somewhere, regardless of which partner triggered this notification.
            Optional<EEBusOhEntityHandler> writeSourceHandler = writeSourceHandlerResolver.get();
            if (writeSourceHandler.isEmpty()) {
                logger.debug("{} partner at {} found, but this Bridge has no eebus:oh-eg-entity Thing to source the "
                        + "write path from, skipping (docs/ADR/047-fanout-energyguard-writes-to-all-" + "partners.md)",
                        getShortCode(), partner.getCommunicationAddress());
                continue;
            }
            // 2026-08-21 fix: see #subscribedPartners javadoc - onUseCasePartnersFound can fire
            // again for a partner already fully processed; without this guard that re-ran
            // resolveLimitIdAndRegisterPartnerBinding from scratch every time, leaking an extra
            // Item state listener each time.
            if (!subscribedPartners.add(partner.getCommunicationAddress())) {
                logger.debug("{} partner at {} already subscribed, ignoring duplicate use-case-partner notification",
                        getShortCode(), partner.getCommunicationAddress());
                continue;
            }
            // Best-effort only (docs/ADR/047): a per-SKI eebus:oh-entity Thing for this specific
            // partner is no longer required for the write path, but if one is still configured
            // (backward compatible with pre-ADR-047 multi-Thing setups), mark it as having
            // detected this use case, same as before. CONCEPT.md §4.2.2 point 3 /
            // docs/changes/dynamic-client-role-channels/proposal.md "Open Questions": value is the
            // actor role the *peer* plays, not openHAB's own role. getShortCode() doubles as the
            // use-case-key property name - see its javadoc.
            ohEntityHandlerResolver.apply(partner.getCommunicationAddress())
                    .ifPresent(handler -> handler.recordDetectedUseCase(getShortCode(), "server"));
            // 2026-09-02: DeviceConfiguration is unrelated to LoadControl binding and
            // entirely optional - resolved here (not inside
            // resolveLimitIdAndRegisterPartnerBinding) since only UseCasePartner exposes
            // getCompleteFeatureAddress() for an arbitrary feature type. null is expected
            // and handled for peers (e.g. the local simulated CS) that don't expose this
            // feature at all - see logDeviceConfigurationDiagnostic.
            FeatureAddressType deviceConfigFeatureAddress = partner
                    .getCompleteFeatureAddress(FeatureTypeEnumType.DEVICE_CONFIGURATION);
            // 2026-09-02: same reasoning as deviceConfigFeatureAddress above, for the
            // "ElectricalConnection gating characteristic" theory - see
            // logElectricalConnectionDiagnostic.
            FeatureAddressType electricalConnectionFeatureAddress = partner
                    .getCompleteFeatureAddress(FeatureTypeEnumType.ELECTRICAL_CONNECTION);
            resolveLimitIdAndRegisterPartnerBinding(featureAddress, deviceConfigFeatureAddress,
                    electricalConnectionFeatureAddress, partner.getCommunicationAddress(), writeSourceHandler.get());
        }
    }

    /**
     * Binds to the peer's {@code LoadControl} feature (ADR-034 - a real peer, confirmed against
     * a Hager Energy S10, rejects every {@code LoadControlLimitListData} WRITE with
     * {@code BINDING_NECESSARY} until the client has done this), then reads the peer's
     * {@code loadControlLimitDescriptionListData} once, resolves this use case's
     * {@code limitId} via {@link #resolveLimitId}, and - only on success - registers this
     * partner's {@link PartnerBinding} and ensures the write-source Item listeners are active
     * ({@link #registerWriteListenersOnce}, docs/ADR/047-fanout-energyguard-writes-to-all-
     * partners.md). See class javadoc "limitId resolution" and docs/ADR/018-lpc-lpp-limitid-
     * assignment.md: on failure (bind failure, read error, or no entry matching
     * {@link #getLimitDirection()}), this peer is simply never added to
     * {@link #partnerBindings} - the fan-out on the next Item change goes to every other
     * already-bound partner as normal, rather than guessing an ID or writing unbound.
     *
     * <p>
     * Also fires (fire-and-forget, {@link #bindDeviceConfiguration}) a binding request against
     * the peer's {@code DeviceConfiguration} feature, if it has one - see ADR-039. Per section
     * 3.8 of the EEBUS LPC Implementation Guideline, a Controllable System withholds its
     * DeviceDiagnosis (Heartbeat) subscription until it has seen binding requests for
     * <strong>both</strong> {@code LoadControl} and {@code DeviceConfiguration} from the same
     * Entity; without the latter, a peer that follows this procedure never subscribes to our
     * Heartbeat and therefore never leaves its "failsafe state" (Error 7 / COMMAND_REJECTED on
     * every limit write) - confirmed against a real Hager Energy S10.
     *
     * @param featureAddress the peer's {@code LoadControl} feature address
     * @param deviceConfigFeatureAddress the peer's {@code DeviceConfiguration} feature
     *            address, or {@code null} if the peer does not expose one - see
     *            {@link #bindDeviceConfiguration} and {@link #logDeviceConfigurationDiagnostic}
     * @param electricalConnectionFeatureAddress the peer's {@code ElectricalConnection}
     *            feature address, or {@code null} if the peer does not expose one - see
     *            {@link #logElectricalConnectionDiagnostic}
     * @param communicationAddress this partner's {@link UseCasePartner#getCommunicationAddress()}
     *            - the {@link #partnerBindings} key, and used for logging now that there is no
     *            per-partner Thing to name instead (docs/ADR/047)
     * @param writeSourceHandler this Bridge's single {@code eebus:oh-eg-entity} Thing's handler
     */
    private void resolveLimitIdAndRegisterPartnerBinding(FeatureAddressType featureAddress,
            @Nullable FeatureAddressType deviceConfigFeatureAddress,
            @Nullable FeatureAddressType electricalConnectionFeatureAddress, String communicationAddress,
            EEBusOhEntityHandler writeSourceHandler) {
        Device localDevice = this.device;
        Entity localEntity = this.entity;
        if (localDevice == null || localEntity == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();
        // ADR-044: see #findGenericClientFeature's javadoc - binds must be issued via this
        // Entity's own GENERIC client feature, not NodeManagement, so their clientAddress
        // correctly identifies this Entity to a peer implementing IG section 3.8.
        Feature genericClientFeature = findGenericClientFeature(localEntity);

        // ADR-039: the DeviceConfiguration bind is independent of the LoadControl bind/write
        // path below (fire-and-forget, does not gate the write path) - it exists solely
        // so a peer following IG section 3.8's "await both bindings before subscribing to
        // Heartbeat" procedure ever sees it.
        bindDeviceConfiguration(deviceConfigFeatureAddress);

        // ADR-034: bind first - this partner is only added to partnerBindings once both the
        // bind and the description read succeed, so a peer that never accepts the binding never
        // gets an unbound write attempt (which it would reject with BINDING_NECESSARY anyway).
        // ADR-044: bound via genericClientFeature, not nodeManagement - see #findGenericClientFeature.
        genericClientFeature.requestBind(featureAddress, FeatureTypeEnumType.LOAD_CONTROL).thenCompose(bindResult -> {
            CmdType descriptionReadCmd = new CmdType()
                    .withLoadControlLimitDescriptionListData(new LoadControlLimitDescriptionListDataType());
            return nodeManagement.requestRead(featureAddress, descriptionReadCmd);
        }).thenAccept(result -> {
            Optional<Long> limitId = resolveLimitId(result);
            if (limitId.isEmpty()) {
                logger.debug(
                        "{} partner's LoadControlLimitDescriptionListData has no entry for limitDirection "
                                + "'{}', not enabling the write path for partner '{}'",
                        getShortCode(), getLimitDirection().value(), communicationAddress);
                return;
            }
            long id = limitId.get();
            partnerBindings.put(communicationAddress, new PartnerBinding(featureAddress, id));
            registerWriteListenersOnce(writeSourceHandler);
            // 2026-09-02: diagnostic-only reads while chasing a real Hager Energy S10's
            // COMMAND_REJECTED (Error 7) response to writes on this limitId - see
            // eebus-lpc-command-rejected-2026-09-02.md project memory (device-bridge notes,
            // not part of this repo). Fire-and-forget, logged only: neither call gates the
            // write path above, since a peer that does not support one of these optional
            // reads must not be prevented from working otherwise.
            logLimitChangeableDiagnostic(featureAddress, id);
            logLimitConstraintsDiagnostic(featureAddress, id);
            logDeviceConfigurationDiagnostic(deviceConfigFeatureAddress, id);
            logElectricalConnectionDiagnostic(electricalConnectionFeatureAddress, id);
        }).exceptionally(ex -> {
            logger.warn(
                    "Failed to bind to peer's LoadControl feature or read its LoadControlLimitDescriptionListData "
                            + "for {} partner '{}', not enabling the write path",
                    getShortCode(), communicationAddress, ex);
            return null;
        });
    }

    /**
     * @return the {@code limitId} whose {@code limitDirection} matches
     *         {@link #getLimitDirection()} - {@link Optional#empty()} if the read failed to
     *         return a description list, or no entry matches, in which case the caller does
     *         <strong>not</strong> fall back to a sole remaining entry (see class javadoc
     *         "limitId resolution").
     */
    private Optional<Long> resolveLimitId(RequestResult result) {
        LoadControlLimitDescriptionListDataType descriptionList = result.getCmd()
                .getLoadControlLimitDescriptionListData();
        if (descriptionList == null) {
            return Optional.empty();
        }
        String direction = getLimitDirection().value();
        return descriptionList.getLoadControlLimitDescriptionData().stream()
                .filter(d -> direction.equals(d.getLimitDirection()))
                .map(LoadControlLimitDescriptionDataType::getLimitId).findFirst();
    }

    /**
     * Diagnostic-only: reads the peer's live {@code LoadControlLimitListData} - distinct from
     * the static {@code LoadControlLimitDescriptionListData} {@link #resolveLimitId} reads;
     * only the live list carries {@code isLimitChangeable} - and logs whether
     * {@code resolvedLimitId} is changeable. Added 2026-09-02 while diagnosing a real Hager
     * Energy S10's {@code COMMAND_REJECTED} (Error 7) response to writes: {@code jeebus.spine}'s
     * own {@code LimitListDataFunction#validateUpdateForMatchingData} rejects a write with
     * exactly this error when a matching entry has {@code isLimitChangeable == false}. Logged
     * only - never gates {@link #registerWriteListenersOnce}, since a peer's live-data read failing
     * (e.g. not yet populated) must not disable the write path this diagnostic investigates.
     *
     * @param featureAddress the peer's {@code LoadControl} feature address
     * @param resolvedLimitId this use case's resolved {@code limitId}, from
     *            {@link #resolveLimitId}
     */
    private void logLimitChangeableDiagnostic(FeatureAddressType featureAddress, long resolvedLimitId) {
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();
        CmdType readCmd = new CmdType().withLoadControlLimitListData(new LoadControlLimitListDataType());
        nodeManagement.requestRead(featureAddress, readCmd).thenAccept(result -> {
            LoadControlLimitListDataType limitList = result.getCmd().getLoadControlLimitListData();
            if (limitList == null) {
                logger.debug("{} partner's LoadControlLimitListData read for limitId={} returned no data",
                        getShortCode(), resolvedLimitId);
                return;
            }
            limitList.getLoadControlLimitData().stream().filter(d -> Objects.equals(d.getLimitId(), resolvedLimitId))
                    .findFirst().ifPresentOrElse(
                            d -> logger.debug(
                                    "{} partner's LoadControlLimitListData for limitId={}: isLimitChangeable={}, "
                                            + "isLimitActive={}, value={}",
                                    getShortCode(), resolvedLimitId, d.getIsLimitChangeable(), d.getIsLimitActive(),
                                    d.getValue()),
                            () -> logger.debug("{} partner's LoadControlLimitListData has no entry for limitId={}",
                                    getShortCode(), resolvedLimitId));
        }).exceptionally(ex -> {
            logger.debug("{} diagnostic read of partner's LoadControlLimitListData for limitId={} failed: {}",
                    getShortCode(), resolvedLimitId, summarize(ex));
            return null;
        });
    }

    /**
     * Diagnostic-only: reads the peer's optional {@code LoadControlLimitConstraintsListData} and
     * logs {@code valueRangeMin}/{@code valueRangeMax} for {@code resolvedLimitId} - the second
     * hypothesis (alongside {@link #logLimitChangeableDiagnostic}) for a real Hager Energy S10's
     * {@code COMMAND_REJECTED} (Error 7) response: {@code jeebus.spine}'s
     * {@code LimitConstraintsDataFunction#validateLimitFitsConstraints} rejects a write whose
     * {@code value} falls outside this range. This function is optional per the LPC/LPP use
     * cases, so a peer not supporting it (empty result or read error) is expected and logged at
     * debug only - never gates {@link #registerWriteListenersOnce}.
     *
     * @param featureAddress the peer's {@code LoadControl} feature address
     * @param resolvedLimitId this use case's resolved {@code limitId}, from
     *            {@link #resolveLimitId}
     */
    private void logLimitConstraintsDiagnostic(FeatureAddressType featureAddress, long resolvedLimitId) {
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();
        CmdType readCmd = new CmdType()
                .withLoadControlLimitConstraintsListData(new LoadControlLimitConstraintsListDataType());
        nodeManagement.requestRead(featureAddress, readCmd).thenAccept(result -> {
            LoadControlLimitConstraintsListDataType constraintsList = result.getCmd()
                    .getLoadControlLimitConstraintsListData();
            if (constraintsList == null) {
                logger.debug("{} partner's LoadControlLimitConstraintsListData read for limitId={} returned no data "
                        + "(peer may not support this optional function)", getShortCode(), resolvedLimitId);
                return;
            }
            constraintsList.getLoadControlLimitConstraintsData().stream()
                    .filter(d -> Objects.equals(d.getLimitId(), resolvedLimitId)).findFirst()
                    .ifPresentOrElse(
                            d -> logger.debug("{} partner's LoadControlLimitConstraintsListData for limitId={}: "
                                    + "valueRangeMin={} W, valueRangeMax={} W", getShortCode(), resolvedLimitId,
                                    toWattsOrNull(d.getValueRangeMin()), toWattsOrNull(d.getValueRangeMax())),
                            () -> logger.debug(
                                    "{} partner's LoadControlLimitConstraintsListData has no entry for limitId={}",
                                    getShortCode(), resolvedLimitId));
        }).exceptionally(ex -> {
            logger.debug(
                    "{} diagnostic read of partner's LoadControlLimitConstraintsListData for limitId={} failed "
                            + "(peer may not support this optional function): {}",
                    getShortCode(), resolvedLimitId, summarize(ex));
            return null;
        });
    }

    /**
     * @param scaledNumber a SPINE {@code ScaledNumberType}, or {@code null}
     * @return the value converted to a plain double via {@link ScaledNumberWrapper}, or
     *         {@code null} if {@code scaledNumber} is {@code null} - used only by
     *         {@link #logLimitConstraintsDiagnostic} to log human-readable Watt values instead
     *         of the raw {@code {number, scale}} pair
     */
    private static @Nullable Double toWattsOrNull(@Nullable ScaledNumberType scaledNumber) {
        return scaledNumber == null ? null : new ScaledNumberWrapper(scaledNumber).toDouble();
    }

    /**
     * ADR-039: sends a binding request to the peer's {@code DeviceConfiguration} feature, if it
     * exposes one. Fire-and-forget - logged only, never gates {@link #registerWriteListenersOnce}
     * or anything else in this class, since we do not (yet) write failsafe values through this
     * binding (see the open "document Scenario 2/4" follow-up in project memory). The sole
     * purpose of this call is to satisfy section 3.8 of the EEBUS LPC Implementation Guideline:
     * a Controllable System SHALL wait for binding requests on <strong>both</strong>
     * {@code LoadControl} and {@code DeviceConfiguration} from the same Entity before it sends
     * its DeviceDiagnosis (Heartbeat) subscription request. Confirmed against a real Hager
     * Energy S10: without this bind, the peer never subscribed to our Heartbeat in six separate
     * connection attempts across two days, stayed in "failsafe state", and rejected every
     * {@code LoadControlLimitListData} write with {@code COMMAND_REJECTED} (Error 7) - see
     * eebus-lpc-command-rejected-2026-09-02.md project memory (device-bridge notes, not part of
     * this repo).
     *
     * @param featureAddress the peer's {@code DeviceConfiguration} feature address, or
     *            {@code null} if the peer does not expose one (e.g. the local simulated CS used
     *            in development, which predates this feature) - skipped and logged at debug
     *            only, matching {@link #logDeviceConfigurationDiagnostic}
     */
    private void bindDeviceConfiguration(@Nullable FeatureAddressType featureAddress) {
        if (featureAddress == null) {
            logger.debug("{} partner has no DeviceConfiguration feature, skipping the ADR-039 binding request "
                    + "(peer will not see a DeviceConfiguration bind and may withhold its Heartbeat subscription "
                    + "per IG section 3.8)", getShortCode());
            return;
        }
        Entity localEntity = this.entity;
        if (localEntity == null) {
            return;
        }
        // ADR-044: bound via this Entity's own GENERIC client feature, not NodeManagement - see
        // #findGenericClientFeature's javadoc.
        Feature genericClientFeature = findGenericClientFeature(localEntity);
        genericClientFeature.requestBind(featureAddress, FeatureTypeEnumType.DEVICE_CONFIGURATION)
                .thenAccept(bindResult -> {
                    logger.debug("{} bound to partner's DeviceConfiguration feature (ADR-039, IG section 3.8 Heartbeat "
                            + "subscription precondition)", getShortCode());
                }).exceptionally(ex -> {
                    logger.debug(
                            "{} failed to bind to partner's DeviceConfiguration feature (peer may not support this "
                                    + "optional binding; per IG section 3.8 it may then withhold its Heartbeat "
                                    + "subscription)",
                            getShortCode(), ex);
                    return null;
                });
    }

    /**
     * Diagnostic-only: reads the peer's optional {@code DeviceConfigurationKeyValue}
     * description and value lists and logs every entry found. Added 2026-09-02 as the third
     * hypothesis (after {@link #logLimitChangeableDiagnostic}/
     * {@link #logLimitConstraintsDiagnostic}, both ruled out against a real Hager Energy S10
     * - see eebus-lpc-command-rejected-2026-09-02.md project memory, device-bridge notes, not
     * part of this repo) for its still-unexplained {@code COMMAND_REJECTED} (Error 7) on
     * {@code LoadControlLimitListData} writes: a vendor-specific lock/enable setting exposed
     * as an ordinary {@code DeviceConfiguration} key (e.g. an "external control
     * allowed"-style toggle) would not show up in any of the standard SPINE LoadControl
     * capability/constraint data this use case already checks. This feature is unrelated to
     * {@code LoadControl} and entirely optional, so a peer without it ({@code featureAddress
     * == null}, from {@link UseCasePartner#getCompleteFeatureAddress}) or one that rejects
     * the read is expected and logged at debug only - never gates
     * {@link #registerWriteListenersOnce}.
     *
     * @param featureAddress the peer's {@code DeviceConfiguration} feature address, or
     *            {@code null} if the peer does not expose one
     * @param resolvedLimitId this use case's resolved {@code limitId}, from
     *            {@link #resolveLimitId} - included in every log line purely for
     *            correlation with the write attempts being investigated, since
     *            {@code DeviceConfiguration} keys are not themselves tied to a limitId
     */
    private void logDeviceConfigurationDiagnostic(@Nullable FeatureAddressType featureAddress, long resolvedLimitId) {
        if (featureAddress == null) {
            logger.debug("{} partner has no DeviceConfiguration feature, skipping vendor-config diagnostic "
                    + "(was investigating limitId={})", getShortCode(), resolvedLimitId);
            return;
        }
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();
        CmdType descriptionReadCmd = new CmdType().withDeviceConfigurationKeyValueDescriptionListData(
                new DeviceConfigurationKeyValueDescriptionListDataType());
        nodeManagement.requestRead(featureAddress, descriptionReadCmd).thenCompose(descriptionResult -> {
            DeviceConfigurationKeyValueDescriptionListDataType descriptionList = descriptionResult.getCmd()
                    .getDeviceConfigurationKeyValueDescriptionListData();
            CmdType valueReadCmd = new CmdType()
                    .withDeviceConfigurationKeyValueListData(new DeviceConfigurationKeyValueListDataType());
            return nodeManagement.requestRead(featureAddress, valueReadCmd)
                    .thenAccept(valueResult -> logDeviceConfigurationEntries(resolvedLimitId, descriptionList,
                            valueResult.getCmd().getDeviceConfigurationKeyValueListData()));
        }).exceptionally(ex -> {
            logger.debug(
                    "{} diagnostic read of partner's DeviceConfigurationKeyValue data failed (peer may not "
                            + "support this optional feature; was investigating limitId={}): {}",
                    getShortCode(), resolvedLimitId, summarize(ex));
            return null;
        });
    }

    /**
     * Logs every entry of a peer's {@code DeviceConfigurationKeyValueListData}, joined against
     * its (also optional, separately-read) {@code DeviceConfigurationKeyValueDescriptionListData}
     * by {@code keyId} for human-readable {@code keyName}/{@code label} context. Split out of
     * {@link #logDeviceConfigurationDiagnostic} only to keep the two chained SPINE reads (each
     * independently absent-or-erroring) readable.
     *
     * @param resolvedLimitId see {@link #logDeviceConfigurationDiagnostic}
     * @param descriptionList the peer's key descriptions, or {@code null} if that read
     *            returned no data - entries are then logged with {@code keyName}/{@code
     *            label} both {@code null} rather than skipped, since the values themselves
     *            are still the point of this diagnostic
     * @param valueList the peer's key values, or {@code null} if that read returned no data
     */
    private void logDeviceConfigurationEntries(long resolvedLimitId,
            @Nullable DeviceConfigurationKeyValueDescriptionListDataType descriptionList,
            @Nullable DeviceConfigurationKeyValueListDataType valueList) {
        if (valueList == null) {
            logger.debug("{} partner's DeviceConfigurationKeyValueListData returned no data (was investigating "
                    + "limitId={})", getShortCode(), resolvedLimitId);
            return;
        }
        List<DeviceConfigurationKeyValueDataType> entries = valueList.getDeviceConfigurationKeyValueData();
        if (entries.isEmpty()) {
            logger.debug("{} partner's DeviceConfigurationKeyValueListData has no entries (was investigating "
                    + "limitId={})", getShortCode(), resolvedLimitId);
            return;
        }
        List<DeviceConfigurationKeyValueDescriptionDataType> descriptions = descriptionList == null ? List.of()
                : descriptionList.getDeviceConfigurationKeyValueDescriptionData();
        for (DeviceConfigurationKeyValueDataType entry : entries) {
            Optional<DeviceConfigurationKeyValueDescriptionDataType> description = descriptions.stream()
                    .filter(d -> Objects.equals(d.getKeyId(), entry.getKeyId())).findFirst();
            logger.debug(
                    "{} partner's DeviceConfiguration keyId={} keyName={} label={} value={} "
                            + "isValueChangeable={} (was investigating limitId={})",
                    getShortCode(), entry.getKeyId(),
                    description.map(DeviceConfigurationKeyValueDescriptionDataType::getKeyName).orElse(null),
                    description.map(DeviceConfigurationKeyValueDescriptionDataType::getLabel).orElse(null),
                    entry.getValue(), entry.getIsValueChangeable(), resolvedLimitId);
        }
        // 2026-09-02: a key described in DeviceConfigurationKeyValueDescriptionListData but
        // absent from DeviceConfigurationKeyValueListData (e.g. a real Hager Energy S10's
        // keyId=4, observed missing from its value-list reply while keyId 0-3/5-8 were
        // present) would otherwise go completely unlogged by the loop above, which only
        // iterates the value list - and a description-only key is exactly the shape a
        // vendor lock/enable flag might take if the peer only exposes it via its
        // description (or omits it from value reads for another reason). Logged separately
        // so the two loops each stay simple.
        for (DeviceConfigurationKeyValueDescriptionDataType description : descriptions) {
            boolean hasValueEntry = entries.stream()
                    .anyMatch(e -> Objects.equals(e.getKeyId(), description.getKeyId()));
            if (!hasValueEntry) {
                logger.debug(
                        "{} partner's DeviceConfiguration keyId={} keyName={} label={} has a description "
                                + "but no entry in the value list (was investigating limitId={})",
                        getShortCode(), description.getKeyId(), description.getKeyName(), description.getLabel(),
                        resolvedLimitId);
            }
        }
    }

    /**
     * Diagnostic-only (2026-09-02, "ElectricalConnection gating characteristic" theory): reads
     * the peer's {@code ElectricalConnectionParameterDescriptionListData} and
     * {@code ElectricalConnectionPermittedValueSetListData}, joins them by {@code parameterId},
     * and logs every entry. Investigated after diagnostics 1-3 (isLimitChangeable,
     * LoadControlLimitConstraints, DeviceConfigurationKeyValue - see
     * eebus-lpc-command-rejected-2026-09-02.md project memory, device-bridge notes, not part of
     * this repo) were all exhausted without explaining a real Hager Energy S10's
     * {@code COMMAND_REJECTED} (Error 7): SPINE's {@code ElectricalConnection} use case can gate
     * what values a related {@code LoadControl} write is allowed to take via
     * {@code ElectricalConnectionPermittedValueSetListData} (e.g. an empty or unexpectedly narrow
     * permitted-value set for the parameter tied to this limit's {@code measurementId} would be a
     * plausible, SPINE-legal reason for a peer to reject an otherwise well-formed
     * {@code LoadControlLimitListData} write). This feature is unrelated to {@code LoadControl} and
     * entirely optional, so a peer without it ({@code featureAddress == null}, from
     * {@link UseCasePartner#getCompleteFeatureAddress}) or one that rejects either read is expected
     * and logged at debug only - never gates {@link #registerWriteListenersOnce}.
     *
     * @param featureAddress the peer's {@code ElectricalConnection} feature address, or
     *            {@code null} if the peer does not expose one
     * @param resolvedLimitId this use case's resolved {@code limitId}, from
     *            {@link #resolveLimitId} - included in every log line purely for correlation,
     *            since {@code ElectricalConnection} parameters are not themselves tied to a
     *            limitId (only, indirectly, via {@code measurementId} - not currently
     *            cross-checked here)
     */
    private void logElectricalConnectionDiagnostic(@Nullable FeatureAddressType featureAddress, long resolvedLimitId) {
        if (featureAddress == null) {
            logger.debug("{} partner has no ElectricalConnection feature, skipping gating-characteristic "
                    + "diagnostic (was investigating limitId={})", getShortCode(), resolvedLimitId);
            return;
        }
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();
        CmdType descriptionReadCmd = new CmdType().withElectricalConnectionParameterDescriptionListData(
                new ElectricalConnectionParameterDescriptionListDataType());
        nodeManagement.requestRead(featureAddress, descriptionReadCmd).thenCompose(descriptionResult -> {
            ElectricalConnectionParameterDescriptionListDataType descriptionList = descriptionResult.getCmd()
                    .getElectricalConnectionParameterDescriptionListData();
            CmdType permittedReadCmd = new CmdType().withElectricalConnectionPermittedValueSetListData(
                    new ElectricalConnectionPermittedValueSetListDataType());
            return nodeManagement.requestRead(featureAddress, permittedReadCmd)
                    .thenAccept(permittedResult -> logElectricalConnectionEntries(resolvedLimitId, descriptionList,
                            permittedResult.getCmd().getElectricalConnectionPermittedValueSetListData()));
        }).exceptionally(ex -> {
            logger.debug(
                    "{} diagnostic read of partner's ElectricalConnection parameter/permitted-value data "
                            + "failed (peer may not support this optional feature; was investigating limitId={}): {}",
                    getShortCode(), resolvedLimitId, summarize(ex));
            return null;
        });
    }

    /**
     * Logs every entry of a peer's {@code ElectricalConnectionPermittedValueSetListData}, joined
     * against its (also optional, separately-read) {@code ElectricalConnectionParameterDescriptionListData}
     * by {@code parameterId} for human-readable {@code measurementId}/{@code scopeType}/{@code label}
     * context. Split out of {@link #logElectricalConnectionDiagnostic} only to keep the two chained
     * SPINE reads (each independently absent-or-erroring) readable - mirrors
     * {@link #logDeviceConfigurationEntries}'s shape.
     *
     * @param resolvedLimitId see {@link #logElectricalConnectionDiagnostic}
     * @param descriptionList the peer's parameter descriptions, or {@code null} if that read
     *            returned no data - entries are then logged with {@code measurementId}/
     *            {@code scopeType}/{@code label} all {@code null} rather than skipped, since the
     *            permitted value sets themselves are still the point of this diagnostic
     * @param permittedValueSetList the peer's permitted value sets, or {@code null} if that read
     *            returned no data
     */
    private void logElectricalConnectionEntries(long resolvedLimitId,
            @Nullable ElectricalConnectionParameterDescriptionListDataType descriptionList,
            @Nullable ElectricalConnectionPermittedValueSetListDataType permittedValueSetList) {
        if (permittedValueSetList == null) {
            logger.debug("{} partner's ElectricalConnectionPermittedValueSetListData returned no data (was "
                    + "investigating limitId={})", getShortCode(), resolvedLimitId);
            return;
        }
        List<ElectricalConnectionParameterDescriptionDataType> descriptions = descriptionList == null ? List.of()
                : descriptionList.getElectricalConnectionParameterDescriptionData();
        for (ElectricalConnectionPermittedValueSetDataType entry : permittedValueSetList
                .getElectricalConnectionPermittedValueSetData()) {
            Optional<ElectricalConnectionParameterDescriptionDataType> description = descriptions.stream()
                    .filter(d -> Objects.equals(d.getParameterId(), entry.getParameterId())).findFirst();
            logger.debug("{} partner's ElectricalConnectionPermittedValueSetListData parameterId={} "
                    + "measurementId={} scopeType={} label={} permittedValueSet={} (was " + "investigating limitId={})",
                    getShortCode(), entry.getParameterId(),
                    description.map(ElectricalConnectionParameterDescriptionDataType::getMeasurementId).orElse(null),
                    description.map(ElectricalConnectionParameterDescriptionDataType::getScopeType).orElse(null),
                    description.map(ElectricalConnectionParameterDescriptionDataType::getLabel).orElse(null),
                    entry.getPermittedValueSet(), resolvedLimitId);
        }
    }

    /**
     * Looks up Items tagged on this Bridge's single {@code eebus:oh-eg-entity} write-source
     * Thing for this use case's Client-role write path (CONCEPT.md, see
     * {@link #DATA_POINT_LIMIT_ACTIVE}/{@link #DATA_POINT_LIMIT_VALUE}/
     * {@link #DATA_POINT_LIMIT_DURATION}) and, for every one found, registers a listener via
     * {@link EEBusMetadataService#registerItemStateListener} that sends a combined SPINE write to
     * <strong>every currently bound partner</strong> ({@link #sendLimitWriteToAllPartners},
     * docs/ADR/047-fanout-energyguard-writes-to-all-partners.md) whenever any of the three Items
     * changes - the *other* two are read fresh via {@link EEBusMetadataService#readState} at that
     * moment, since a single SPINE write always carries
     * {@code isLimitActive}/{@code value}/{@code timePeriod} together. No-op if none of the three
     * Items is tagged on the write-source Thing (the write path is opt-in - most Bridges will
     * only ever be read, not written to). Untagged {@link #DATA_POINT_LIMIT_DURATION} simply
     * means every write omits {@code timePeriod} (unbounded limit) - see docs/ADR/033-lpc-lpp-
     * limit-duration-channel.md.
     *
     * <p>
     * Guarded by {@link #writeListenersRegistered} so repeated calls (once per newly bound
     * partner - see {@link #resolveLimitIdAndRegisterPartnerBinding}) register the same three
     * listeners only once. If the write-source Thing has no tagged Items yet when first called,
     * {@link #writeListenersRegistered} is deliberately left {@code false} so a later partner's
     * call can retry once Items get tagged.
     * </p>
     *
     * @param writeSourceHandler this Bridge's single {@code eebus:oh-eg-entity} Thing's handler,
     *            used for its {@link ThingUID} (the write-path tag's required prefix) and logging
     */
    private void registerWriteListenersOnce(EEBusOhEntityHandler writeSourceHandler) {
        registerChannelWriter(writeSourceHandler);
        if (writeListenersRegistered.get()) {
            return;
        }
        ThingUID sourceUid = writeSourceHandler.getThing().getUID();
        Optional<String> activeItem = metadataService.findByTag(sourceUid, getShortCode(), DATA_POINT_LIMIT_ACTIVE);
        Optional<String> valueItem = metadataService.findByTag(sourceUid, getShortCode(), DATA_POINT_LIMIT_VALUE);
        Optional<String> durationItem = metadataService.findByTag(sourceUid, getShortCode(), DATA_POINT_LIMIT_DURATION);
        if (activeItem.isEmpty() && valueItem.isEmpty() && durationItem.isEmpty()) {
            return;
        }
        if (!writeListenersRegistered.compareAndSet(false, true)) {
            // Another partner's concurrent resolution already won the race and registered these
            // listeners - nothing left to do here.
            return;
        }
        logger.debug(
                "{} write path: active-Item={}, value-Item={}, duration-Item={} for oh-eg-entity '{}', "
                        + "fanning out to every currently bound partner (docs/ADR/047)",
                getShortCode(), activeItem, valueItem, durationItem, sourceUid);
        activeItem.ifPresent(activeItemName -> {
            Consumer<State> listener = state -> sendLimitWriteToAllPartners(toActive(state),
                    toWatts(valueItem.flatMap(metadataService::readState).orElse(null)),
                    toSeconds(durationItem.flatMap(metadataService::readState).orElse(null)));
            metadataService.registerItemStateListener(activeItemName, listener);
            writeListenerCleanup.add(() -> metadataService.unregisterItemStateListener(activeItemName, listener));
        });
        valueItem.ifPresent(valueItemName -> {
            Consumer<State> listener = state -> sendLimitWriteToAllPartners(
                    toActive(activeItem.flatMap(metadataService::readState).orElse(null)), toWatts(state),
                    toSeconds(durationItem.flatMap(metadataService::readState).orElse(null)));
            metadataService.registerItemStateListener(valueItemName, listener);
            writeListenerCleanup.add(() -> metadataService.unregisterItemStateListener(valueItemName, listener));
        });
        durationItem.ifPresent(durationItemName -> {
            Consumer<State> listener = state -> sendLimitWriteToAllPartners(
                    toActive(activeItem.flatMap(metadataService::readState).orElse(null)),
                    toWatts(valueItem.flatMap(metadataService::readState).orElse(null)), toSeconds(state));
            metadataService.registerItemStateListener(durationItemName, listener);
            writeListenerCleanup.add(() -> metadataService.unregisterItemStateListener(durationItemName, listener));
        });
    }

    private final Object channelWriterLock = new Object();
    private @Nullable EEBusOhEntityHandler channelWriterHandler;
    private EEBusOhEntityHandler.@Nullable LimitChannelWriter channelWriter;

    /**
     * Channel-based write path of the Energy Guard (value provider): a command on the write-source
     * Thing's {@code limit-active}/{@code limit-value}/{@code limit-duration} Channel of this use
     * case's group sends the same combined fan-out write as a tagged-Item change does. Idempotent
     * (called once per bound partner); removed again in {@link #close()}.
     */
    private void registerChannelWriter(EEBusOhEntityHandler writeSourceHandler) {
        synchronized (channelWriterLock) {
            if (channelWriter != null) {
                return;
            }
            EEBusOhEntityHandler.LimitChannelWriter writer = (active, value,
                    duration) -> sendLimitWriteToAllPartners(toActive(active), toWatts(value), toSeconds(duration));
            writeSourceHandler.registerLimitChannelWriter(getShortCode(), writer);
            channelWriterHandler = writeSourceHandler;
            channelWriter = writer;
        }
    }

    /**
     * Fan-out counterpart of the old single-partner write call (docs/ADR/047-fanout-
     * energyguard-writes-to-all-partners.md): sends the identical {@code active}/{@code watts}/
     * {@code durationSeconds} to every partner currently in {@link #partnerBindings}, each via
     * {@link #sendLimitWrite} with its own resolved {@code limitId} - see class javadoc "limitId
     * resolution": two peers are not guaranteed to share a {@code limitId}, only the
     * value/active/duration are guaranteed identical.
     *
     * @param active the {@code isLimitActive} value to write to every bound partner
     * @param watts the {@code value} (in Watt) to write to every bound partner
     * @param durationSeconds the {@code timePeriod.endTime} to write to every bound partner, or
     *            {@code null} to omit {@code timePeriod} (unbounded limit) - see
     *            {@link #sendLimitWrite}
     */
    private void sendLimitWriteToAllPartners(boolean active, double watts, @Nullable Long durationSeconds) {
        if (partnerBindings.isEmpty()) {
            logger.debug("{} write-path Item changed, but no partner is currently bound - nothing to send",
                    getShortCode());
            return;
        }
        // One failing partner (e.g. a peer whose connection was just torn down) must not prevent the write
        // from reaching the remaining partners - found live 2026-10-05: an NPE inside jeebus.ship for a
        // disabled CS aborted the fan-out before it reached the S10.
        partnerBindings.forEach((communicationAddress, binding) -> {
            try {
                sendLimitWrite(binding.featureAddress(), binding.limitId(), active, watts, durationSeconds);
            } catch (RuntimeException e) {
                logger.warn("{} limit write to partner {} failed: {}", getShortCode(), communicationAddress,
                        summarize(e));
            }
        });
    }

    /**
     * Sends a combined {@code LoadControlLimitDataType} write (CONCEPT.md, Client-role write
     * path) to the peer's {@code LoadControl} feature - the outbound counterpart of
     * {@link AbstractEEBusLimitControllableSystemUseCase}'s
     * {@code addUseCaseWriteDataListener}-based receive path. Fire-and-forget: failures are
     * logged, not retried or surfaced to the tagged Item (v1 - see CONCEPT.md, not yet tracked as
     * an open item).
     *
     * @param featureAddress the peer's {@code LoadControl} feature address
     * @param limitId this use case's resolved {@code limitId} for this peer, from
     *            {@link #resolveLimitIdAndRegisterPartnerBinding}
     * @param active the {@code isLimitActive} value to write
     * @param watts the {@code value} (in Watt) to write
     * @param durationSeconds the {@code timePeriod.endTime} (a relative duration, per
     *            LimitationOfPowerConsumption TS V1.0.0 §3.1.8.2) to write, in seconds, or
     *            {@code null} to omit {@code timePeriod} entirely (unbounded limit, [LPC-004]) -
     *            see docs/ADR/033-lpc-lpp-limit-duration-channel.md
     */
    private void sendLimitWrite(FeatureAddressType featureAddress, long limitId, boolean active, double watts,
            @Nullable Long durationSeconds) {
        Device localDevice = this.device;
        Entity localEntity = this.entity;
        if (localDevice == null || localEntity == null) {
            return;
        }
        // ADR-045 (2026-09-04): the write must be issued via this entity's own Generic/client
        // feature, not the bare NodeManagement (entity=0) - otherwise the outgoing addressSource
        // doesn't match the entity that was actually bound (ADR-044, entity=1), and the real Hager
        // S10 correctly rejects the write with Error 9 (BINDING_NECESSARY, "Client not bound").
        // Confirmed via the 2026-09-04 21:11-21:21 real-S10 retest: bind now reports entity=1, but
        // the write still went out via nodeManagement (entity=0) and every write attempt failed with
        // exactly this error. Reads (logLimitStateSnapshot below) are unaffected and stay on
        // nodeManagement - the S10 answers reads regardless of binding.
        Feature genericClientFeature = findGenericClientFeature(localEntity);
        LoadControlLimitDataType data = new LoadControlLimitDataType().withLimitId(limitId).withIsLimitActive(active)
                .withValue(new ScaledNumberWrapper(watts).toXsdType())
                // [LPC-004]/ADR-033: SHALL be set only if the limit has a duration of validity,
                // SHALL be absent otherwise - never write an empty TimePeriodType.
                .withTimePeriod(durationSeconds != null
                        ? new TimePeriodType().withEndTime(secondsToDuration(durationSeconds).toString())
                        : null);
        CmdType writeCmd = new CmdType()
                .withLoadControlLimitListData(new LoadControlLimitListDataType().withLoadControlLimitData(data));
        // 2026-09-02: log the exact outgoing payload (including limitId, previously missing from the
        // failure log below too) while diagnosing a real Hager Energy S10 COMMAND_REJECTED response -
        // see docs/ADR - this only logs what we send, the peer's negative ack is still handled below.
        logger.debug("{} sending limit write: limitId={}, active={}, value={} W, duration={} s, feature={}",
                getShortCode(), limitId, active, watts, durationSeconds, featureAddress);
        // 2026-09-02: diagnostic 4 ("state-dependent rejection" theory) while still chasing a real
        // Hager Energy S10's COMMAND_REJECTED (Error 7), after diagnostics 1-3 (isLimitChangeable,
        // LoadControlLimitConstraints, DeviceConfigurationKeyValue) were all exhausted without
        // explaining it - see eebus-lpc-command-rejected-2026-09-02.md project memory (device-bridge
        // notes, not part of this repo). Snapshots the peer's live LoadControlLimitListData for this
        // limitId immediately before issuing the write, and again once the write attempt has settled
        // (success or failure), purely to see whether the peer's own reported state looks any
        // different across a rejected write. Both snapshots are independent of the write itself
        // (fire-and-forget, logged only) and never delay or gate the write below.
        logLimitStateSnapshot(featureAddress, limitId, "pre-write");
        genericClientFeature.requestWrite(featureAddress, writeCmd).handle((result, ex) -> {
            if (ex != null) {
                logger.warn("{} limit write failed: limitId={}, active={}, value={} W, duration={} s, " + "feature={}",
                        getShortCode(), limitId, active, watts, durationSeconds, featureAddress, ex);
            }
            return null;
        }).thenRun(() -> logLimitStateSnapshot(featureAddress, limitId, "post-write"));
    }

    /**
     * Diagnostic-only (state-dependent-rejection theory, 2026-09-02): reads the peer's live
     * {@code LoadControlLimitListData} for {@code limitId} and logs the matching entry tagged
     * with {@code label} ("pre-write"/"post-write") - see {@link #sendLimitWrite}. Same read
     * shape as {@link #logLimitChangeableDiagnostic} (which only runs once, at bind time); this
     * one runs around every write attempt instead, to catch a rejection-adjacent state change
     * that a bind-time-only snapshot would miss.
     *
     * @param featureAddress the peer's {@code LoadControl} feature address
     * @param limitId the {@code limitId} being written
     * @param label distinguishes the two call sites in the log ("pre-write"/"post-write")
     */
    private void logLimitStateSnapshot(FeatureAddressType featureAddress, long limitId, String label) {
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();
        CmdType readCmd = new CmdType().withLoadControlLimitListData(new LoadControlLimitListDataType());
        nodeManagement.requestRead(featureAddress, readCmd).thenAccept(result -> {
            LoadControlLimitListDataType limitList = result.getCmd().getLoadControlLimitListData();
            if (limitList == null) {
                logger.debug("{} {} LoadControlLimitListData read for limitId={} returned no data", getShortCode(),
                        label, limitId);
                return;
            }
            limitList.getLoadControlLimitData().stream().filter(d -> Objects.equals(d.getLimitId(), limitId))
                    .findFirst().ifPresentOrElse(
                            d -> logger.debug(
                                    "{} {} LoadControlLimitListData for limitId={}: isLimitChangeable={}, "
                                            + "isLimitActive={}, value={}",
                                    getShortCode(), label, limitId, d.getIsLimitChangeable(), d.getIsLimitActive(),
                                    d.getValue()),
                            () -> logger.debug("{} {} LoadControlLimitListData has no entry for limitId={}",
                                    getShortCode(), label, limitId));
        }).exceptionally(ex -> {
            logger.debug("{} {} diagnostic read of partner's LoadControlLimitListData for limitId={} failed: {}",
                    getShortCode(), label, limitId, summarize(ex));
            return null;
        });
    }

    /**
     * @param seconds the duration in whole seconds
     * @return an {@code xs:duration} of exactly {@code seconds}, matching
     *         {@link AbstractEEBusLimitControllableSystemUseCase}'s identical private helper (not
     *         shared - each Use Case class is otherwise self-contained, per this codebase's
     *         existing convention)
     */
    private static javax.xml.datatype.Duration secondsToDuration(long seconds) {
        return DatatypeFactory.newDefaultInstance().newDuration(true, null, null, null, null, null,
                BigDecimal.valueOf(seconds));
    }

    /**
     * @param state the {@link #DATA_POINT_LIMIT_ACTIVE} Item's state, or {@code null} if that
     *            data point is not tagged for this peer/use case
     * @return {@code true} only for {@link OnOffType#ON} - defaults to {@code false} (limit not
     *         active) if the Item is missing or holds any other state, since writing an
     *         unintended active limit is the worse failure mode of the two
     */
    private static boolean toActive(@Nullable State state) {
        return state instanceof OnOffType onOff && onOff == OnOffType.ON;
    }

    /**
     * @param state the {@link #DATA_POINT_LIMIT_VALUE} Item's state, or {@code null} if that
     *            data point is not tagged for this peer/use case
     * @return the state converted to Watt - {@link QuantityType} is converted via
     *         {@link Units#WATT}, anything else falls back to {@link State#as(Class)} with
     *         {@link DecimalType} (treated as already being in Watt); {@code 0.0} if the Item is
     *         missing or unconvertible
     */
    private static double toWatts(@Nullable State state) {
        if (state instanceof QuantityType<?> quantity) {
            QuantityType<?> watts = quantity.toUnit(Units.WATT);
            return watts != null ? watts.doubleValue() : 0.0;
        }
        DecimalType decimal = state == null ? null : state.as(DecimalType.class);
        return decimal != null ? decimal.doubleValue() : 0.0;
    }

    /**
     * @param state the {@link #DATA_POINT_LIMIT_DURATION} Item's state, or {@code null} if that
     *            data point is not tagged for this peer/use case
     * @return the state converted to whole seconds - {@link QuantityType} is converted via
     *         {@link Units#SECOND}, anything else falls back to {@link State#as(Class)} with
     *         {@link DecimalType} (treated as already being in seconds); {@code null} if the Item
     *         is missing, unconvertible, or holds {@link org.openhab.core.types.UnDefType#UNDEF} -
     *         {@code null} means {@code timePeriod} is omitted entirely from the write (unbounded
     *         limit, [LPC-004]), see docs/ADR/033-lpc-lpp-limit-duration-channel.md
     */
    private static @Nullable Long toSeconds(@Nullable State state) {
        if (state instanceof QuantityType<?> quantity) {
            QuantityType<?> seconds = quantity.toUnit(Units.SECOND);
            return seconds != null ? Math.round(seconds.doubleValue()) : null;
        }
        DecimalType decimal = state == null ? null : state.as(DecimalType.class);
        return decimal != null ? Math.round(decimal.doubleValue()) : null;
    }

    @Override
    public void close() {
        // Unlike EEBusMpcClientUseCase#close(), this class does hold per-instance resources now
        // (see #registerWriteListenersOnce) - must unregister to avoid leaking listeners across
        // Bridge restarts (see EEBusMetadataService#unregisterItemStateListener's javadoc).
        writeListenerCleanup.forEach(Runnable::run);
        writeListenerCleanup.clear();
        synchronized (channelWriterLock) {
            EEBusOhEntityHandler handler = channelWriterHandler;
            EEBusOhEntityHandler.LimitChannelWriter writer = channelWriter;
            if (handler != null && writer != null) {
                handler.unregisterLimitChannelWriter(getShortCode(), writer);
            }
            channelWriterHandler = null;
            channelWriter = null;
        }
        subscribedPartners.clear();
        partnerBindings.clear();
        writeListenersRegistered.set(false);
    }

    /**
     * One-line summary of a failed optional diagnostic read (no stack trace: such failures, e.g.
     * COMMAND_NOT_SUPPORTED from a peer lacking the optional function, are expected and a full
     * trace per read only floods the log).
     */
    private static String summarize(Throwable ex) {
        Throwable cause = ex;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }
}
