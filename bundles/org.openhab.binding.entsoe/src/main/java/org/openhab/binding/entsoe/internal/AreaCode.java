/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.entsoe.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Miika Jukka - Initial contribution
 *
 */
@NonNullByDefault
public enum AreaCode {

    DK("DK", "10Y1001A1001A65H", "Denmark"),
    DE("DE", "10Y1001A1001A83F", "Germany"),
    UK("UK", "10Y1001A1001A92E", "United Kingdom"),
    MT("MT", "10Y1001A1001A93C", "Malta"),
    MD("MD", "10Y1001A1001A990", "Moldova"),
    AM("AM", "10Y1001A1001B004", "Armenia"),
    GE("GE", "10Y1001A1001B012", "Georgia"),
    AZ("AZ", "10Y1001A1001B05V", "Azerbaijan"),
    UA("UA", "10Y1001C--00003F", "Ukraine"),
    XK("XK", "10Y1001C--00100H", "Kosovo"),
    AL("AL", "10YAL-KESH-----5", "Albania"),
    AT("AT", "10YAT-APG------L", "Austria"),
    BA("BA", "10YBA-JPCC-----D", "Bosnia and Herz."),
    BE("BE", "10YBE----------2", "Belgium"),
    BG("BG", "10YCA-BULGARIA-R", "Bulgaria"),
    CH("CH", "10YCH-SWISSGRIDZ", "Switzerland"),
    ME("ME", "10YCS-CG-TSO---S", "Montenegro"),
    RS("RS", "10YCS-SERBIATSOV", "Serbia"),
    CY("CY", "10YCY-1001A0003J", "Cyprus"),
    CZ("CC", "10YCZ-CEPS-----N", "Czech Republic"),
    FI("FI", "10YFI-1--------U", "Finland"),
    FR("FR", "10YFR-RTE------C", "France"),
    GR("GR", "10YGR-HTSO-----Y", "Greece"),
    HR("HR", "10YHR-HEP------M", "Croatia"),
    HU("HU", "10YHU-MAVIR----U", "Hungary"),
    IE("IE", "10YIE-1001A00010", "Ireland"),
    IT("IT", "10YIT-GRTN-----B", "Italy"),
    LT("LT", "10YLT-1001A0008Q", "Lithuania"),
    LU("LU", "10YLU-CEGEDEL-NQ", "Luxembourg"),
    LV("LV", "10YLV-1001A00074", "Latvia"),
    MK("MK", "10YMK-MEPSO----8", "North Macedonia"),
    NL("NL", "10YNL----------L", "Netherlands"),
    NO("NO", "10YNO-0--------C", "Norway"),
    PT("PT", "10YPT-REN------W", "Portugal"),
    RO("RO", "10YRO-TEL------P", "Romania"),
    SE("SE", "10YSE-1--------K", "Sweden"),
    SI("SI", "10YSI-ELES-----O", "Slovenia"),
    SK("SK", "10YSK-SEPS-----K", "Slovakia"),
    TR("TR", "10YTR-TEIAS----W", "Turkey"),
    BY("BY", "BY", "Belarus"),
    RU("RU", "RU", "Russia"),
    IS("IS", "IS", "Iceland");

    public final String countryCode;
    public final String areaCode;
    public final String country;

    public static @Nullable AreaCode valueOfCountryCode(String countryCode) {
        for (AreaCode areaCode : values()) {
            if (areaCode.countryCode.equals(countryCode)) {
                return areaCode;
            }
        }
        return null;
    }

    private AreaCode(String countryCode, String areaCode, String country) {
        this.countryCode = countryCode;
        this.areaCode = areaCode;
        this.country = country;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public String country() {
        return country;
    }

    public String countryCode() {
        return countryCode;
    }
}
