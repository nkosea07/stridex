package com.racetracker.service;

import com.racetracker.dto.tracking.GpsLocationBatchRequest;
import com.racetracker.dto.tracking.GpsLocationRequest;
import com.racetracker.dto.tracking.TrackingCheckpointRequest;
import com.racetracker.service.impl.PublicTrackingServiceImpl;
import com.racetracker.service.impl.TrackingServiceImpl;
import io.micrometer.observation.annotation.Observed;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingObservationCoverageTest {

    @Test
    void publicTrackingHotPaths_haveStableObservationNames() throws Exception {
        assertObserved(PublicTrackingServiceImpl.class, "getRaceHub", "tracking.public.race_hub", UUID.class);
        assertObserved(PublicTrackingServiceImpl.class, "getLiveAthletes", "tracking.public.live_athletes", UUID.class, String.class);
        assertObserved(PublicTrackingServiceImpl.class, "getAthleteTracking", "tracking.public.athlete_detail", UUID.class, UUID.class);
    }

    @Test
    void trackingHotPaths_haveStableObservationNames() throws Exception {
        assertObserved(TrackingServiceImpl.class, "recordLocation", "tracking.ingest.location", UUID.class, GpsLocationRequest.class);
        assertObserved(TrackingServiceImpl.class, "recordLocationsBatch", "tracking.ingest.location_batch", UUID.class, GpsLocationBatchRequest.class);
        assertObserved(TrackingServiceImpl.class, "recordCheckpoint", "tracking.ingest.checkpoint", UUID.class, TrackingCheckpointRequest.class);
        assertObserved(TrackingServiceImpl.class, "getLiveData", "tracking.live.aggregate", UUID.class);
        assertObserved(TrackingServiceImpl.class, "getParticipantTrack", "tracking.live.participant_track", UUID.class, UUID.class);
    }

    private void assertObserved(Class<?> serviceClass, String methodName, String expectedName, Class<?>... parameterTypes) throws Exception {
        Method method = serviceClass.getMethod(methodName, parameterTypes);
        Observed observed = method.getAnnotation(Observed.class);

        assertThat(observed)
            .as("%s.%s should be annotated with @Observed", serviceClass.getSimpleName(), methodName)
            .isNotNull();
        assertThat(observed.name()).isEqualTo(expectedName);
        assertThat(observed.lowCardinalityKeyValues()).isEmpty();
    }
}
