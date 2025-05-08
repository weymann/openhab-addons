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
package org.openhab.binding.curves.internal.handler;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.common.registry.RegistryChangeListener;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemNotUniqueException;
import org.openhab.core.items.ItemRegistry;

/**
 * The {@link ItemRegistryMock} Helper Util to read test resource files
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ItemRegistryMock implements ItemRegistry {
    Map<String, Item> items = new HashMap<>();

    @Override
    public void addRegistryChangeListener(RegistryChangeListener<Item> listener) {
    }

    @Override
    public Collection<Item> getAll() {
        return items.values();
    }

    @Override
    public Stream<Item> stream() {
        return items.values().stream();
    }

    @Override
    public @Nullable Item get(String key) {
        Item i = items.get(key);
        System.out.println("ItemRegsitry: get item " + key + " has? " + i.getLastState());
        return items.get(key);
    }

    @Override
    public void removeRegistryChangeListener(RegistryChangeListener<Item> listener) {
        // TODO Auto-generated method stub
    }

    @Override
    public Item add(Item element) {
        items.put(element.getName(), element);
        System.out.println("Add item " + element.getName());
        return element;
    }

    @Override
    public @Nullable Item update(Item element) {
        return items.put(element.getName(), element);
    }

    @Override
    public @Nullable Item remove(String key) {
        return items.remove(key);
    }

    @Override
    public Item getItem(String name) throws ItemNotFoundException {
        Item item = items.get(name);
        if (item != null) {
            return item;
        }
        throw new ItemNotFoundException("Item not found: " + name);
    }

    @Override
    public Item getItemByPattern(String name) throws ItemNotFoundException, ItemNotUniqueException {
        Item item = items.get(name);
        if (item != null) {
            return item;
        }
        throw new ItemNotFoundException("Item not found: " + name);
    }

    @Override
    public Collection<Item> getItems() {
        return items.values();
    }

    @Override
    public Collection<Item> getItemsOfType(String type) {
        return items.values();
    }

    @Override
    public Collection<Item> getItems(String pattern) {
        return items.values();
    }

    @Override
    public Collection<Item> getItemsByTag(String... tags) {
        return items.values();
    }

    @Override
    public Collection<Item> getItemsByTagAndType(String type, String... tags) {
        return items.values();
    }

    @Override
    public <T extends Item> Collection<T> getItemsByTag(Class<T> typeFilter, String... tags) {
        return List.of();
    }

    @Override
    public @Nullable Item remove(String itemName, boolean recursive) {
        return items.remove(itemName);
    }
}
