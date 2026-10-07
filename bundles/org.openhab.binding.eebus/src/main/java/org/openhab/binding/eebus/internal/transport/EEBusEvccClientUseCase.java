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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
import org.openmuc.jeebus.spine.api.PresenceIndication;
import org.openmuc.jeebus.spine.api.RequestResult;
import org.openmuc.jeebus.spine.api.UseCasePartner;
import org.openmuc.jeebus.spine.xsd.v1.CmdType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceClassificationManufacturerDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDescriptionListDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueListDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceDiagnosisStateDataType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.IdentificationDataType;
import org.openmuc.jeebus.spine.xsd.v1.IdentificationListDataType;

/**
 * EVCC (EV Commissioning and Configuration) in the Client ({@code CEM}) role, scoped to the
 * wallbox SKI of the HEMS Thing (docs/ADR/054). Implemented scenarios of the remote {@code EV}
 * actor: 1 (connected), 2 (communication standard), 3 (asymmetric charging support), 4
 * (identification), 5 (manufacturer information) and 7 (sleep mode, via the operating state).
 * Scenario 6 (charging power limits) is deferred to the OPEV step. Written to the Channel group
 * {@code <prefix>-ev}.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusEvccClientUseCase extends AbstractEEBusEvClientUseCase {

    private static final String KEY_COMMUNICATION_STANDARD = "communicationStandard";
    private static final String KEY_ASYMMETRIC_CHARGING = "asymmetricChargingSupported";

    public EEBusEvccClientUseCase(Supplier<Optional<EEBusOhEntityHandler>> hemsHandlerResolver, String partnerSki,
            String channelGroupPrefix, Function<String, Optional<String>> skiResolver) {
        super(hemsHandlerResolver, partnerSki, channelGroupPrefix + "-ev", skiResolver);
    }

    @Override
    public String getName() {
        return "evCommissioningAndConfiguration";
    }

    @Override
    protected String remoteActor() {
        return "EV";
    }

    @Override
    protected Map<Long, PresenceIndication> scenarioRequirements() {
        return Map.of(1L, PresenceIndication.RECOMMENDED);
    }

    @Override
    protected Set<CommunicationPartnerFeatureRequirement> partnerFeatureRequirements() {
        return Set.of();
    }

    @Override
    protected void onPartnerAppeared(UseCasePartner partner, EEBusOhEntityHandler handler) {
        handler.recordDetectedUseCase(channelGroup(), "server");
        handler.applyEvSwitch(channelGroup(), "connected", "Connected", true);

        FeatureAddressType configuration = partner.getCompleteFeatureAddress(FeatureTypeEnumType.DEVICE_CONFIGURATION);
        if (configuration != null) {
            readOnce(configuration,
                    new CmdType().withDeviceConfigurationKeyValueDescriptionListData(
                            new DeviceConfigurationKeyValueDescriptionListDataType()),
                    descriptions -> subscribeConfiguration(configuration, descriptions, handler));
        }
        FeatureAddressType identification = partner.getCompleteFeatureAddress(FeatureTypeEnumType.IDENTIFICATION);
        if (identification != null) {
            readAndSubscribe(identification, FeatureTypeEnumType.IDENTIFICATION,
                    new CmdType().withIdentificationListData(new IdentificationListDataType()),
                    result -> applyIdentification(result, handler));
        }
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

    private void subscribeConfiguration(FeatureAddressType configuration, RequestResult descriptions,
            EEBusOhEntityHandler handler) {
        DeviceConfigurationKeyValueDescriptionListDataType list = descriptions.getCmd()
                .getDeviceConfigurationKeyValueDescriptionListData();
        if (list == null) {
            return;
        }
        Map<Long, String> keyNames = new HashMap<>();
        for (DeviceConfigurationKeyValueDescriptionDataType description : list
                .getDeviceConfigurationKeyValueDescriptionData()) {
            if (description.getKeyId() != null && description.getKeyName() != null) {
                keyNames.put(description.getKeyId(), description.getKeyName());
            }
        }
        readAndSubscribe(configuration, FeatureTypeEnumType.DEVICE_CONFIGURATION,
                new CmdType().withDeviceConfigurationKeyValueListData(new DeviceConfigurationKeyValueListDataType()),
                result -> applyConfiguration(result, keyNames, handler));
    }

    private void applyConfiguration(RequestResult result, Map<Long, String> keyNames, EEBusOhEntityHandler handler) {
        DeviceConfigurationKeyValueListDataType list = result.getCmd().getDeviceConfigurationKeyValueListData();
        if (list == null) {
            return;
        }
        for (DeviceConfigurationKeyValueDataType data : list.getDeviceConfigurationKeyValueData()) {
            String keyName = data.getKeyId() == null ? null : keyNames.get(data.getKeyId());
            if (keyName == null || data.getValue() == null) {
                continue;
            }
            if (KEY_COMMUNICATION_STANDARD.equals(keyName) && data.getValue().getString() != null) {
                handler.applyEvText(channelGroup(), "communication-standard", "Communication Standard",
                        data.getValue().getString());
            } else if (KEY_ASYMMETRIC_CHARGING.equals(keyName) && data.getValue().getBoolean() != null) {
                handler.applyEvSwitch(channelGroup(), "asymmetric-charging", "Asymmetric Charging Supported",
                        Boolean.TRUE.equals(data.getValue().getBoolean()));
            }
        }
    }

    private void applyIdentification(RequestResult result, EEBusOhEntityHandler handler) {
        IdentificationListDataType list = result.getCmd().getIdentificationListData();
        if (list == null || list.getIdentificationData().isEmpty()) {
            return;
        }
        IdentificationDataType first = list.getIdentificationData().get(0);
        text(handler, "identification", "Identification", first.getIdentificationValue());
        text(handler, "identification-type", "Identification Type", first.getIdentificationType());
    }
}
