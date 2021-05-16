/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.ems.internal.handler;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventFilter;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.events.ItemStateChangedEvent;
import org.openhab.core.items.events.ItemStateEvent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Generic {@link ItemListener} listenign to Item Events
 *
 * @author Bernd Weymann - Initial contribution
 */
public class ItemListener implements EventSubscriber {
    private static final Set<String> SUBSCRIBED_EVENT_TYPES = Set.of(ItemStateEvent.TYPE, ItemStateChangedEvent.TYPE);
    private final ServiceRegistration<?> eventSubscriberRegistration;
    private EventFilter eventFilter;
    private EMSHandler handler;

    public ItemListener(EMSHandler h, String item, BundleContext context) {
        handler = h;
        eventFilter = event -> {
            String topic = event.getTopic();

            return topic.equals("openhab/items/" + item + "/state")
                    || topic.equals("openhab/items/" + item + "/statechanged");
        };
        eventSubscriberRegistration = context.registerService(EventSubscriber.class.getName(), this, null);
    }

    @Override
    public Set<@NonNull String> getSubscribedEventTypes() {
        return SUBSCRIBED_EVENT_TYPES;
    }

    @Override
    public @Nullable EventFilter getEventFilter() {
        return eventFilter;
    }

    @Override
    public void receive(Event event) {
        handler.receivedEvent(event);
    }

    public void dispose() {
        eventSubscriberRegistration.unregister();
    }
}
