package org.plausing.asserts;

import org.assertj.core.api.AbstractAssert;

import java.sql.ResultSet;

/**
 * Created by Florian on 08.02.2015.
 */
public class ResultSetAssert extends AbstractAssert<ResultSetAssert, ResultSet>{

    // 2 - Write a constructor to build your assertion class with the object you want make assertions on.
    public ResultSetAssert(ResultSet actual) {
        super(actual, ResultSet.class);
    }

    // 3 - A fluent entry point to your specific assertion class, use it with static import.
    public static ResultSetAssert assertThat(ResultSet actual) {
        return new ResultSetAssert(actual);
    }

    


}
