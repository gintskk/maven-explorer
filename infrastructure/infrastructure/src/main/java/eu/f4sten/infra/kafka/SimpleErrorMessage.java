/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.f4sten.infra.kafka;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class SimpleErrorMessage<T> {

    public T obj;
    public String stacktrace;

    public SimpleErrorMessage() {
        // for serialization
    }

    public SimpleErrorMessage(T obj, String stacktrace) {
        this.obj = obj;
        this.stacktrace = stacktrace;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    public static <T> SimpleErrorMessage<T> get(T obj) {
        return get(obj, null);
    }

    public static <T> SimpleErrorMessage<T> get(T obj, Throwable t) {
        var stacktrace = t == null ? null : ExceptionUtils.getStackTrace(t);
        return new SimpleErrorMessage<T>(obj, stacktrace);
    }
}