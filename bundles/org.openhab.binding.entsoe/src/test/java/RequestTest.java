
/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javax.measure.quantity.Length;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.i18n.UnitProvider;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.link.ItemChannelLink;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;

/**
 * {@link RequestTest} testing updates in item metadata changes
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class RequestTest {

        private static DateTimeFormatter REQUEST_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

        @Test
        public void testMetadataUpdate() {
                String url = "https://web-api.tp.entsoe.eu/api?securityToken=9223ca31-b0a8-4903-8c41-4465b5ddcd03&documentType=A44&in_domain=11X0-0000-0557-H&out_domain=11X0-0000-0557-H&periodStart=202409112200&periodEnd=202409122200";
                ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.HOURS);
                System.out.println(zdt.format(REQUEST_FORMAT));
        }
}
