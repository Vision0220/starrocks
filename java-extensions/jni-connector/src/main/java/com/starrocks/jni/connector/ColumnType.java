// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.starrocks.jni.connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColumnType {

    public enum TypeValue {
        BYTE,
        BOOLEAN,
        SHORT,
        INT,
        FLOAT,
        LONG,
        DOUBLE,
        STRING,
        BINARY,
        DATE,
        // INT96 timestamp type, hive compatible (hive version < 4.x)
        DATETIME,
        // INT64 timestamp type, TIMESTAMP(isAdjustedToUTC=true, unit=MICROS)
        DATETIME_MICROS,
        // INT64 timestamp type, TIMESTAMP(isAdjustedToUTC=true, unit=MILLIS)
        DATETIME_MILLIS,
        DECIMAL,
        ARRAY,
        MAP,
        STRUCT,
    }

    TypeValue typeValue;
    String name;
    List<String> childNames;
    List<ColumnType> childTypes;
    List<Integer> structFieldIndex;

    private static final Map<String, TypeValue> PRIMITIVE_TYPE_VALUE_MAPPING = new HashMap<>();
    private static final Map<TypeValue, Integer> PRIMITIVE_TYPE_VALUE_SIZE = new HashMap<>();

    static {
        PRIMITIVE_TYPE_VALUE_MAPPING.put("byte", TypeValue.BYTE);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("bool", TypeValue.BOOLEAN);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("boolean", TypeValue.BOOLEAN);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("short", TypeValue.SHORT);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("int", TypeValue.INT);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("float", TypeValue.FLOAT);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("bigint", TypeValue.LONG);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("double", TypeValue.DOUBLE);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("string", TypeValue.STRING);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("binary", TypeValue.BINARY);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("date", TypeValue.DATE);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("timestamp", TypeValue.DATETIME);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("TimestampMicros", TypeValue.DATETIME_MICROS);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("TimestampMillis", TypeValue.DATETIME_MILLIS);
        PRIMITIVE_TYPE_VALUE_MAPPING.put("decimal", TypeValue.DECIMAL);

        PRIMITIVE_TYPE_VALUE_SIZE.put(TypeValue.BYTE, 1);
        PRIMITIVE_TYPE_VALUE_SIZE.put(TypeValue.BOOLEAN, 1);
        PRIMITIVE_TYPE_VALUE_SIZE.put(TypeValue.SHORT, 2);
        PRIMITIVE_TYPE_VALUE_SIZE.put(TypeValue.INT, 4);
        PRIMITIVE_TYPE_VALUE_SIZE.put(TypeValue.FLOAT, 4);
        PRIMITIVE_TYPE_VALUE_SIZE.put(TypeValue.LONG, 8);
        PRIMITIVE_TYPE_VALUE_SIZE.put(TypeValue.DOUBLE, 8);
    }

    @Override
    public String toString() {
        return typeValue.toString() + "(" + name + ")";
    }

    static final class StringScanner {
        String s;
        int offset;

        StringScanner(String s) {
            this.s = s;
            offset = 0;
        }

        int indexOf(char... args) {
            for (int i = offset; i < s.length(); i++) {
                char c = s.charAt(i);
                for (char ch : args) {
                    if (c == ch) {
                        return i;
                    }
                }
            }
            return s.length();
        }

        String substr(int end) {
            return s.substring(offset, end);
        }

        void moveTo(int p) {
            offset = p;
        }

        void next() {
            offset += 1;
        }

        char peek() {
            return s.charAt(offset);
        }
    }

    private void parseArray(List<ColumnType> childTypeValues, StringScanner scanner) {
        int idx = 0;
        while (scanner.peek() != '>') {
            scanner.next(); // '<', or ','
            ColumnType x = new ColumnType(this.name + '#' + idx, TypeValue.BYTE);
            idx += 1;
            x.parse(scanner);
            childTypeValues.add(x);
        }
        scanner.next(); // '>'
    }

    private void parseStruct(List<String> childNames, List<ColumnType> childTypeValues, StringScanner scanner) {
        while (scanner.peek() != '>') {
            scanner.next(); // '<' or ','
            int p = scanner.indexOf(':');
            String name = scanner.substr(p);
            childNames.add(name);
            String fieldName = this.name + ':' + name;
            scanner.moveTo(p + 1);
            ColumnType x = new ColumnType(fieldName, TypeValue.BYTE);
            x.parse(scanner);
            childTypeValues.add(x);
        }
        scanner.next(); // '>'
    }

    private void parse(StringScanner scanner) {
        int p = scanner.indexOf('<', ',', '>');
        String t = scanner.substr(p);
        scanner.moveTo(p);

        // assume there is no blank char in `type`.
        typeValue = null;
        switch (t) {
            case "array": {
                // array<TYPE>
                typeValue = TypeValue.ARRAY;
                childTypes = new ArrayList<>();
                parseArray(childTypes, scanner);
            }
            break;
            case "map": {
                // map<TYPE1,TYPE2>
                typeValue = TypeValue.MAP;
                childTypes = new ArrayList<>();
                parseArray(childTypes, scanner);
            }
            break;
            case "struct": {
                // struct<F1:TYPE1,F2:TYPE2,F3:TYPE3..>
                typeValue = TypeValue.STRUCT;
                childNames = new ArrayList<>();
                childTypes = new ArrayList<>();
                parseStruct(childNames, childTypes, scanner);
            }
            break;
            default: {
                // convert decimal(x,y) to decimal
                if (t.startsWith("decimal")) {
                    t = "decimal";
                }
                typeValue = PRIMITIVE_TYPE_VALUE_MAPPING.getOrDefault(t, null);
            }
        }

        if (typeValue == null) {
            throw new RuntimeException("Unknown type: " + t);
        }
    }

    public ColumnType(String type) {
        this("null", type);
    }

    public ColumnType(String name, ColumnType.TypeValue value) {
        this.name = name;
        typeValue = value;
    }

    public ColumnType(String name, String type) {
        this.name = name;
        StringScanner scanner = new StringScanner(type);
        parse(scanner);
    }

    public boolean isByteStorageType() {
        return typeValue == TypeValue.STRING || typeValue == TypeValue.DATE || typeValue == TypeValue.DECIMAL
                || typeValue == TypeValue.BINARY || typeValue == TypeValue.DATETIME
                || typeValue == TypeValue.DATETIME_MICROS || typeValue == TypeValue.DATETIME_MILLIS;
    }

    public boolean isArray() {
        return typeValue == TypeValue.ARRAY;
    }

    public boolean isMap() {
        return typeValue == TypeValue.MAP;
    }

    public boolean isStruct() {
        return typeValue == TypeValue.STRUCT;
    }

    public int computeColumnSize() {
        switch (typeValue) {
            case ARRAY: {
                // [ null | offset | data ]
                return 2 + childTypes.get(0).computeColumnSize();
            }
            case MAP: {
                // [ null | offset | key | value ]
                return 2 + childTypes.get(0).computeColumnSize() + childTypes.get(1).computeColumnSize();
            }
            case STRUCT: {
                // [null | c0 | c1 .. ]
                int res = 1;
                for (ColumnType t : childTypes) {
                    res += t.computeColumnSize();
                }
                return res;
            }
            case STRING:
            case BINARY:
            case DECIMAL:
            case DATE:
            case DATETIME:
            case DATETIME_MICROS:
            case DATETIME_MILLIS:
                // [null | offset | data ]
                return 3;
            default:
                return 2;
        }
    }

    public ColumnType.TypeValue getTypeValue() {
        return typeValue;
    }

    public int getPrimitiveTypeValueSize() {
        return PRIMITIVE_TYPE_VALUE_SIZE.getOrDefault(typeValue, -1);
    }

    public List<String> getChildNames() {
        return childNames;
    }

    public List<ColumnType> getChildTypes() {
        return childTypes;
    }

    public List<Integer> getStructFieldIndex() {
        return structFieldIndex;
    }

    public void pruneOnStructSelectedFields(StructSelectedFields ssf) {
        // make index and prune on this struct.
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < childNames.size(); i++) {
            index.put(childNames.get(i), i);
        }

        List<String> fields = ssf.getFields();
        List<String> names = new ArrayList<>();
        List<ColumnType> types = new ArrayList<>();
        structFieldIndex = new ArrayList<>();
        for (String f : fields) {
            Integer i = index.get(f);
            structFieldIndex.add(i);
            types.add(childTypes.get(i));
            names.add(f);
        }
        childNames = names;
        childTypes = types;

        // prune on sub structs.
        for (int i = 0; i < childTypes.size(); i++) {
            ColumnType type = childTypes.get(i);
            if (type.isStruct()) {
                StructSelectedFields ssf2 = ssf.findChildren(childNames.get(i));
                type.pruneOnStructSelectedFields(ssf2);
            }
        }
    }
}
