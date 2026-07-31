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

/**
 * Binding-internal callback interface - deliberately <strong>not</strong> an openHAB framework
 * mechanism - that lets a child Entity Thing handler ({@link EEBusOhEntityHandler}, serving
 * {@code eebus:oh-entity}/{@code eebus:oh-cs-entity}/{@code eebus:oh-eg-entity}) notify its
 * parent Bridge handler ({@link EEBusHandler}) that something about it changed, so the Bridge
 * can rebuild its local SPINE {@code Device} with a freshly-derived Use-Case set. See
 * docs/ADR/027-derive-local-use-cases-from-entities.md.
 *
 * <p>
 * Implemented by {@link EEBusHandler}. Called by {@link EEBusOhEntityHandler#initialize()}/
 * {@link EEBusOhEntityHandler#dispose()} via {@code getBridge()} - not registered or looked up
 * through any openHAB service mechanism, since both classes already share the same Bridge/child
 * Thing relationship openHAB itself maintains ({@code getBridge()} is always available once a
 * child Thing has a parent Bridge configured).
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public interface EEBusEntityChangeListener {

    /**
     * Called by a child Entity Thing handler whenever it is initialized, disposed, or removed -
     * i.e. whenever its contribution to the parent Bridge's derived local Use-Case set could
     * have changed. The implementation ({@link EEBusHandler#onEntityChanged()}) schedules a full
     * {@code dispose()}/{@code initialize()} rebuild of the Bridge's local SPINE {@code Device} -
     * see docs/ADR/027-derive-local-use-cases-from-entities.md, "Every configuration change is
     * now uniformly a full rebuild" - after a short debounce window so a burst of near-
     * simultaneous calls coalesces into one rebuild (docs/ADR/028-serialize-startshipspine-per-
     * thing.md). Fire-and-forget: this method does not block until that rebuild has run.
     */
    void onEntityChanged();
}
