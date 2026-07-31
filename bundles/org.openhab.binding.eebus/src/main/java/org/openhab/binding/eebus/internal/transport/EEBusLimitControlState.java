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

/**
 * The five states of the LPC/LPP "Controllable System" state machine, as defined in
 * {@code EEBus_UC_TS_LimitationOfPowerConsumption_V1.0.0_public.pdf} §2.3.2 ("Defined states") -
 * identical for LPP (§7 item "LPC/LPP Server-Rolle"). See {@link EEBusLimitControlStateMachine}
 * for the transition logic between these states.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public enum EEBusLimitControlState {
    /**
     * After (re)start, before the first Heartbeat/limit write is seen. The Active Power
     * Consumption Limit is deactivated; the CS is limited only by the failsafe value.
     */
    INIT,
    /** Controlled by the Energy Guard, but currently not limited (limit deactivated). */
    UNLIMITED_CONTROLLED,
    /** Controlled by the Energy Guard and currently limited (limit activated). */
    LIMITED,
    /**
     * The Energy Guard's Heartbeat is missing - the CS is limited by the failsafe value,
     * independently of the (deactivated) Active Power Consumption Limit.
     */
    FAILSAFE,
    /**
     * Left uncontrolled after an extended period without a working Energy Guard connection
     * (past {@code failsafeDurationMinimum}, or no limit write ever received). Not limited at
     * all - consumes/produces as if there were no external limitation.
     */
    UNLIMITED_AUTONOMOUS
}
