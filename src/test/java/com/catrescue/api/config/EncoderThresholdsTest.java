package com.catrescue.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EncoderThresholdsTest {

    @Test
    void dinov2VersionsUseTheirOwnThreshold() {
        EmbeddingServiceProperties props = new EmbeddingServiceProperties();
        EmbeddingServiceProperties.EncoderThresholds t = props.thresholdsFor("dinov2-b-catcrop-v1");
        assertThat(t.getDistanceThreshold()).isEqualTo(0.45);
        assertThat(t.getCalibrationCeiling()).isEqualTo(0.80);
    }

    @Test
    void clipAndUnknownVersionsUseTopLevelSettings() {
        EmbeddingServiceProperties props = new EmbeddingServiceProperties();
        props.setDistanceThreshold(0.33);
        assertThat(props.thresholdsFor("clip-b32-catcrop-v1").getDistanceThreshold()).isEqualTo(0.33);
        assertThat(props.thresholdsFor(null).getDistanceThreshold()).isEqualTo(0.33);
        assertThat(props.thresholdsFor("").getCalibrationCeiling()).isEqualTo(0.60);
    }

    @Test
    void encoderSettingsBindFromConfiguration() {
        Map<String, String> source = Map.of(
                "cat-rescue.embedding.distance-threshold", "0.35",
                "cat-rescue.embedding.encoders.dinov2.distance-threshold", "0.5",
                "cat-rescue.embedding.encoders.dinov2.calibration-floor", "0.3",
                "cat-rescue.embedding.encoders.dinov2.calibration-ceiling", "0.8"
        );
        EmbeddingServiceProperties props = new Binder(new MapConfigurationPropertySource(source))
                .bind("cat-rescue.embedding", EmbeddingServiceProperties.class)
                .get();
        assertThat(props.thresholdsFor("dinov2-b-full-v1").getDistanceThreshold()).isEqualTo(0.5);
        assertThat(props.thresholdsFor("clip-b32-full-v1").getDistanceThreshold()).isEqualTo(0.35);
    }
}
