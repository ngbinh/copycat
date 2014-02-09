/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Command metadata.
 *
 * @author Jordan Halterman
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {

  /**
   * A command type.
   *
   * @author Jordan Halterman
   */
  public static enum Type {

    /**
     * A read-only command.
     */
    READ("read"),

    /**
     * A write-only command.
     */
    WRITE("write"),

    /**
     * A read/write command.
     */
    READ_WRITE("read-write");

    private final String name;

    private Type(String name) {
      this.name = name;
    }

    /**
     * Returns the command name.
     *
     * @return The command name.
     */
    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return getName();
    }

  }

  /**
   * A command argument.
   *
   * @author Jordan Halterman
   */
  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  public static @interface Argument {

    /**
     * The argument name.
     */
    String value();

    /**
     * Indicates whether the argument is required.
     */
    boolean required() default true;

  }

  /**
   * The command name.
   */
  String name();

  /**
   * The command type.
   */
  Type type() default Type.READ_WRITE;

}