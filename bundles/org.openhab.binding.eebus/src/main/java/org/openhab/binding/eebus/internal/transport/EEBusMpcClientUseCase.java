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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
import org.openmuc.jeebus.spine.api.Device;
import org.openmuc.jeebus.spine.api.Entity;
import org.openmuc.jeebus.spine.api.NodeManagement;
import org.openmuc.jeebus.spine.api.PresenceIndication;
import org.openmuc.jeebus.spine.api.RequestResult;
import org.openmuc.jeebus.spine.api.UseCasePartner;
import org.openmuc.jeebus.spine.spi.FeatureRequirement;
import org.openmuc.jeebus.spine.spi.Inject;
import org.openmuc.jeebus.spine.spi.UseCase;
import org.openmuc.jeebus.spine.utils.datatypes.ScaledNumberWrapper;
import org.openmuc.jeebus.spine.xsd.v1.CmdType;
import org.openmuc.jeebus.spine.xsd.v1.EntityTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FunctionEnumType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementDescriptionListDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementListDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.ScopeTypeEnumType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects the MPC (Monitoring of Power Consumption) use case in the **Client role** — i.e.
 * openHAB looks for a paired peer offering MPC as the "CEM" server actor (e.g. a real inverter
 * or energy management system) and reads its power measurement via SPINE subscription. This is
 * the consumer-side counterpart to {@link EEBusMpcServerUseCase} (CONCEPT.md §1, §5.4.1,
 * §5.4.2, §7 item 3/8 follow-up: Client-role wiring for {@code supportedUseCasesClient}).
 *
 * <p>
 * <strong>How detection works</strong> (verified against jeebus.spine's
 * {@code NodeManagement#addUseCaseListener}, {@code UseCaseListener}, and the demo
 * {@code ExampleUseCase} in {@code jeebus.spine/projects/demo}): this class is added to the
 * local {@code CEM} entity's {@code withUseCases(...)} array exactly like a Server-role use
 * case (see {@code org.openhab.binding.eebus.internal.handler.EEBusHandler#startShipSpine});
 * {@link Entity#addUseCase(UseCase)} fills the {@link Inject} field, then calls {@link #setup()},
 * where {@code entity.getDevice().getNodeManagement().addUseCaseListener(...)} is registered.
 * SPINE then calls {@link #onUseCasePartnersFound(List)} whenever the set of matching peers
 * changes.
 * </p>
 *
 * <p>
 * <strong>Per-peer routing:</strong> unlike the Server-role use case (one value, Bridge-wide),
 * Client-role data is inherently per-peer - several paired peers could each offer MPC. Each
 * found {@link UseCasePartner} is resolved back to its paired {@code eebus:oh-entity}
 * {@link EEBusOhEntityHandler} via the {@code ohEntityHandlerResolver} passed into the constructor
 * (composed in {@code org.openhab.binding.eebus.internal.handler.EEBusHandler} from
 * {@link EEBusMdnsBrowser#skiForCommunicationAddress}). Per CONCEPT.md §4.2 and docs/ADR/014-
 * dynamic-client-role-channels.md, this data is delivered to
 * {@link EEBusOhEntityHandler#applyMpcPower(double)}, which creates the {@code mpc#power} Channel
 * on first use and updates its state - <strong>not</strong> Item metadata (metadata is
 * Server-role only as of CONCEPT.md §4.5).
 * </p>
 *
 * <p>
 * <strong>Scope, deliberately limited</strong> (mirrors {@link EEBusMpcServerUseCase}): only
 * scenario 1 (total AC power) is requested, only the {@code Measurement} feature is used. Since
 * a real peer is not guaranteed to use measurement ID 0 for its total-power entry (our own
 * Server-role class always does, but that is our own convention, not a spec requirement), this
 * class first reads {@code MeasurementDescriptionListData} to find the ID whose
 * {@code scopeType} is {@code ACPowerTotal} (falling back to {@code measurementType == Power},
 * then to the sole entry if only one description exists) before parsing subsequent
 * {@code MeasurementListData} notifications for that ID.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
// No @AllowedEntityTypes restriction: the Monitoring Appliance actor's TS text (MPC §3.2.1,
// same wording for MGCP) states the use case data "follows behind any entityType" - no
// restriction at all (see docs/ADR/042-configurable-entitytype.md's Context section and its
// 2026-09-04 update). jeebus.spine's own EntityImpl#addFeaturesForUseCase treats a missing
// annotation as "skip the check" (confirmed via source read), which matches this exactly -
// no enumerated allow-list would be more correct than "none" here.
public class EEBusMpcClientUseCase implements UseCase {

    private final Logger logger = LoggerFactory.getLogger(EEBusMpcClientUseCase.class);
    private final Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver;

    /**
     * Communication addresses ({@link UseCasePartner#getCommunicationAddress()}) already fully
     * subscribed by {@link #onUseCasePartnersFound}.
     *
     * <p>
     * <strong>2026-08-21 fix</strong> (mirrors the identical fix and rationale in
     * {@link AbstractEEBusLimitEnergyGuardUseCase#subscribedPartners}): per this class's own
     * javadoc ("How detection works"), jeebus.spine calls {@code onUseCasePartnersFound(List)}
     * whenever the set of matching peers changes, not just once - and re-invocations pass the
     * current full partner list again, including partners already seen. Before this fix, every
     * invocation unconditionally re-ran {@link #subscribe} for every partner in the list,
     * creating an additional, independent {@code NodeManagement#requestSubscription} each time
     * with no way to tear down the previous one - observed live (in the sibling LPC/LPP class,
     * which shares this exact pattern) as N-fold duplicate measurement-apply log lines for a
     * single real SPINE notification. This set makes partner processing idempotent. A partner
     * that failed to resolve (see the {@code ohEntityHandler.isEmpty()} branch below) is
     * deliberately <em>not</em> added, so a later notification can still succeed for it.
     * </p>
     */
    private final Set<String> subscribedPartners = ConcurrentHashMap.newKeySet();

    @Inject
    private @Nullable Entity entity;

    private @Nullable FeatureAddressType address;
    private @Nullable Device device;

    /**
     * @param ohEntityHandlerResolver resolves a SPINE {@code communicationAddress} to the paired
     *            {@code eebus:oh-entity} Thing's {@link EEBusOhEntityHandler}, if any (see class
     *            javadoc "Per-peer routing"). Returns the live handler, not just its UID, so
     *            {@link #applyMeasurement} can call {@link EEBusOhEntityHandler#applyMpcPower}
     *            directly - see docs/ADR/014-dynamic-client-role-channels.md.
     */
    public EEBusMpcClientUseCase(Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver) {
        this.ohEntityHandlerResolver = ohEntityHandlerResolver;
    }

    @Override
    public String getActor() {
        // "MonitoringAppliance" - the Client Actor of MPC per CONCEPT.md §5.4.1 (primary
        // catalog: "Monitoring Appliance"); exact wire-format casing taken from the verified
        // eebus-go reference, same source already cited for EEBusMpcServerUseCase.
        return "MonitoringAppliance";
    }

    @Override
    public String getName() {
        // Must match the use case name a real peer's Server role advertises. Kept identical to
        // EEBusMpcServerUseCase#getName() for interop with another instance of this binding.
        // Confirmed 2026-08-05 against a real Hager Energy S10's discovery JSON (jeebus.spine's
        // DiscoveryLogger output, see docs/ADR/011-usecasename-lowercamelcase.md): the wire
        // format is lowerCamelCase, not PascalCase - was "MonitoringOfPowerConsumption" before.
        return "monitoringOfPowerConsumption";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    // UseCase (jeebus.spine) is an unannotated legacy interface - see the identical note on
    // AbstractEEBusLimitControllableSystemUseCase#getScenarioSupport().
    @Override
    @NonNullByDefault({})
    public List<Long> getScenarioSupport() {
        return List.of(1L);
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
        // The generic client feature "needed by the client actor of most use cases" (see
        // FeatureRequirement#GENERIC_CLIENT javadoc) - no local Measurement feature is needed
        // since NodeManagement#requestRead/requestSubscription can target any remote feature
        // address directly (verified against jeebus.spine's ExampleUseCase demo).
        return Set.of(FeatureRequirement.GENERIC_CLIENT);
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

        localEntity.getDevice().getNodeManagement().addUseCaseListener(this::onUseCasePartnersFound, getName(), "CEM", // the
                                                                                                                       // actor
                                                                                                                       // we
                                                                                                                       // expect
                                                                                                                       // a
                                                                                                                       // remote
                                                                                                                       // peer
                                                                                                                       // to
                                                                                                                       // play
                                                                                                                       // -
                                                                                                                       // MPC
                                                                                                                       // Server
                                                                                                                       // Actor
                                                                                                                       // per
                                                                                                                       // CONCEPT.md
                                                                                                                       // §5.4.1
                Map.of(1L, PresenceIndication.MANDATORY),
                Set.of(new CommunicationPartnerFeatureRequirement(FeatureTypeEnumType.MEASUREMENT,
                        Map.of(FunctionEnumType.MEASUREMENT_LIST_DATA, Map.of(1L, PresenceIndication.MANDATORY),
                                FunctionEnumType.MEASUREMENT_DESCRIPTION_LIST_DATA,
                                Map.of(1L, PresenceIndication.MANDATORY)))));
    }

    private void onUseCasePartnersFound(List<UseCasePartner> partners) {
        for (UseCasePartner partner : partners) {
            FeatureAddressType featureAddress = partner.getCompleteFeatureAddress(FeatureTypeEnumType.MEASUREMENT);
            if (featureAddress == null) {
                logger.debug("MPC partner at {} has no Measurement feature address, skipping",
                        partner.getCommunicationAddress());
                continue;
            }
            Optional<EEBusOhEntityHandler> ohEntityHandler = ohEntityHandlerResolver
                    .apply(partner.getCommunicationAddress());
            if (ohEntityHandler.isEmpty()) {
                logger.debug(
                        "MPC partner at {} could not be resolved to a paired eebus:oh-entity Thing (mDNS not (yet) "
                                + "seeing it?), skipping",
                        partner.getCommunicationAddress());
                continue;
            }
            // 2026-08-21 fix: see #subscribedPartners javadoc.
            if (!subscribedPartners.add(partner.getCommunicationAddress())) {
                logger.debug("MPC partner at {} already subscribed, ignoring duplicate use-case-partner notification",
                        partner.getCommunicationAddress());
                continue;
            }
            EEBusOhEntityHandler handler = ohEntityHandler.get();
            // CONCEPT.md §4.2.2 point 3 / docs/changes/dynamic-client-role-channels/proposal.md
            // "Open Questions": value is the actor role the *peer* plays ("CEM"/Server per
            // setup() below), not openHAB's own role.
            handler.recordDetectedUseCase(EEBusBindingConstants.USE_CASE_KEY_MPC, "server");
            subscribe(featureAddress, handler);
        }
    }

    private void subscribe(FeatureAddressType featureAddress, EEBusOhEntityHandler ohEntityHandler) {
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();

        // Resolve the description once (measurementId -> scopeType/measurementType mapping, see
        // class javadoc) for both the mandatory power id and any of the twelve additional data
        // points found (docs/ADR/037-mpc-additional-datapoints.md Decision 2), then subscribe
        // for ongoing MeasurementListData notifications covering all resolved ids in one
        // subscription. An initial read is issued as well so the eventual Channels get a value
        // immediately rather than waiting for the peer's next spontaneous notification.
        CmdType descriptionReadCmd = new CmdType()
                .withMeasurementDescriptionListData(new MeasurementDescriptionListDataType());
        nodeManagement.requestRead(featureAddress, descriptionReadCmd).thenAccept(result -> {
            Optional<Long> powerMeasurementId = resolvePowerMeasurementId(result);
            Map<Long, MpcDataPoint> additionalMeasurementIds = resolveAdditionalMeasurementIds(result);
            if (powerMeasurementId.isEmpty() && additionalMeasurementIds.isEmpty()) {
                logger.debug("MPC partner's MeasurementDescriptionListData has no resolvable data point, ignoring "
                        + "feature at {}", featureAddress);
                return;
            }

            CmdType valueReadCmd = new CmdType().withMeasurementListData(new MeasurementListDataType());
            nodeManagement.requestRead(featureAddress, valueReadCmd)
                    .thenAccept(valueResult -> applyMeasurement(valueResult, powerMeasurementId,
                            additionalMeasurementIds, ohEntityHandler))
                    .exceptionally(ex -> {
                        logger.debug("Initial MPC read failed for oh-entity '{}'", ohEntityHandler.getThing().getUID(),
                                ex);
                        return null;
                    });

            nodeManagement.requestSubscription(featureAddress, FeatureTypeEnumType.MEASUREMENT,
                    notification -> applyMeasurement(notification, powerMeasurementId, additionalMeasurementIds,
                            ohEntityHandler))
                    .handle((subscribeResult, ex) -> {
                        if (ex != null) {
                            logger.warn("Failed to subscribe to MPC measurements for oh-entity '{}'",
                                    ohEntityHandler.getThing().getUID(), ex);
                        }
                        return null;
                    });
        }).exceptionally(ex -> {
            logger.warn("Failed to read MeasurementDescriptionListData for MPC partner (oh-entity '{}')",
                    ohEntityHandler.getThing().getUID(), ex);
            return null;
        });
    }

    /**
     * @return the measurement ID whose description matches total AC power, per the fallback
     *         chain documented in the class javadoc.
     */
    private Optional<Long> resolvePowerMeasurementId(RequestResult result) {
        MeasurementDescriptionListDataType descriptionList = result.getCmd().getMeasurementDescriptionListData();
        if (descriptionList == null) {
            return Optional.empty();
        }
        List<MeasurementDescriptionDataType> descriptions = descriptionList.getMeasurementDescriptionData();
        Optional<Long> byScope = descriptions.stream()
                .filter(d -> ScopeTypeEnumType.AC_POWER_TOTAL.value().equals(d.getScopeType()))
                .map(MeasurementDescriptionDataType::getMeasurementId).findFirst();
        if (byScope.isPresent()) {
            return byScope;
        }
        Optional<Long> byType = descriptions.stream()
                .filter(d -> MeasurementTypeEnumType.POWER.value().equals(d.getMeasurementType()))
                .map(MeasurementDescriptionDataType::getMeasurementId).findFirst();
        if (byType.isPresent()) {
            return byType;
        }
        if (descriptions.size() == 1) {
            return Optional.ofNullable(descriptions.get(0).getMeasurementId());
        }
        return Optional.empty();
    }

    /**
     * Describes one of the twelve additional MPC Client-role data points resolved by
     * {@link #resolveAdditionalMeasurementIds} (docs/ADR/037-mpc-additional-datapoints.md) - the
     * {@code ScopeType} a measurement description must match, and everything
     * {@link EEBusOhEntityHandler#applyMpcMeasurement} needs to create/update the corresponding
     * Channel.
     */
    private record MpcDataPoint(String scopeType, String channelId, ChannelTypeUID channelTypeUid,
            String acceptedItemType, String label, Unit<?> unit) {
    }

    /**
     * The twelve additional MPC Client-role data points beyond the mandatory {@code power}
     * (docs/ADR/037-mpc-additional-datapoints.md Decision 1) - matched by exact {@code ScopeType}
     * only, deliberately without {@code power}'s measurement-type/sole-entry fallback (see that
     * ADR's Decision 1 for why a fallback would be ambiguous for these twelve).
     */
    private static final List<MpcDataPoint> ADDITIONAL_DATA_POINTS = List.of(
            new MpcDataPoint(ScopeTypeEnumType.AC_POWER_A.value(), EEBusBindingConstants.CHANNEL_MPC_POWER_PHASE_A,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_POWER_PHASE_A, "Number:Power", "Power (Phase A)",
                    Units.WATT),
            new MpcDataPoint(ScopeTypeEnumType.AC_POWER_B.value(), EEBusBindingConstants.CHANNEL_MPC_POWER_PHASE_B,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_POWER_PHASE_B, "Number:Power", "Power (Phase B)",
                    Units.WATT),
            new MpcDataPoint(ScopeTypeEnumType.AC_POWER_C.value(), EEBusBindingConstants.CHANNEL_MPC_POWER_PHASE_C,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_POWER_PHASE_C, "Number:Power", "Power (Phase C)",
                    Units.WATT),
            new MpcDataPoint(ScopeTypeEnumType.AC_ENERGY_CONSUMED.value(),
                    EEBusBindingConstants.CHANNEL_MPC_ENERGY_CONSUMED,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_ENERGY_CONSUMED, "Number:Energy", "Energy Consumed",
                    Units.WATT_HOUR),
            new MpcDataPoint(ScopeTypeEnumType.AC_ENERGY_PRODUCED.value(),
                    EEBusBindingConstants.CHANNEL_MPC_ENERGY_PRODUCED,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_ENERGY_PRODUCED, "Number:Energy", "Energy Produced",
                    Units.WATT_HOUR),
            new MpcDataPoint(ScopeTypeEnumType.AC_CURRENT_A.value(), EEBusBindingConstants.CHANNEL_MPC_CURRENT_PHASE_A,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_A, "Number:ElectricCurrent",
                    "Current (Phase A)", Units.AMPERE),
            new MpcDataPoint(ScopeTypeEnumType.AC_CURRENT_B.value(), EEBusBindingConstants.CHANNEL_MPC_CURRENT_PHASE_B,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_B, "Number:ElectricCurrent",
                    "Current (Phase B)", Units.AMPERE),
            new MpcDataPoint(ScopeTypeEnumType.AC_CURRENT_C.value(), EEBusBindingConstants.CHANNEL_MPC_CURRENT_PHASE_C,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_C, "Number:ElectricCurrent",
                    "Current (Phase C)", Units.AMPERE),
            new MpcDataPoint(ScopeTypeEnumType.AC_VOLTAGE_A.value(), EEBusBindingConstants.CHANNEL_MPC_VOLTAGE_PHASE_A,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_VOLTAGE_PHASE_A, "Number:ElectricPotential",
                    "Voltage (Phase A-Neutral)", Units.VOLT),
            new MpcDataPoint(ScopeTypeEnumType.AC_VOLTAGE_B.value(), EEBusBindingConstants.CHANNEL_MPC_VOLTAGE_PHASE_B,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_VOLTAGE_PHASE_B, "Number:ElectricPotential",
                    "Voltage (Phase B-Neutral)", Units.VOLT),
            new MpcDataPoint(ScopeTypeEnumType.AC_VOLTAGE_C.value(), EEBusBindingConstants.CHANNEL_MPC_VOLTAGE_PHASE_C,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_VOLTAGE_PHASE_C, "Number:ElectricPotential",
                    "Voltage (Phase C-Neutral)", Units.VOLT),
            new MpcDataPoint(ScopeTypeEnumType.AC_FREQUENCY_GRID.value(), EEBusBindingConstants.CHANNEL_MPC_FREQUENCY,
                    EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_FREQUENCY, "Number:Frequency", "Frequency",
                    Units.HERTZ));

    /**
     * @return a Map from measurement ID to the {@link MpcDataPoint} whose {@code scopeType}
     *         exactly matched, for every entry in {@code result} matching one of
     *         {@link #ADDITIONAL_DATA_POINTS} - see docs/ADR/037-mpc-additional-datapoints.md
     *         Decision 1 for why this is scope-match only, unlike
     *         {@link #resolvePowerMeasurementId}'s fallback chain. Empty when {@code result} has
     *         no description list, or none of its entries match.
     */
    private Map<Long, MpcDataPoint> resolveAdditionalMeasurementIds(RequestResult result) {
        MeasurementDescriptionListDataType descriptionList = result.getCmd().getMeasurementDescriptionListData();
        if (descriptionList == null) {
            return Map.of();
        }
        Map<Long, MpcDataPoint> resolved = new HashMap<>();
        for (MeasurementDescriptionDataType description : descriptionList.getMeasurementDescriptionData()) {
            Long measurementId = description.getMeasurementId();
            String scopeType = description.getScopeType();
            if (measurementId == null || scopeType == null) {
                continue;
            }
            for (MpcDataPoint dataPoint : ADDITIONAL_DATA_POINTS) {
                if (dataPoint.scopeType().equals(scopeType)) {
                    resolved.put(measurementId, dataPoint);
                    break;
                }
            }
        }
        return resolved;
    }

    private void applyMeasurement(RequestResult result, Optional<Long> powerMeasurementId,
            Map<Long, MpcDataPoint> additionalMeasurementIds, EEBusOhEntityHandler ohEntityHandler) {
        MeasurementListDataType list = result.getCmd().getMeasurementListData();
        if (list == null) {
            return;
        }
        for (MeasurementDataType data : list.getMeasurementData()) {
            Long measurementId = data.getMeasurementId();
            if (measurementId == null) {
                continue;
            }
            if (powerMeasurementId.isPresent() && powerMeasurementId.get().equals(measurementId)) {
                double watts = new ScaledNumberWrapper(data.getValue()).toDouble();
                // Per CONCEPT.md §4.2/§4.5 and docs/ADR/014-dynamic-client-role-channels.md:
                // delivered to the mpc#power Channel on the paired eebus:oh-entity Thing, created
                // on first use.
                ohEntityHandler.applyMpcPower(watts);
                logger.debug("MPC.power = {} W for oh-entity '{}'", watts, ohEntityHandler.getThing().getUID());
                continue;
            }
            MpcDataPoint dataPoint = additionalMeasurementIds.get(measurementId);
            if (dataPoint == null) {
                // Not one of our resolved data points - an id we never asked about, or one of
                // the three unresolvable phase-to-phase voltages (docs/ADR/037-mpc-additional-
                // datapoints.md Decision 1/5) - ignored, exactly as an unresolved id always was
                // before this change.
                continue;
            }
            double value = new ScaledNumberWrapper(data.getValue()).toDouble();
            // Per docs/ADR/037-mpc-additional-datapoints.md Decision 3: delivered to the
            // corresponding mpc Channel on the paired eebus:oh-entity Thing, created on first
            // use.
            ohEntityHandler.applyMpcMeasurement(dataPoint.channelId(), dataPoint.channelTypeUid(),
                    dataPoint.acceptedItemType(), dataPoint.label(), value, dataPoint.unit());
            logger.debug("MPC.{} = {} {} for oh-entity '{}'", dataPoint.channelId(), value, dataPoint.unit(),
                    ohEntityHandler.getThing().getUID());
        }
    }

    @Override
    public void close() {
        // No per-instance resources to release: subscriptions are tied to the SPINE
        // Communication/Device lifecycle, which EEBusHandler#dispose() already tears down as a
        // whole (communication.disconnect() / device.close()).
        subscribedPartners.clear();
    }
}
