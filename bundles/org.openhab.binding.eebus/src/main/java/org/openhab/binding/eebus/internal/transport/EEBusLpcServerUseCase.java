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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyNameEnumType;
import org.openmuc.jeebus.spine.xsd.v1.EnergyDirectionEnumType;

/**
 * LPC (Limitation of Power Consumption) in the Server role ("Controllable System" actor) - see
 * {@link AbstractEEBusLimitControllableSystemUseCase} for the shared implementation (all three
 * mandatory scenarios: limit, failsafe values, mutual heartbeat).
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusLpcServerUseCase extends AbstractEEBusLimitControllableSystemUseCase {

    public EEBusLpcServerUseCase(EEBusMetadataService metadataService, String ohServiceId) {
        super(metadataService, ohServiceId);
    }

    @Override
    protected String getShortCode() {
        return "LPC";
    }

    @Override
    protected String getUseCaseName() {
        // Confirmed 2026-08-05 against a real Hager Energy S10's discovery JSON (jeebus.spine's
        // DiscoveryLogger output, see docs/ADR/011-usecasename-lowercamelcase.md): the wire
        // format is lowerCamelCase, not PascalCase - was "LimitationOfPowerConsumption" before.
        return "limitationOfPowerConsumption";
    }

    @Override
    protected String getLimitDataPoint() {
        return "consumptionLimit";
    }

    @Override
    protected String getFailsafeLimitDataPoint() {
        return "failsafeConsumptionLimit";
    }

    @Override
    protected EnergyDirectionEnumType getLimitDirection() {
        return EnergyDirectionEnumType.CONSUME;
    }

    @Override
    protected DeviceConfigurationKeyNameEnumType getFailsafeLimitKeyName() {
        return DeviceConfigurationKeyNameEnumType.FAILSAFE_CONSUMPTION_ACTIVE_POWER_LIMIT;
    }
}
