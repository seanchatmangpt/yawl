/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.schema;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Michael Adams
 * Creation Date: 14/09/2008
 */
public class XSDType {

    public static final int INVALID_TYPE         = -1;
    public static final int ANY_TYPE             = 0;

    // Numeric Types
    public static final int INTEGER              = 1;                        // Integral
    public static final int POSITIVE_INTEGER     = 2;
    public static final int NEGATIVE_INTEGER     = 3;
    public static final int NON_POSITIVE_INTEGER = 4;
    public static final int NON_NEGATIVE_INTEGER = 5;
    public static final int INT                  = 6;
    public static final int LONG                 = 7;
    public static final int SHORT                = 8;
    public static final int BYTE                 = 9;
    public static final int UNSIGNED_LONG        = 10;
    public static final int UNSIGNED_INT         = 11;
    public static final int UNSIGNED_SHORT       = 12;
    public static final int UNSIGNED_BYTE        = 13;
    public static final int DOUBLE               = 14;                    // Non-integral
    public static final int FLOAT                = 15;
    public static final int DECIMAL              = 16;

    // String Types
    public static final int STRING               = 17;
    public static final int NORMALIZED_STRING    = 18;
    public static final int TOKEN                = 19;
    public static final int LANGUAGE             = 20;
    public static final int NMTOKEN              = 21;
    public static final int NMTOKENS             = 22;
    public static final int NAME                 = 23;
    public static final int NCNAME               = 24;

    // Date Time Types
    public static final int DATE                 = 25;
    public static final int TIME                 = 26;
    public static final int DATETIME             = 27;
    public static final int DURATION             = 28;
    public static final int GDAY                 = 29;
    public static final int GMONTH               = 30;
    public static final int GYEAR                = 31;
    public static final int GMONTHDAY            = 32;
    public static final int GYEARMONTH           = 33;

    // Magic Types
    public static final int ID                   = 34;
    public static final int IDREF                = 35;
    public static final int IDREFS               = 36;
    public static final int ENTITY               = 37;
    public static final int ENTITIES             = 38;

    // Other Types
    public static final int QNAME                = 39;
    public static final int BOOLEAN              = 40;
    public static final int HEX_BINARY           = 41;
    public static final int BASE64_BINARY        = 42;
    public static final int NOTATION             = 43;
    public static final int ANY_URI              = 44;


    public enum RestrictionFacet { minExclusive, maxExclusive,
            minInclusive, maxInclusive, minLength, maxLength, length,
            totalDigits, fractionDigits, whiteSpace, pattern, enumeration }


    private static final List<String> _typeList = makeList();


    public static String getString(int type) {
        return switch (type) {
            case ANY_TYPE -> "anyType";
            case INTEGER -> "integer";
            case POSITIVE_INTEGER -> "positiveInteger";
            case NEGATIVE_INTEGER -> "negativeInteger";
            case NON_POSITIVE_INTEGER -> "nonPositiveInteger";
            case NON_NEGATIVE_INTEGER -> "nonNegativeInteger";
            case INT -> "int";
            case LONG -> "long";
            case SHORT -> "short";
            case BYTE -> "byte";
            case UNSIGNED_LONG -> "unsignedLong";
            case UNSIGNED_INT -> "unsignedInt";
            case UNSIGNED_SHORT -> "unsignedShort";
            case UNSIGNED_BYTE -> "unsignedByte";
            case DOUBLE -> "double";
            case FLOAT -> "float";
            case DECIMAL -> "decimal";
            case STRING -> "string";
            case NORMALIZED_STRING -> "normalizedString";
            case TOKEN -> "token";
            case LANGUAGE -> "language";
            case NMTOKEN -> "NMTOKEN";
            case NMTOKENS -> "NMTOKENS";
            case NAME -> "Name";
            case NCNAME -> "NCName";
            case DATE -> "date";
            case TIME -> "time";
            case DATETIME -> "dateTime";
            case DURATION -> "duration";
            case GDAY -> "gDay";
            case GMONTH -> "gMonth";
            case GYEAR -> "gYear";
            case GMONTHDAY -> "gMonthDay";
            case GYEARMONTH -> "gYearMonth";
            case ID -> "ID";
            case IDREF -> "IDREF";
            case IDREFS -> "IDREFS";
            case ENTITY -> "ENTITY";
            case ENTITIES -> "ENTITIES";
            case QNAME -> "QName";
            case BOOLEAN -> "boolean";
            case HEX_BINARY -> "hexBinary";
            case BASE64_BINARY -> "base64Binary";
            case NOTATION -> "notation";
            case ANY_URI -> "anyURI";
            default -> "invalid_type";
        };
    }


    public static boolean isBuiltInType(String type) {
        return _typeList.contains(type);
    }

    
    public static int getOrdinal(String type) {
        return _typeList.indexOf(type);
    }

    public static boolean isNumericType(String type) {
        int ordinal = getOrdinal(type);
        return (ordinal >= INTEGER) && (ordinal <= DECIMAL);
    }

    public static boolean isStringType(String type) {
        int ordinal = getOrdinal(type);
        return (ordinal >= STRING) && (ordinal <= TOKEN);
    }

    public static boolean isIntegralType(String type) {
        int ordinal = getOrdinal(type);
        return (ordinal >= INTEGER) && (ordinal <= UNSIGNED_BYTE);
    }

    public static boolean isFloatType(String type) {
        int ordinal = getOrdinal(type);
        return (ordinal >= DOUBLE) && (ordinal <= DECIMAL);        
    }

    public static boolean isBooleanType(String type) {
        return getOrdinal(type) == BOOLEAN;
    }

    public static boolean isDateType(String type) {
        int ordinal = getOrdinal(type);
        return (ordinal >= DATE) && (ordinal <= DATETIME);
    }

    public static boolean isListType(String type) {
        int ordinal = getOrdinal(type);
        return ordinal == NMTOKENS || ordinal == ENTITIES || ordinal == IDREFS;
    }

    public static boolean isBinaryType(String type) {
        int ordinal = getOrdinal(type);
        return ordinal == HEX_BINARY || ordinal == BASE64_BINARY;
    }

    public static boolean isStringForType(String s, int type) {
        return getString(type).equals(s);
    }

    public static List<String> getBuiltInTypeList() {
        return new ArrayList<>(_typeList);                              // send a copy
    }

    public static String[] getBuiltInTypeArray() {
        return _typeList.toArray(String[]::new);
    }


    public static String getSampleValue(String type) {
        return switch (getOrdinal(type)) {
            case INTEGER, POSITIVE_INTEGER, INT, LONG, SHORT, UNSIGNED_LONG, UNSIGNED_INT,
                 UNSIGNED_SHORT, UNSIGNED_BYTE, NON_NEGATIVE_INTEGER, GYEAR, BYTE, DECIMAL -> "100";
            case NEGATIVE_INTEGER, NON_POSITIVE_INTEGER -> "-100";
            case STRING, NORMALIZED_STRING -> "a string";
            case TOKEN, NMTOKEN, NMTOKENS -> "token";
            case NAME, NCNAME, ID, IDREF, IDREFS, ENTITY, ENTITIES, BASE64_BINARY,
                 NOTATION, ANY_URI, ANY_TYPE -> "name";
            case BOOLEAN -> "false";
            case LANGUAGE -> "en";
            case QNAME -> "xs:name";
            case HEX_BINARY -> "FF";
            case DOUBLE, FLOAT -> "3.142";
            case DATE -> getDateTimeValue("yyyy-MM-dd");
            case TIME -> getDateTimeValue("HH:mm:ss");
            case DATETIME -> getDateTimeValue("yyyy-MM-dd'T'HH:mm:ss");
            case DURATION -> "P2Y";
            case GDAY -> getDateTimeValue("'---'dd");
            case GMONTH -> getDateTimeValue("'--'MM");
            case GMONTHDAY -> getDateTimeValue("'--'MM-dd");
            case GYEARMONTH -> getDateTimeValue("yyyy-MM");
            default -> "name";
        };
    }


    public static char[] getConstrainingFacetMap(String type) {
        String vMap = switch (getOrdinal(type)) {
            case INTEGER, POSITIVE_INTEGER, NEGATIVE_INTEGER, NON_POSITIVE_INTEGER,
                 NON_NEGATIVE_INTEGER, INT, LONG, SHORT, UNSIGNED_LONG, UNSIGNED_INT,
                 UNSIGNED_SHORT, UNSIGNED_BYTE -> "111100010111";
            case STRING, NORMALIZED_STRING, TOKEN, LANGUAGE, NMTOKEN, NMTOKENS, NAME,
                 NCNAME, ID, IDREF, IDREFS, ENTITY, ENTITIES, QNAME, HEX_BINARY,
                 BASE64_BINARY, NOTATION, ANY_URI -> "000011100111";
            case DOUBLE, FLOAT, DATE, TIME, DATETIME, DURATION, GDAY, GMONTH, GYEAR,
                 GMONTHDAY, GYEARMONTH -> "111100000111";
            case BOOLEAN -> "000000000110";
            case BYTE -> "111100110111";
            case DECIMAL -> "111100011111";
            case ANY_TYPE -> "000000000000";
            default -> "000000000000";
        };
        return vMap.toCharArray();
    }


    public static boolean isValidFacet(String facetName, String type) {
        char[] validationMap = getConstrainingFacetMap(type);
        try {
            RestrictionFacet facet = RestrictionFacet.valueOf(facetName);
            int ordinal = facet.ordinal();
            return validationMap[ordinal] == '1';
        }
        catch (IllegalArgumentException iae) {
            return false;                                  // invalid restriction name
        }
    }


    private static String[] makeYAWLTypeArray() {
        String[] simpleYAWLTypes = {"NCName", "anyURI", "boolean", "date", "double",
                                    "duration", "long", "string", "time" } ;
        return simpleYAWLTypes;
    }


    private static List<String> makeList() {
        var typeList = new ArrayList<String>();
        for (int i = ANY_TYPE; i <= ANY_URI; i++) {
            typeList.add(getString(i));
        }
        return typeList;
    }


    private static String getDateTimeValue(String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                .withZone(ZoneId.systemDefault());
        return formatter.format(Instant.now());
    }

}
