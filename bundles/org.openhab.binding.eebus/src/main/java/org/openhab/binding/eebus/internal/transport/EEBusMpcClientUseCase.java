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
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.items.Metadata;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
import org.openmuc.jeebus.spine.api.Device;
import org.openmuc.jeebus.spine.api.Entity;
import org.openmuc.jeebus.spine.api.NodeManagement;
import org.openmuc.jeebus.spine.api.PresenceIndication;
import org.openmuc.jeebus.spine.api.RequestResult;
import org.openmuc.jeebus.spine.api.UseCasePartner;
import org.openmuc.jeebus.spine.spi.AllowedEntityTypes;
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
 * or energy management system) and mirrors its power reading into a local Item. This is the
 * consumer-side counterpart to {@link EEBusMpcServerUseCase} (CONCEPT.md §1, §5.4.1, §5.4.2,
 * §7 item 3/8 follow-up: Client-role wiring for {@code supportedUseCasesClient}).
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
 * found {@link UseCasePartner} is resolved back to a paired {@code eebus:peer} Thing UID via
 * the {@code peerThingUidResolver} passed into the constructor (composed in
 * {@code org.openhab.binding.eebus.internal.handler.EEBusHandler} from
 * {@link EEBusMdnsBrowser#skiForCommunicationAddress}), then matched against
 * {@code eebus="MPC.power" [peer="<uid>"]} Item metadata (CONCEPT.md §4.2).
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
@AllowedEntityTypes({ EntityTypeEnumType.CEM })
public class EEBusMpcClientUseCase implements UseCase {

    private final Logger logger = LoggerFactory.getLogger(EEBusMpcClientUseCase.class);
    private final EEBusMetadataService metadataService;
    private final Function<String, Optional<String>> peerThingUidResolver;

    @Inject
    private @Nullable Entity entity;

    private @Nullable FeatureAddressType address;
    private @Nullable Device device;

    public EEBusMpcClientUseCase(EEBusMetadataService metadataService,
            Function<String, Optional<String>> peerThingUidResolver) {
        this.metadataService = metadataService;
        this.peerThingUidResolver = peerThingUidResolver;
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
        // EEBusMpcServerUseCase#getName() for interop with another instance of this binding;
        // the exact wire-format use case name string is not independently verified against the
        // primary EEBUS spec (same caveat as the Server-role class).
        return "MonitoringOfPowerConsumption";
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
            Optional<String> peerThingUid = peerThingUidResolver.apply(partner.getCommunicationAddress());
            if (peerThingUid.isEmpty()) {
                logger.debug("MPC partner at {} could not be resolved to a paired eebus:peer Thing (mDNS not (yet) "
                        + "seeing it?), skipping", partner.getCommunicationAddress());
                continue;
            }
            Optional<Metadata> metadata = metadataService.find("MPC", "power", peerThingUid.get());
            if (metadata.isEmpty()) {
                logger.debug("No Item declares eebus=\"MPC.power\" [peer=\"{}\"] - ignoring detected MPC partner",
                        peerThingUid.get());
                continue;
            }
            String itemName = EEBusMetadataService.itemNameOf(metadata.get());
            subscribe(featureAddress, itemName);
        }
    }

    private void subscribe(FeatureAddressType featureAddress, String itemName) {
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();

        // Resolve the description once (measurementId -> scopeType/measurementType mapping, see
        // class javadoc), then subscribe for ongoing MeasurementListData notifications using
        // that ID. An initial read is issued as well so the Item gets a value immediately rather
        // than waiting for the peer's next spontaneous notification.
        CmdType descriptionReadCmd = new CmdType()
                .withMeasurementDescriptionListData(new MeasurementDescriptionListDataType());
        nodeManagement.requestRead(featureAddress, descriptionReadCmd).thenAccept(result -> {
            Optional<Long> measurementId = resolvePowerMeasurementId(result);
            if (measurementId.isEmpty()) {
                logger.debug("MPC partner's MeasurementDescriptionListData has no AC-power-total/Power entry, "
                        + "ignoring feature at {}", featureAddress);
                return;
            }
            long id = measurementId.get();

            CmdType valueReadCmd = new CmdType().withMeasurementListData(new MeasurementListDataType());
            nodeManagement.requestRead(featureAddress, valueReadCmd)
                    .thenAccept(valueResult -> applyMeasurement(valueResult, id, itemName)).exceptionally(ex -> {
                        logger.debug("Initial MPC.power read failed for Item '{}'", itemName, ex);
                        return null;
                    });

            nodeManagement.requestSubscription(featureAddress, FeatureTypeEnumType.MEASUREMENT,
                    notification -> applyMeasurement(notification, id, itemName)).exceptionally(ex -> {
                        logger.warn("Failed to subscribe to MPC.power for Item '{}'", itemName, ex);
                        return null;
                    });
        }).exceptionally(ex -> {
            logger.warn("Failed to read MeasurementDescriptionListData for MPC partner (Item '{}')", itemName, ex);
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

    private void applyMeasurement(RequestResult result, long measurementId, String itemName) {
        MeasurementListDataType list = result.getCmd().getMeasurementListData();
        if (list == null) {
            return;
        }
        Optional<MeasurementDataType> data = list.getMeasurementData().stream()
                .filter(d -> Long.valueOf(measurementId).equals(d.getMeasurementId())).findFirst();
        if (data.isEmpty()) {
            logger.debug("MeasurementListData notification had no entry for id {}, ignoring", measurementId);
            return;
        }
        double watts = new ScaledNumberWrapper(data.get().getValue()).toDouble();
        metadataService.updateState(itemName, new QuantityType<>(watts, Units.WATT));
    }

    @Override
    public void close() {
        // No per-instance resources to release: subscriptions are tied to the SPINE
        // Communication/Device lifecycle, which EEBusHandler#dispose() already tears down as a
        // whole (communication.disconnect() / device.close()).
    }
}
