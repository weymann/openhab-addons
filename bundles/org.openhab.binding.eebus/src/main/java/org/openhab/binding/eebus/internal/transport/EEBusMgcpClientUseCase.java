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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
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
 * Detects the MGCP (Monitoring of Grid Connection Point) use case in the **Client role** - i.e.
 * openHAB looks for a paired peer offering MGCP as the "GridConnectionPoint" server actor (e.g.
 * a real energy meter or hybrid inverter at the grid connection point) and reads its total
 * active power measurement via SPINE subscription. See docs/ADR/040-mgcp-client-usecase.md and
 * docs/changes/mgcp-client-usecase/proposal.md.
 *
 * <p>
 * <strong>Motivation, deliberately narrow scope</strong>: unlike MPC (docs/ADR/037), this use
 * case was added specifically as an independent read path to verify general SHIP/SPINE
 * communication against a real device whose MPC Server role could not be confirmed
 * (docs/changes/mgcp-client-usecase/proposal.md "Intent"). Of MGCP's seven Scenarios, only
 * Scenario 2 (Total Active Power, {@code [MGCP-021]}) is implemented here - the remaining six
 * are explicitly out of scope for this MVP, see that proposal's "Scope" section for the reasons
 * (Scenario 1 is {@code DeviceConfiguration}-based rather than {@code Measurement}-based;
 * Scenarios 5/6 need a harder {@code ElectricalConnection}-to-{@code Measurement} join via
 * {@code acMeasuredPhases} that no existing class in this binding implements yet).
 * </p>
 *
 * <p>
 * <strong>How detection/routing works</strong>: structurally identical to
 * {@link EEBusMpcClientUseCase} - see that class's javadoc "How detection works" and "Per-peer
 * routing" for the shared jeebus.spine mechanics ({@code Entity#addUseCase(UseCase)},
 * {@link Inject}, {@link #setup()}, {@link #onUseCasePartnersFound(List)}). Data is delivered to
 * {@link EEBusOhEntityHandler#applyMgcpMeasurement(double)}, which creates the
 * {@code mgcp#total-active-power} Channel on first use and updates its state.
 * </p>
 *
 * <p>
 * <strong>Peer feature requirements</strong>: per MGCP TS Table 22, a peer supporting Scenario 2
 * must offer both the {@code Measurement} feature (as MPC's peer does) <em>and</em> the
 * {@code ElectricalConnection} feature (Mandatory for both, even though this MVP does not itself
 * read any {@code ElectricalConnection} data yet - see docs/ADR/040-mgcp-client-usecase.md
 * Decision 3). The measurement ID for total active power is resolved the same way
 * {@link EEBusMpcClientUseCase#resolvePowerMeasurementId} resolves its own {@code power}: first
 * by {@code scopeType == AC_POWER_TOTAL} (the same {@link ScopeTypeEnumType} value MPC's own
 * {@code power} Channel already uses), falling back to {@code measurementType == Power}, then to
 * the sole entry if only one description exists.
 * </p>
 *
 * <p>
 * <strong>Sign convention</strong>: MGCP Scenario 2 follows the same "load convention" MPC's own
 * {@code power} Channel already uses ({@code [MGCP-001]}/{@code [MPC-001]}, both TS §2.5.1):
 * positive values are consumption from the grid, negative values are feed-in - see
 * docs/changes/mgcp-client-usecase/specs/mgcp-client-usecase/spec.md.
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
public class EEBusMgcpClientUseCase implements UseCase {

    private final Logger logger = LoggerFactory.getLogger(EEBusMgcpClientUseCase.class);
    private final Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver;

    /**
     * Communication addresses ({@link UseCasePartner#getCommunicationAddress()}) already fully
     * subscribed by {@link #onUseCasePartnersFound} - same 2026-08-21 idempotency fix and
     * rationale as {@link EEBusMpcClientUseCase#subscribedPartners}, applied here from the start
     * since it is now established binding practice for every Client-role use case that listens
     * via {@code NodeManagement#addUseCaseListener}.
     */
    private final Set<String> subscribedPartners = ConcurrentHashMap.newKeySet();

    @Inject
    private @Nullable Entity entity;

    private @Nullable FeatureAddressType address;
    private @Nullable Device device;

    /**
     * @param ohEntityHandlerResolver resolves a SPINE {@code communicationAddress} to the paired
     *            {@code eebus:oh-entity} Thing's {@link EEBusOhEntityHandler}, if any - see
     *            {@link EEBusMpcClientUseCase} class javadoc "Per-peer routing".
     */
    public EEBusMgcpClientUseCase(Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver) {
        this.ohEntityHandlerResolver = ohEntityHandlerResolver;
    }

    @Override
    public String getActor() {
        // "MonitoringAppliance" - the Client Actor of MGCP per CONCEPT.md §5.4.1, identical to
        // MPC's own Client Actor (both use cases share this actor for the monitoring-appliance
        // role, confirmed against the same verified eebus-go reference already cited for MPC).
        return "MonitoringAppliance";
    }

    @Override
    public String getName() {
        // Must match the use case name a real peer's Server role advertises. Confirmed against a
        // real Hager Energy S10's discovery JSON (hagers10.json, entity [6]): useCaseName
        // "monitoringOfGridConnectionPoint", lowerCamelCase - same wire-format convention already
        // documented for MPC (docs/ADR/011-usecasename-lowercamelcase.md).
        return "monitoringOfGridConnectionPoint";
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
        // MVP scope: Scenario 2 (Total Active Power) only - see class javadoc "Motivation,
        // deliberately narrow scope" and docs/changes/mgcp-client-usecase/proposal.md.
        return List.of(2L);
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
        // The generic client feature "needed by the client actor of most use cases" - identical
        // rationale to EEBusMpcClientUseCase#getFeatureRequirements.
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

        // "GridConnectionPoint" - the actor we expect a remote peer to play, i.e. MGCP's Server
        // Actor per CONCEPT.md §5.4.1 - unlike EEBusMpcClientUseCase/EEBusMpcServerUseCase's
        // still-open "CEM" discrepancy (flagged, not yet fixed - see project notes), this class
        // uses the catalog-matching value from the start, verified 2026-09-03 against
        // hagers10.json entity [6] ("actor": "GridConnectionPoint").
        localEntity.getDevice().getNodeManagement().addUseCaseListener(this::onUseCasePartnersFound, getName(),
                "GridConnectionPoint", Map.of(2L, PresenceIndication.MANDATORY), Set.of(
                        new CommunicationPartnerFeatureRequirement(FeatureTypeEnumType.MEASUREMENT,
                                Map.of(FunctionEnumType.MEASUREMENT_LIST_DATA, Map.of(2L, PresenceIndication.MANDATORY),
                                        FunctionEnumType.MEASUREMENT_DESCRIPTION_LIST_DATA,
                                        Map.of(2L, PresenceIndication.MANDATORY))),
                        new CommunicationPartnerFeatureRequirement(FeatureTypeEnumType.ELECTRICAL_CONNECTION,
                                Map.of(FunctionEnumType.ELECTRICAL_CONNECTION_DESCRIPTION_LIST_DATA,
                                        Map.of(2L, PresenceIndication.MANDATORY),
                                        FunctionEnumType.ELECTRICAL_CONNECTION_PARAMETER_DESCRIPTION_LIST_DATA,
                                        Map.of(2L, PresenceIndication.MANDATORY)))));
    }

    private void onUseCasePartnersFound(List<UseCasePartner> partners) {
        for (UseCasePartner partner : partners) {
            FeatureAddressType featureAddress = partner.getCompleteFeatureAddress(FeatureTypeEnumType.MEASUREMENT);
            if (featureAddress == null) {
                logger.debug("MGCP partner at {} has no Measurement feature address, skipping",
                        partner.getCommunicationAddress());
                continue;
            }
            Optional<EEBusOhEntityHandler> ohEntityHandler = ohEntityHandlerResolver
                    .apply(partner.getCommunicationAddress());
            if (ohEntityHandler.isEmpty()) {
                logger.debug("MGCP partner at {} could not be resolved to a paired eebus:oh-entity Thing (mDNS not "
                        + "(yet) seeing it?), skipping", partner.getCommunicationAddress());
                continue;
            }
            if (!subscribedPartners.add(partner.getCommunicationAddress())) {
                logger.debug("MGCP partner at {} already subscribed, ignoring duplicate use-case-partner notification",
                        partner.getCommunicationAddress());
                continue;
            }
            EEBusOhEntityHandler handler = ohEntityHandler.get();
            // Value is the actor role the *peer* plays ("GridConnectionPoint"/Server per
            // setup() above), not openHAB's own role - same convention as
            // EEBusMpcClientUseCase#onUseCasePartnersFound.
            handler.recordDetectedUseCase(EEBusBindingConstants.USE_CASE_KEY_MGCP, "server");
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
        // class javadoc "Peer feature requirements"), then subscribe for ongoing
        // MeasurementListData notifications. An initial read is issued as well so the eventual
        // Channel gets a value immediately rather than waiting for the peer's next spontaneous
        // notification - mirrors EEBusMpcClientUseCase#subscribe.
        CmdType descriptionReadCmd = new CmdType()
                .withMeasurementDescriptionListData(new MeasurementDescriptionListDataType());
        nodeManagement.requestRead(featureAddress, descriptionReadCmd).thenAccept(result -> {
            Optional<Long> totalActivePowerMeasurementId = resolveTotalActivePowerMeasurementId(result);
            if (totalActivePowerMeasurementId.isEmpty()) {
                logger.debug("MGCP partner's MeasurementDescriptionListData has no resolvable Total Active Power data "
                        + "point, ignoring feature at {}", featureAddress);
                return;
            }

            CmdType valueReadCmd = new CmdType().withMeasurementListData(new MeasurementListDataType());
            nodeManagement.requestRead(featureAddress, valueReadCmd).thenAccept(
                    valueResult -> applyMeasurement(valueResult, totalActivePowerMeasurementId.get(), ohEntityHandler))
                    .exceptionally(ex -> {
                        logger.debug("Initial MGCP read failed for oh-entity '{}'", ohEntityHandler.getThing().getUID(),
                                ex);
                        return null;
                    });

            nodeManagement.requestSubscription(featureAddress, FeatureTypeEnumType.MEASUREMENT,
                    notification -> applyMeasurement(notification, totalActivePowerMeasurementId.get(),
                            ohEntityHandler))
                    .handle((subscribeResult, ex) -> {
                        if (ex != null) {
                            logger.warn("Failed to subscribe to MGCP measurements for oh-entity '{}'",
                                    ohEntityHandler.getThing().getUID(), ex);
                        }
                        return null;
                    });
        }).exceptionally(ex -> {
            logger.warn("Failed to read MeasurementDescriptionListData for MGCP partner (oh-entity '{}')",
                    ohEntityHandler.getThing().getUID(), ex);
            return null;
        });
    }

    /**
     * @return the measurement ID whose description matches total AC power, per the same
     *         fallback chain as {@link EEBusMpcClientUseCase#resolvePowerMeasurementId}
     *         ({@code scopeType == AC_POWER_TOTAL}, then {@code measurementType == Power}, then
     *         the sole entry if only one description exists).
     */
    private Optional<Long> resolveTotalActivePowerMeasurementId(RequestResult result) {
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

    private void applyMeasurement(RequestResult result, Long totalActivePowerMeasurementId,
            EEBusOhEntityHandler ohEntityHandler) {
        MeasurementListDataType list = result.getCmd().getMeasurementListData();
        if (list == null) {
            return;
        }
        for (MeasurementDataType data : list.getMeasurementData()) {
            Long measurementId = data.getMeasurementId();
            if (measurementId == null || !totalActivePowerMeasurementId.equals(measurementId)) {
                continue;
            }
            double watts = new ScaledNumberWrapper(data.getValue()).toDouble();
            // Per docs/ADR/040-mgcp-client-usecase.md Decision 4: delivered to the
            // mgcp#total-active-power Channel on the paired eebus:oh-entity Thing, created on
            // first use.
            ohEntityHandler.applyMgcpMeasurement(watts);
            logger.debug("MGCP.total-active-power = {} W for oh-entity '{}'", watts,
                    ohEntityHandler.getThing().getUID());
        }
    }

    @Override
    public void close() {
        // No per-instance resources to release: subscriptions are tied to the SPINE
        // Communication/Device lifecycle, which EEBusHandler#dispose() already tears down as a
        // whole (communication.disconnect() / device.close()) - identical rationale to
        // EEBusMpcClientUseCase#close().
        subscribedPartners.clear();
    }
}
