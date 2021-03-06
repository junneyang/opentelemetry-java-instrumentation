/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling.exporter;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.spi.exporter.ExporterConfig;

public class DefaultExporterConfig implements ExporterConfig {
  private final String prefix;

  public DefaultExporterConfig(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public String getString(String key, String defaultValue) {
    return Config.getSettingFromEnvironment(prefix + "." + key, defaultValue);
  }

  @Override
  public int getInt(String key, int defaultValue) {
    String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Integer.parseInt(s); // TODO: Handle format errors gracefully?
  }

  @Override
  public long getLong(String key, long defaultValue) {
    String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Long.parseLong(s); // TODO: Handle format errors gracefully?
  }

  @Override
  public boolean getBoolean(String key, boolean defaultValue) {
    String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(s); // TODO: Handle format errors gracefully?
  }

  @Override
  public double getDouble(String key, double defaultValue) {
    String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Double.parseDouble(s); // TODO: Handle format errors gracefully?
  }
}
