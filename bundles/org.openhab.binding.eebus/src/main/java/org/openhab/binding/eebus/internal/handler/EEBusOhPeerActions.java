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
package org.openhab.binding.eebus.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * The {@link EEBusOhPeerActions} class exposes the {@code pair()}/{@code unpair()} Thing
 * Actions on an {@code eebus:oh-peer} Thing.
 *
 * <p>
 * Both Actions are deliberately parameterless (no {@code @ActionInput}), so each renders as a
 * plain button on the Thing's Actions page in Main UI - the same, already community-verified
 * mechanism CONCEPT.md §4.4 identified for {@code reboot()}/{@code permitJoin()}-style Actions,
 * without the Options-list-parameter rendering risk the earlier cross-Bridge {@code pairWith}
 * idea left under test reservation. See docs/ADR/012-pairing-trust-property-and-actions.md for
 * the full decision.
 * </p>
 *
 * <p>
 * <strong>Not verified against an actual openHAB core build</strong> (no compiler access in the
 * environment this class was written in - see docs/changes/decouple-oh-peer-config-from-pairing/
 * tasks.md §4 for the same caveat applied to the rest of this change). The
 * {@code ThingActionsScope}/{@code RuleAction}/{@code @Component(scope = ServiceScope.PROTOTYPE)}
 * pattern below follows the standard, long-established openHAB binding convention (used e.g. by
 * the network and astro bindings' Actions classes); a real build should confirm it against the
 * openHAB core version pinned in this project's {@code pom.xml}.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = EEBusOhPeerActions.class)
@ThingActionsScope(name = "eebus")
@NonNullByDefault
public class EEBusOhPeerActions implements ThingActions {

    private @Nullable EEBusOhPeerHandler handler;

    @RuleAction(label = "Pair", description = "Adds this Thing's SKI to the parent service's trusted-SKI set.")
    public void pair() {
        EEBusOhPeerHandler localHandler = handler;
        if (localHandler != null) {
            localHandler.pair();
        }
    }

    @RuleAction(label = "Unpair", description = "Removes this Thing's SKI from the parent service's trusted-SKI "
            + "set, without removing this Thing.")
    public void unpair() {
        EEBusOhPeerHandler localHandler = handler;
        if (localHandler != null) {
            localHandler.unpair();
        }
    }

    @Override
    public void setThingHandler(ThingHandler handler) {
        if (handler instanceof EEBusOhPeerHandler ohPeerHandler) {
            this.handler = ohPeerHandler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }
}
