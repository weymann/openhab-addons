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

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
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

    /**
     * @param initialFailsafeLimitWatts see
     *            {@link AbstractEEBusLimitControllableSystemUseCase#AbstractEEBusLimitControllableSystemUseCase}
     * @param initialFailsafeDurationMinimumSeconds see the same constructor's javadoc
     */
    public EEBusLppServerUseCase(EEBusMetadataService metadataService, String ohServiceId,
            Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver, double initialFailsafeLimitWatts,
            long initialFailsafeDurationMinimumSeconds) {
        super(metadataService, ohServiceId, ohEntityHandlerResolver, initialFailsafeLimitWatts,
                initialFailsafeDurationMinimumSeconds);
    }

    @Override
    protected String getShortCode() {
        return "LPP";
    }

    @Override
    protected String getUseCaseName() {
        // Confirmed 2026-08-05 against a real Hager Energy S10's discovery JSON (jeebus.spine's
        // DiscoveryLogger output, see docs/ADR/011-usecasename-lowercamelcase.md): the wire
        // format is lowerCamelCase, not PascalCase - was "LimitationOfPowerProduction" before.
        return "limitationOfPowerProduction";
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
    protected long getLimitId() {
        // Distinct from LPC's 0 - see docs/ADR/018-lpc-lpp-limitid-assignment.md. Before this
        // ADR, LPP shared LPC's hardcoded limitId=0, so its LoadControlLimitDescriptionData/
        // LoadControlLimitData entry silently overwrote LPC's on the shared LoadControl feature.
        return 1L;
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
