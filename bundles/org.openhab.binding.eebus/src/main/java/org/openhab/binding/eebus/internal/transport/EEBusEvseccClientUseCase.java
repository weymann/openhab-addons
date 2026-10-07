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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
import org.openmuc.jeebus.spine.api.PresenceIndication;
import org.openmuc.jeebus.spine.api.UseCasePartner;
import org.openmuc.jeebus.spine.xsd.v1.CmdType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceClassificationManufacturerDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceDiagnosisStateDataType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;

/**
 * EVSECC (EVSE Commissioning and Configuration) in the Client ({@code CEM}) role, scoped to the
 * wallbox SKI of the HEMS Thing (docs/ADR/054). Scenario 1 (manufacturer information) and
 * scenario 2 (error state) of the remote {@code EVSE} actor are written to the Channel group
 * {@code <prefix>-evse}.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusEvseccClientUseCase extends AbstractEEBusEvClientUseCase {

    public EEBusEvseccClientUseCase(Supplier<Optional<EEBusOhEntityHandler>> hemsHandlerResolver, String partnerSki,
            String channelGroupPrefix, Function<String, Optional<String>> skiResolver) {
        super(hemsHandlerResolver, partnerSki, channelGroupPrefix + "-evse", skiResolver);
    }

    @Override
    public String getName() {
        return "evseCommissioningAndConfiguration";
    }

    @Override
    protected String remoteActor() {
        return "EVSE";
    }

    @Override
    protected Map<Long, PresenceIndication> scenarioRequirements() {
        return Map.of(2L, PresenceIndication.RECOMMENDED);
    }

    @Override
    protected Set<CommunicationPartnerFeatureRequirement> partnerFeatureRequirements() {
        return Set.of();
    }

    @Override
    protected void onPartnerAppeared(UseCasePartner partner, EEBusOhEntityHandler handler) {
        handler.recordDetectedUseCase(channelGroup(), "server");
        handler.applyEvSwitch(channelGroup(), "connected", "Connected", true);

        FeatureAddressType classification = partner
                .getCompleteFeatureAddress(FeatureTypeEnumType.DEVICE_CLASSIFICATION);
        if (classification != null) {
            readOnce(classification,
                    new CmdType()
                            .withDeviceClassificationManufacturerData(new DeviceClassificationManufacturerDataType()),
                    result -> applyManufacturerData(result, handler));
        }
        FeatureAddressType diagnosis = partner.getCompleteFeatureAddress(FeatureTypeEnumType.DEVICE_DIAGNOSIS);
        if (diagnosis != null) {
            readAndSubscribe(diagnosis, FeatureTypeEnumType.DEVICE_DIAGNOSIS,
                    new CmdType().withDeviceDiagnosisStateData(new DeviceDiagnosisStateDataType()),
                    result -> applyDiagnosisState(result, handler));
        }
    }

    @Override
    protected void onPartnerDisappeared(EEBusOhEntityHandler handler) {
        handler.applyEvSwitch(channelGroup(), "connected", "Connected", false);
    }
}
