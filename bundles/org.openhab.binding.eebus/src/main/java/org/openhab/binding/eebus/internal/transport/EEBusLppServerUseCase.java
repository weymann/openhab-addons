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
 * LPP (Limitation of Power Production) in the Server role ("Controllable System" actor) -
 * structurally identical to LPC (CONCEPT.md §5.4.2: same scenario/feature tables, mirrored
 * sign/naming), see {@link AbstractEEBusLimitControllableSystemUseCase} for the shared
 * implementation and {@link EEBusLpcServerUseCase} for the consumption-direction counterpart.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusLppServerUseCase extends AbstractEEBusLimitControllableSystemUseCase {

    public EEBusLppServerUseCase(EEBusMetadataService metadataService) {
        super(metadataService);
    }

    @Override
    protected String getShortCode() {
        return "LPP";
    }

    @Override
    protected String getUseCaseName() {
        return "LimitationOfPowerProduction";
    }

    @Override
    protected String getLimitDataPoint() {
        return "productionLimit";
    }

    @Override
    protected String getFailsafeLimitDataPoint() {
        return "failsafeProductionLimit";
    }

    @Override
    protected EnergyDirectionEnumType getLimitDirection() {
        return EnergyDirectionEnumType.PRODUCE;
    }

    @Override
    protected DeviceConfigurationKeyNameEnumType getFailsafeLimitKeyName() {
        return DeviceConfigurationKeyNameEnumType.FAILSAFE_PRODUCTION_ACTIVE_POWER_LIMIT;
    }
}
