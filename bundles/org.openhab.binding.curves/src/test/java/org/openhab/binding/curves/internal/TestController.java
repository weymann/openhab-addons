/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.curves.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.curves.internal.handler.ControllerMock;
import org.openhab.binding.curves.internal.handler.HemsBridge;
import org.openhab.binding.curves.internal.handler.ThingHandlerCallbackMock;

/**
 * The {@link TestController} for curve generation use cases
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class TestController {

    @Test
    void testControllerInit() {
        HemsBridge controller = ControllerMock.createController();
        ThingHandlerCallbackMock controllerListener = new ThingHandlerCallbackMock();
        controller.setCallback(controllerListener);
        controller.initialize();
    }

    @Test
    void testSun() {
        HemsBridge controller = ControllerMock.createController();
        controller.initialize();
    }
}
