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
package org.openhab.binding.boschthermotechnology.internal.discovery;

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_GATEWAY_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_GATEWAY;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.dto.GatewayDto;
import org.openhab.binding.boschthermotechnology.internal.handler.AccountBridgeHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link GatewayDiscoveryService} lists the gateways registered to an authorized
 * {@code account} bridge (via {@code GET /gateways/}) and proposes a {@code gateway} Thing for
 * each one. One instance is created and registered per bridge by
 * {@code BoschThermotechnologyHandlerFactory} - see its {@code registerDiscoveryService} /
 * {@code unregisterDiscoveryService} methods.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class GatewayDiscoveryService extends AbstractDiscoveryService {

    private static final int TIMEOUT_SECONDS = 10;

    private final Logger logger = LoggerFactory.getLogger(GatewayDiscoveryService.class);
    private final AccountBridgeHandler bridgeHandler;

    public GatewayDiscoveryService(AccountBridgeHandler bridgeHandler) {
        super(Set.of(THING_TYPE_GATEWAY), TIMEOUT_SECONDS, false);
        this.bridgeHandler = bridgeHandler;
    }

    @Override
    protected void startScan() {
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        try {
            String accessToken = bridgeHandler.getValidAccessToken();
            for (GatewayDto gateway : bridgeHandler.getApiClient().listGateways(accessToken)) {
                String gatewayId = gateway.id;
                if (gatewayId == null || gatewayId.isBlank()) {
                    logger.debug("Skipping a gateway list entry without an id");
                    continue;
                }

                ThingUID thingUID = new ThingUID(THING_TYPE_GATEWAY, bridgeUID, gatewayId);
                DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                        .withProperty(CONFIG_GATEWAY_ID, gatewayId).withRepresentationProperty(CONFIG_GATEWAY_ID)
                        .withLabel("Bosch Thermotechnology Gateway " + gatewayId).build();
                thingDiscovered(result);
            }
        } catch (PointTApiException e) {
            logger.debug("Gateway discovery failed: {}", e.getMessage());
        }
    }
}
