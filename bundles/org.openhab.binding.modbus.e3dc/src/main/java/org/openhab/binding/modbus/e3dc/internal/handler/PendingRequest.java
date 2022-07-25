/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.modbus.e3dc.internal.handler;

import java.time.LocalDateTime;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PendingRequest} holds user data requet
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class PendingRequest {
    private final Logger logger = LoggerFactory.getLogger(PendingRequest.class);
    private static final int UPDATE_LOCK_MAX_TIME_SEC = 30;
    private ChannelUID channelUID;
    private OnOffType command;
    private LocalDateTime expirationDateTime;

    public PendingRequest(ChannelUID cuid, OnOffType c) {
        channelUID = cuid;
        command = c;
        expirationDateTime = LocalDateTime.now().plusSeconds(UPDATE_LOCK_MAX_TIME_SEC);
        logger.info("New pending request {}", this.toString());
    }

    public String getChannel() {
        return channelUID.getIdWithoutGroup();
    }

    public OnOffType getCommand() {
        return command;
    }

    public boolean timeoutExpired() {
        return LocalDateTime.now().isAfter(expirationDateTime);
    }

    /**
     * Identify PendingRequests based on their ChannelId without group
     *
     * @param req
     * @return
     */
    public boolean equals(PendingRequest req) {
        return channelUID.getIdWithoutGroup().equals(req.getChannel());
    }

    @Override
    public String toString() {
        return channelUID + " " + command + " " + expirationDateTime;
    }
}
