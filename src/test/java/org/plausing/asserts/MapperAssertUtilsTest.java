package org.plausing.asserts;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.List;

public class MapperAssertUtilsTest {

    public static class A {
        private String att1;
        private String att2;
    }

    public static class B {
        private String att1;
    }

    public static class C {
        public Integer i;
        public C(Integer i) {
            this.i = i;
        }
    }

    @Test
    public void testGenerateTestValuesFromSpawningType() throws Exception {
        // given
        MapperAssert ma = new MapperAssert<A,B>((a) -> new B(), MapperAssert.class);

        // when
        List<C> list = ma.generateTestValuesFromGeneratingType(C.class, Integer.class);

        Assertions.assertThat(list)
                .extracting((e) -> e == null ? null: e.i)
                .containsExactly(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1, 0, null);

    }
}