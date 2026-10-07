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
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
import org.openmuc.jeebus.spine.api.Device;
import org.openmuc.jeebus.spine.api.Entity;
import org.openmuc.jeebus.spine.api.PresenceIndication;
import org.openmuc.jeebus.spine.api.RequestResult;
import org.openmuc.jeebus.spine.api.UseCasePartner;
import org.openmuc.jeebus.spine.spi.FeatureRequirement;
import org.openmuc.jeebus.spine.spi.Inject;
import org.openmuc.jeebus.spine.spi.UseCase;
import org.openmuc.jeebus.spine.xsd.v1.CmdType;
import org.openmuc.jeebus.spine.xsd.v1.EntityTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common base of the three read-only EV Client-role use cases of the HEMS Thing (docs/ADR/054):
 * EVSECC (EVSE Commissioning and Configuration), EVCC (EV Commissioning and Configuration) and
 * EVCEM (EV Charging Electricity Measurement). In all three the local entity plays the
 * {@code CEM} actor (client) and the wallbox side plays the server actor ({@code EVSE} or
 * {@code EV}).
 *
 * <p>
 * Like the scoped Energy Guard use cases (docs/ADR/053), an instance only reacts to the partner
 * whose SKI equals {@code partnerSki}; all values are delivered to the HEMS Thing's handler into
 * Channel group {@code <prefix>-<group>}. Discovery-based: {@code UseCaseListener#onUpdate} is
 * only invoked by jeebus.spine for a non-empty partner list, so a disappearing partner (EV
 * unplugged) is detected only when a later, non-empty list no longer contains it - see
 * {@link #onPartnersChanged}.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractEEBusEvClientUseCase implements UseCase {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final Supplier<Optional<EEBusOhEntityHandler>> hemsHandlerResolver;
    private final String partnerSki;
    private final String channelGroup;
    private final Function<String, Optional<String>> skiResolver;

    /** Partner keys (communication address + entity address) currently known as present. */
    private final Set<String> presentPartners = ConcurrentHashMap.newKeySet();

    @Inject
    private @Nullable Entity entity;

    private @Nullable FeatureAddressType address;
    private @Nullable Device device;

    /**
     * @param hemsHandlerResolver resolves the {@code eebus:oh-hems-entity} Thing's live handler
     * @param partnerSki SKI of the only device this instance reacts to (the wallbox)
     * @param channelGroup the Channel Group id the values are written to, e.g. {@code wallbox-evse}
     * @param skiResolver resolves a SPINE communication address to the partner's SKI
     */
    protected AbstractEEBusEvClientUseCase(Supplier<Optional<EEBusOhEntityHandler>> hemsHandlerResolver,
            String partnerSki, String channelGroup, Function<String, Optional<String>> skiResolver) {
        this.hemsHandlerResolver = hemsHandlerResolver;
        this.partnerSki = partnerSki;
        this.channelGroup = channelGroup;
        this.skiResolver = skiResolver;
    }

    /** @return the use case name as advertised on the wire (lowerCamelCase) */
    @Override
    public abstract String getName();

    /** @return the actor name the remote partner plays, e.g. {@code EVSE} or {@code EV} */
    protected abstract String remoteActor();

    /** @return the scenario requirements used to match remote partners */
    protected abstract Map<Long, PresenceIndication> scenarioRequirements();

    /** @return the feature requirements used to match remote partners */
    protected abstract Set<CommunicationPartnerFeatureRequirement> partnerFeatureRequirements();

    /**
     * Called once per newly appeared partner (already scoped to {@code partnerSki}).
     *
     * @param partner the partner entity
     * @param handler the HEMS Thing's handler
     */
    protected abstract void onPartnerAppeared(UseCasePartner partner, EEBusOhEntityHandler handler);

    /**
     * Called when a previously present partner is missing from a later partner list.
     *
     * @param handler the HEMS Thing's handler
     */
    protected abstract void onPartnerDisappeared(EEBusOhEntityHandler handler);

    /** @return the Channel Group id this instance writes to */
    protected String channelGroup() {
        return channelGroup;
    }

    @Override
    public String getActor() {
        return "CEM";
    }

    @Override
    public String getVersion() {
        return "1.0.1";
    }

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
        localEntity.getDevice().getNodeManagement().addUseCaseListener(this::onPartnersChanged, getName(),
                remoteActor(), scenarioRequirements(), partnerFeatureRequirements());
    }

    private void onPartnersChanged(List<UseCasePartner> partners) {
        Optional<EEBusOhEntityHandler> handlerOpt = hemsHandlerResolver.get();
        if (handlerOpt.isEmpty()) {
            logger.debug("{}: HEMS Thing handler not available yet, ignoring partner notification", getName());
            return;
        }
        EEBusOhEntityHandler handler = handlerOpt.get();
        Set<String> seenNow = ConcurrentHashMap.newKeySet();
        for (UseCasePartner partner : partners) {
            Optional<String> ski = skiResolver.apply(partner.getCommunicationAddress());
            if (ski.isEmpty() || !partnerSki.equalsIgnoreCase(ski.get())) {
                logger.trace("{}: ignoring partner at {} (SKI {}, scoped to {})", getName(),
                        partner.getCommunicationAddress(), ski.orElse("unresolved"), partnerSki);
                continue;
            }
            String key = partner.getCommunicationAddress() + "/"
                    + partner.getEntityInfo().getDescription().getEntityAddress().getEntity();
            seenNow.add(key);
            if (presentPartners.add(key)) {
                logger.debug("{}: partner {} appeared", getName(), key);
                onPartnerAppeared(partner, handler);
            }
        }
        if (!seenNow.isEmpty() || !presentPartners.isEmpty()) {
            for (String key : Set.copyOf(presentPartners)) {
                if (!seenNow.contains(key) && presentPartners.remove(key)) {
                    logger.debug("{}: partner {} disappeared", getName(), key);
                    onPartnerDisappeared(handler);
                }
            }
        }
    }

    /**
     * Reads one function of a remote feature once and subscribes to its notifications; both
     * results are passed to {@code consumer}.
     *
     * @param featureAddress the remote feature
     * @param featureType the feature type to subscribe to
     * @param readCmd an empty command naming the function to read
     * @param consumer receives the read reply and every notification
     */
    protected void readAndSubscribe(FeatureAddressType featureAddress, FeatureTypeEnumType featureType, CmdType readCmd,
            java.util.function.Consumer<RequestResult> consumer) {
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        var nodeManagement = localDevice.getNodeManagement();
        nodeManagement.requestRead(featureAddress, readCmd).thenAccept(consumer).exceptionally(ex -> {
            logger.debug("{}: initial read of {} failed", getName(), featureType, ex);
            return null;
        });
        nodeManagement.requestSubscription(featureAddress, featureType, consumer::accept).handle((r, ex) -> {
            if (ex != null) {
                logger.debug("{}: subscription to {} failed", getName(), featureType, ex);
            }
            return null;
        });
    }

    /**
     * Reads one function of a remote feature once, without subscribing.
     *
     * @param featureAddress the remote feature
     * @param readCmd an empty command naming the function to read
     * @param consumer receives the reply
     */
    protected void readOnce(FeatureAddressType featureAddress, CmdType readCmd,
            java.util.function.Consumer<RequestResult> consumer) {
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        localDevice.getNodeManagement().requestRead(featureAddress, readCmd).thenAccept(consumer).exceptionally(ex -> {
            logger.debug("{}: read failed", getName(), ex);
            return null;
        });
    }

    /**
     * Writes the manufacturer data of {@code result} (if it carries any) to the Channels
     * {@code device-name}, {@code vendor-name}, {@code brand-name}, {@code serial-number},
     * {@code software-revision}, {@code hardware-revision} and {@code manufacturer-label}.
     */
    protected void applyManufacturerData(RequestResult result, EEBusOhEntityHandler handler) {
        var data = result.getCmd().getDeviceClassificationManufacturerData();
        if (data == null) {
            return;
        }
        text(handler, "device-name", "Device Name", data.getDeviceName());
        text(handler, "vendor-name", "Vendor Name", data.getVendorName());
        text(handler, "brand-name", "Brand Name", data.getBrandName());
        text(handler, "serial-number", "Serial Number", data.getSerialNumber());
        text(handler, "software-revision", "Software Revision", data.getSoftwareRevision());
        text(handler, "hardware-revision", "Hardware Revision", data.getHardwareRevision());
        text(handler, "manufacturer-label", "Manufacturer Label", data.getManufacturerLabel());
    }

    /** Writes the {@code operating-state} and {@code last-error-code} Channels from a DeviceDiagnosis state reply. */
    protected void applyDiagnosisState(RequestResult result, EEBusOhEntityHandler handler) {
        var data = result.getCmd().getDeviceDiagnosisStateData();
        if (data == null) {
            return;
        }
        text(handler, "operating-state", "Operating State", data.getOperatingState());
        text(handler, "last-error-code", "Last Error Code", data.getLastErrorCode());
    }

    /** Writes a text Channel; a {@code null} value is skipped (the Channel is not created). */
    protected void text(EEBusOhEntityHandler handler, String channelId, String label, @Nullable String value) {
        if (value != null) {
            handler.applyEvText(channelGroup, channelId, label, value);
        }
    }

    @Override
    public void close() {
        presentPartners.clear();
    }
}
