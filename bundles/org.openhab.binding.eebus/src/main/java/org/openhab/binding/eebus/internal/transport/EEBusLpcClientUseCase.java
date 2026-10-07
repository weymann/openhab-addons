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
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openmuc.jeebus.spine.xsd.v1.EnergyDirectionEnumType;

/**
 * LPC (Limitation of Power Consumption) in the Client role ("EnergyGuard" actor) - see
 * {@link AbstractEEBusLimitEnergyGuardUseCase} for the shared implementation and
 * {@link EEBusLpcServerUseCase} for the Server-role ("ControllableSystem") counterpart.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusLpcClientUseCase extends AbstractEEBusLimitEnergyGuardUseCase {

    /**
     * @param ohEntityHandlerResolver see {@link AbstractEEBusLimitEnergyGuardUseCase}'s constructor
     * @param writeSourceHandlerResolver see {@link AbstractEEBusLimitEnergyGuardUseCase}'s constructor
     * @param metadataService see {@link AbstractEEBusLimitEnergyGuardUseCase}'s constructor
     */
    public EEBusLpcClientUseCase(Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver,
            Supplier<Optional<EEBusOhEntityHandler>> writeSourceHandlerResolver, EEBusMetadataService metadataService) {
        super(ohEntityHandlerResolver, writeSourceHandlerResolver, metadataService);
    }

    /**
     * Target-scoped variant for the HEMS convenience Thing - see
     * {@link AbstractEEBusLimitEnergyGuardUseCase}'s scoped constructor and
     * docs/ADR/053-hems-convenience-entity.md.
     */
    public EEBusLpcClientUseCase(Function<String, Optional<EEBusOhEntityHandler>> ohEntityHandlerResolver,
            Supplier<Optional<EEBusOhEntityHandler>> writeSourceHandlerResolver, EEBusMetadataService metadataService,
            String partnerSki, String channelGroupPrefix, Function<String, Optional<String>> skiResolver) {
        super(ohEntityHandlerResolver, writeSourceHandlerResolver, metadataService, partnerSki, channelGroupPrefix,
                skiResolver);
    }

    @Override
    protected String getShortCode() {
        return EEBusBindingConstants.CHANNEL_GROUP_LPC;
    }

    @Override
    protected String getUseCaseName() {
        // lowerCamelCase wire format - see EEBusLpcServerUseCase#getUseCaseName()'s identical
        // note (confirmed against a real Hager Energy S10, docs/ADR/011).
        return "limitationOfPowerConsumption";
    }

    @Override
    protected EnergyDirectionEnumType getLimitDirection() {
        // See docs/ADR/018-lpc-lpp-limitid-assignment.md - used to resolve which of the peer's
        // LoadControl limit entries is this use case's, since limitId itself is not a reliable
        // discriminator (mirrors EEBusLpcServerUseCase#getLimitDirection()).
        return EnergyDirectionEnumType.CONSUME;
    }
}
