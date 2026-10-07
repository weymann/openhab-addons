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
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openhab.core.library.unit.Units;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
import org.openmuc.jeebus.spine.api.PresenceIndication;
import org.openmuc.jeebus.spine.api.RequestResult;
import org.openmuc.jeebus.spine.api.UseCasePartner;
import org.openmuc.jeebus.spine.utils.datatypes.ScaledNumberWrapper;
import org.openmuc.jeebus.spine.xsd.v1.CmdType;
import org.openmuc.jeebus.spine.xsd.v1.ElectricalConnectionParameterDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.ElectricalConnectionParameterDescriptionListDataType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementDescriptionListDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementListDataType;
import org.openmuc.jeebus.spine.xsd.v1.ScopeTypeEnumType;

/**
 * EVCEM (EV Charging Electricity Measurement) in the Client ({@code CEM}) role, scoped to the
 * wallbox SKI of the HEMS Thing (docs/ADR/054). Reads the remote {@code EV} actor's charging
 * current per phase (scenario 1, scope {@code acCurrent}, phase from the ElectricalConnection
 * parameter description), charging power (scenario 2, {@code acPower}) and charged energy
 * (scenario 3, {@code charge}) into the Channel group {@code <prefix>-evcem}.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusEvcemClientUseCase extends AbstractEEBusEvClientUseCase {

    /** What a resolved measurement id is written to. */
    private record Target(String channelId, String label, String itemType, javax.measure.Unit<?> unit,
            org.openhab.core.thing.type.ChannelTypeUID type) {
    }

    public EEBusEvcemClientUseCase(Supplier<Optional<EEBusOhEntityHandler>> hemsHandlerResolver, String partnerSki,
            String channelGroupPrefix, Function<String, Optional<String>> skiResolver) {
        super(hemsHandlerResolver, partnerSki, channelGroupPrefix + "-evcem", skiResolver);
    }

    @Override
    public String getName() {
        // Wire name confirmed against an evcc discovery dump (the TS title is "EV Charging Electricity Measurement").
        return "measurementOfElectricityDuringEvCharging";
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
        FeatureAddressType measurement = partner.getCompleteFeatureAddress(FeatureTypeEnumType.MEASUREMENT);
        if (measurement == null) {
            logger.debug("EVCEM partner has no Measurement feature, skipping");
            return;
        }
        FeatureAddressType electrical = partner.getCompleteFeatureAddress(FeatureTypeEnumType.ELECTRICAL_CONNECTION);
        if (electrical == null) {
            resolveMeasurements(measurement, Map.of(), handler);
            return;
        }
        readOnce(electrical,
                new CmdType().withElectricalConnectionParameterDescriptionListData(
                        new ElectricalConnectionParameterDescriptionListDataType()),
                result -> resolveMeasurements(measurement, phasesByMeasurementId(result), handler));
    }

    @Override
    protected void onPartnerDisappeared(EEBusOhEntityHandler handler) {
        // Last measured values stay on their Channels; EVCC's "connected" Channel reports the state.
    }

    private Map<Long, String> phasesByMeasurementId(RequestResult result) {
        Map<Long, String> phases = new HashMap<>();
        ElectricalConnectionParameterDescriptionListDataType list = result.getCmd()
                .getElectricalConnectionParameterDescriptionListData();
        if (list != null) {
            for (ElectricalConnectionParameterDescriptionDataType parameter : list
                    .getElectricalConnectionParameterDescriptionData()) {
                if (parameter.getMeasurementId() != null && parameter.getAcMeasuredPhases() != null) {
                    phases.put(parameter.getMeasurementId(), parameter.getAcMeasuredPhases());
                }
            }
        }
        return phases;
    }

    private void resolveMeasurements(FeatureAddressType measurement, Map<Long, String> phases,
            EEBusOhEntityHandler handler) {
        readOnce(measurement,
                new CmdType().withMeasurementDescriptionListData(new MeasurementDescriptionListDataType()), result -> {
                    Map<Long, Target> targets = resolveTargets(result, phases);
                    if (targets.isEmpty()) {
                        logger.debug("EVCEM partner's measurement description has no resolvable data point");
                        return;
                    }
                    readAndSubscribe(measurement, FeatureTypeEnumType.MEASUREMENT,
                            new CmdType().withMeasurementListData(new MeasurementListDataType()),
                            values -> applyValues(values, targets, handler));
                });
    }

    private Map<Long, Target> resolveTargets(RequestResult result, Map<Long, String> phases) {
        Map<Long, Target> targets = new HashMap<>();
        MeasurementDescriptionListDataType list = result.getCmd().getMeasurementDescriptionListData();
        if (list == null) {
            return targets;
        }
        for (MeasurementDescriptionDataType description : list.getMeasurementDescriptionData()) {
            Long id = description.getMeasurementId();
            String scope = description.getScopeType();
            if (id == null || scope == null) {
                continue;
            }
            if (ScopeTypeEnumType.AC_POWER.value().equals(scope)) {
                targets.put(id, new Target("power", "Charging Power", "Number:Power", Units.WATT,
                        EEBusBindingConstants.CHANNEL_TYPE_UID_EV_POWER));
            } else if (ScopeTypeEnumType.CHARGE.value().equals(scope)) {
                targets.put(id, new Target("energy-charged", "Charged Energy", "Number:Energy", Units.WATT_HOUR,
                        EEBusBindingConstants.CHANNEL_TYPE_UID_EV_ENERGY));
            } else if (ScopeTypeEnumType.AC_CURRENT.value().equals(scope)) {
                String phase = phases.get(id);
                if (phase != null && ("a".equalsIgnoreCase(phase) || "b".equalsIgnoreCase(phase)
                        || "c".equalsIgnoreCase(phase))) {
                    String letter = phase.toLowerCase();
                    targets.put(id,
                            new Target("current-phase-" + letter,
                                    "Charging Current (Phase " + letter.toUpperCase() + ")", "Number:ElectricCurrent",
                                    Units.AMPERE, EEBusBindingConstants.CHANNEL_TYPE_UID_EV_CURRENT));
                }
            }
        }
        return targets;
    }

    private void applyValues(RequestResult result, Map<Long, Target> targets, EEBusOhEntityHandler handler) {
        MeasurementListDataType list = result.getCmd().getMeasurementListData();
        if (list == null) {
            return;
        }
        for (MeasurementDataType data : list.getMeasurementData()) {
            Target target = data.getMeasurementId() == null ? null : targets.get(data.getMeasurementId());
            if (target == null || data.getValue() == null) {
                continue;
            }
            double value = new ScaledNumberWrapper(data.getValue()).toDouble();
            handler.applyEvQuantity(channelGroup(), target.channelId(), target.type(), target.itemType(),
                    target.label(), value, target.unit());
        }
    }
}
