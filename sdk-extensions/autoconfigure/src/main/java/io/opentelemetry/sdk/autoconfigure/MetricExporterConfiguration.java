/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.prometheus.PrometheusCollector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReaderBuilder;
import io.prometheus.client.exporter.HTTPServer;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

final class MetricExporterConfiguration {

  static void configureExporter(
      String name, ConfigProperties config, SdkMeterProvider meterProvider) {
    switch (name) {
      case "otlp":
      case "otlp_metrics":
        configureOtlpMetrics(config, meterProvider);
        return;
      case "prometheus":
        configurePrometheusMetrics(config, meterProvider);
        return;
      default:
        return;
    }
  }

  // Visible for testing
  static OtlpGrpcMetricExporter configureOtlpMetrics(
      ConfigProperties config, SdkMeterProvider meterProvider) {
    ClasspathUtil.checkClassExists(
        "io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter",
        "OTLP Metrics Exporter",
        "opentelemetry-exporter-otlp-metrics");
    OtlpGrpcMetricExporterBuilder builder = OtlpGrpcMetricExporter.builder();

    String endpoint = config.getString("otel.exporter.otlp.endpoint");
    if (endpoint != null) {
      builder.setEndpoint(endpoint);
    }

    boolean insecure = config.getBoolean("otel.exporter.otlp.insecure");
    if (!insecure) {
      builder.setUseTls(true);
    }

    config.getCommaSeparatedMap("otel.exporter.otlp.headers").forEach(builder::addHeader);

    Long timeoutMillis = config.getLong("otel.exporter.otlp.timeout");
    if (timeoutMillis != null) {
      builder.setTimeout(Duration.ofMillis(timeoutMillis));
    }

    OtlpGrpcMetricExporter exporter = builder.build();

    IntervalMetricReaderBuilder readerBuilder =
        IntervalMetricReader.builder()
            .setMetricProducers(Collections.singleton(meterProvider))
            .setMetricExporter(exporter);
    Long exportIntervalMillis = config.getLong("otel.imr.export.interval");
    if (exportIntervalMillis != null) {
      readerBuilder.setExportIntervalMillis(exportIntervalMillis);
    }
    IntervalMetricReader reader = readerBuilder.build();
    Runtime.getRuntime().addShutdownHook(new Thread(reader::shutdown));

    return exporter;
  }

  private static void configurePrometheusMetrics(
      ConfigProperties config, SdkMeterProvider meterProvider) {
    ClasspathUtil.checkClassExists(
        "io.opentelemetry.exporter.prometheus.PrometheusCollector",
        "Prometheus Metrics Server",
        "opentelemetry-exporter-prometheus");
    PrometheusCollector.builder().setMetricProducer(meterProvider).buildAndRegister();
    Integer port = config.getInt("otel.exporter.prometheus.port");
    if (port == null) {
      port = 9464;
    }
    String host = config.getString("otel.exporter.prometheus.host");
    if (host == null) {
      host = "0.0.0.0";
    }
    final HTTPServer server;
    try {
      server = new HTTPServer(host, port, true);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create Prometheus server", e);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
  }

  private MetricExporterConfiguration() {}
}
