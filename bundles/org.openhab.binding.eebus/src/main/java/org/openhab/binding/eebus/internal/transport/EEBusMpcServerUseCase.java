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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.items.Metadata;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;
import org.openmuc.jeebus.spine.api.DataValidationException;
import org.openmuc.jeebus.spine.api.Entity;
import org.openmuc.jeebus.spine.api.Feature;
import org.openmuc.jeebus.spine.impl.FeatureInformationService;
import org.openmuc.jeebus.spine.spi.AllowedEntityTypes;
import org.openmuc.jeebus.spine.spi.FeatureRequirement;
import org.openmuc.jeebus.spine.spi.Inject;
import org.openmuc.jeebus.spine.spi.UseCase;
import org.openmuc.jeebus.spine.utils.datatypes.ScaledNumberWrapper;
import org.openmuc.jeebus.spine.utils.features.measurement.MeasurementDescriptionListDataFunction;
import org.openmuc.jeebus.spine.utils.features.measurement.MeasurementFeature;
import org.openmuc.jeebus.spine.utils.features.measurement.MeasurementListDataFunction;
import org.openmuc.jeebus.spine.xsd.v1.EntityTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.MeasurementValueTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.RoleType;
import org.openmuc.jeebus.spine.xsd.v1.ScopeTypeEnumType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offers the MPC (Monitoring of Power Consumption) use case in the **Server role** — i.e.
 * openHAB exposes a single active-power measurement (e.g. from an inverter Thing) to the
 * EEBUS network, matching the user's original "Wechselrichter/ImSys" motivating scenario
 * (see CONCEPT.md §1, §5.4.1, §5.4.2).
 *
 * <p>
 * <strong>Scope, deliberately limited:</strong> only scenario 1 (mandatory: total AC power,
 * {@code ScopeTypeEnumType.AC_POWER_TOTAL}) is implemented. Scenarios 2-5 (per-phase, energy,
 * etc. — see CONCEPT.md §5.4.2) are optional per the verified `eebus-go` reference and are
 * left as a follow-up; the exact `ElectricalConnection` characteristic/parameter setup a real
 * peer expects alongside `Measurement` is <strong>not yet verified</strong> (no SERVER-role
 * reference implementation was found in `eebus-go`, which only implements MPC client-side) —
 * this class therefore declares only the {@code Measurement} server feature, not
 * {@code ElectricalConnection}. See CONCEPT.md §7 item 8.
 * </p>
 *
 * <p>
 * Data flow (CONCEPT.md §4.2): the value comes from whichever Item declares
 * {@code eebus="MPC.power"} metadata (Bridge-wide, no {@code peer} attribute — a Server
 * feature is visible to the whole network, not to one peer). {@link EEBusMetadataService}
 * pushes state changes for that Item to {@link #onItemStateChanged(State)}, which updates the
 * SPINE {@code Measurement} feature's cached data — SPINE itself serves read requests and
 * subscription notifications from that cache (verified against
 * {@code ReadListFeatureFunction}/{@code DataListHolder} in jeebus.spine, which hold data and
 * auto-answer reads; there is no read-request callback to implement).
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@AllowedEntityTypes({ EntityTypeEnumType.CEM })
public class EEBusMpcServerUseCase implements UseCase {

    /**
     * Single, fixed measurement ID for the one data point this class exposes. Fine as long as
     * only one MPC.power value is ever wired per Bridge (current scope, see class javadoc).
     */
    private static final long MEASUREMENT_ID = 0L;

    private final Logger logger = LoggerFactory.getLogger(EEBusMpcServerUseCase.class);
    private final EEBusMetadataService metadataService;
    private final Consumer<State> itemStateListener = this::onItemStateChanged;

    @Inject
    private @Nullable Entity entity;

    private @Nullable FeatureAddressType address;
    private @Nullable String itemName;
    private @Nullable MeasurementListDataFunction measurementListDataFunction;

    public EEBusMpcServerUseCase(EEBusMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public String getActor() {
        // Bug fixed 2026-07-30: this class implements the SERVER role of MPC (openHAB acts as
        // the "CEM" actor, offering the measurement), but previously returned the CLIENT actor
        // string "MonitoringAppliance" instead. Now unambiguous per the primary-verified catalog
        // (CONCEPT.md §5.4.1, EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf Annex A Table 1: MPC
        // Client Actor = "Monitoring Appliance", Server Actor = "CEM"). Exact wire-format casing
        // ("CEM") still taken from the verified eebus-go reference, per class javadoc.
        return "CEM";
    }

    @Override
    public String getName() {
        return "MonitoringOfPowerConsumption";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    // UseCase (jeebus.spine) is an unannotated legacy interface - see the identical note on
    // AbstractEEBusLimitControllableSystemUseCase#getScenarioSupport().
    @Override
    @NonNullByDefault({})
    public List<Long> getScenarioSupport() {
        // Only scenario 1 (mandatory total AC power) - see class javadoc.
        return List.of(1L);
    }

    @Override
    public FeatureAddressType getAddress() {
        FeatureAddressType currentAddress = address;
        if (currentAddress == null) {
            throw new IllegalStateException("getAddress() called before setup()");
        }
        return currentAddress;
    }

    @Override
    @NonNullByDefault({})
    public Set<FeatureRequirement> getFeatureRequirements(EntityTypeEnumType entityType) {
        return Set.of(new FeatureRequirement(FeatureTypeEnumType.MEASUREMENT, RoleType.SERVER,
                MeasurementDescriptionListDataFunction.class, MeasurementListDataFunction.class));
    }

    @Override
    public void setup() {
        Entity localEntity = this.entity;
        if (localEntity == null) {
            // Should not happen - Entity#addUseCase() is documented to fill @Inject fields
            // before calling setup().
            throw new IllegalStateException("Entity was not injected before setup()");
        }
        this.address = new FeatureAddressType().withDevice(localEntity.getStaticAddress().getDevice())
                .withEntity(localEntity.getStaticAddress().getEntity());

        Optional<Metadata> metadata = metadataService.find("MPC", "power", null);
        if (metadata.isEmpty()) {
            logger.info("No Item declares eebus=\"MPC.power\" metadata - the MPC server use case "
                    + "is registered but will not expose any value until one does. See CONCEPT.md §4.2.");
            return;
        }
        String resolvedItemName = EEBusMetadataService.itemNameOf(metadata.get());
        this.itemName = resolvedItemName;

        Feature rawFeature = localEntity.getFeatures().stream()
                .filter(f -> f.getType() == FeatureTypeEnumType.MEASUREMENT && f.getRole() == RoleType.SERVER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "MEASUREMENT server feature missing - Entity#addUseCase() should have added it "
                                + "from getFeatureRequirements()"));
        MeasurementFeature measurementFeature = (MeasurementFeature) FeatureInformationService.getInstance()
                .createFeatureWrapper(rawFeature);

        MeasurementDescriptionListDataFunction descriptionFunction = measurementFeature
                .addMeasurementDescriptionListDataFunction();
        this.measurementListDataFunction = measurementFeature.addMeasurementListDataFunction();

        try {
            descriptionFunction.addData(new MeasurementDescriptionDataType().withMeasurementId(MEASUREMENT_ID)
                    .withMeasurementType(MeasurementTypeEnumType.POWER.value()).withUnit("W")
                    .withScopeType(ScopeTypeEnumType.AC_POWER_TOTAL.value()));
        } catch (DataValidationException e) {
            logger.warn("Failed to register MPC.power measurement description", e);
            return;
        }

        // Seed with the Item's current state (if any) before subscribing to changes, so a peer
        // reading immediately after pairing gets a real value instead of an empty list entry.
        metadataService.readState(resolvedItemName).ifPresent(this::onItemStateChanged);
        metadataService.registerItemStateListener(resolvedItemName, itemStateListener);
    }

    private void onItemStateChanged(State state) {
        MeasurementListDataFunction function = this.measurementListDataFunction;
        if (function == null) {
            return;
        }
        if (!(state instanceof QuantityType<?> quantity)) {
            logger.debug("Item state '{}' for eebus=\"MPC.power\" is not a QuantityType, ignoring", state);
            return;
        }
        // QuantityType#toUnit(Unit) returns null "in case of an error" (verified against openHAB
        // Core javadoc) - e.g. if the Item's unit is not power-compatible with Watt.
        QuantityType<?> watts = quantity.toUnit(Units.WATT);
        if (watts == null) {
            logger.debug("Item state '{}' for eebus=\"MPC.power\" could not be converted to Watts, ignoring", state);
            return;
        }

        MeasurementDataType data = new MeasurementDataType().withMeasurementId(MEASUREMENT_ID)
                .withValueType(MeasurementValueTypeEnumType.VALUE.value())
                .withValue(new ScaledNumberWrapper(watts.doubleValue()).toXsdType());
        try {
            // Index 0: the one entry added in setup() and never removed since.
            function.updateData(0, data);
        } catch (DataValidationException e) {
            logger.warn("Failed to update MPC.power measurement data from Item '{}'", itemName, e);
        }
    }

    @Override
    public void close() {
        String name = this.itemName;
        if (name != null) {
            metadataService.unregisterItemStateListener(name, itemStateListener);
        }
    }
}
