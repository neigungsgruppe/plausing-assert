package org.plausing.asserts;

import java.awt.peer.ChoicePeer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Created by Florian on 15.02.2015.
 */
public class MapperAssertTestData {
        public HashMap<Class, Object> LEARN_VALUES_BY_TYPE = new HashMap<Class, Object>();
        public HashMap<Class, List> TEST_VALUES_BY_TYPE = new HashMap<Class, List>();
        public HashMap<String, Class> COLUMN_LABELS_TO_TYPES = new HashMap<String, Class>();
        public HashMap<TypePair, Function> mappers = new HashMap<TypePair, Function>();
        public HashMap<String, Object> LEARN_VALUES_BY_FIELDNAME = new HashMap<String, Object>();
        public HashMap<String, List> TEST_VALUES_BY_FIELDNAME = new HashMap<String, List>();
        public HashSet<String> ENUM_NAMES_TO_IGNORE = new HashSet<String>();
        public List<OverrideMapping> overrideMappingValues = new ArrayList<OverrideMapping>();

        // fields that don't use null values.
        public HashSet<String> NON_NULL_FIELDS = new HashSet<String>();
}
