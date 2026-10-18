package com.racetracker.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import com.racetracker.exception.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3MediaStorageTest {

    @Mock
    S3Client client;

    @Test
    void awsProviderStoresLoadsAndDeletesPrivateObjectKeys() {
        AwsS3MediaStorage storage = new AwsS3MediaStorage(client, "race-media");
        byte[] original = new byte[]{1, 2, 3};

        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), original));

        String key = storage.store(
            new MockMultipartFile("file", "photo.png", "image/png", original),
            "race-1"
        );

        byte[] loaded = storage.load(key);
        storage.delete(key);

        assertThat(key).startsWith("race-1/").endsWith(".png");
        assertThat(loaded).isEqualTo(original);

        ArgumentCaptor<PutObjectRequest> putRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(putRequest.capture(), any(RequestBody.class));
        assertThat(putRequest.getValue().bucket()).isEqualTo("race-media");
        assertThat(putRequest.getValue().key()).isEqualTo(key);
        assertThat(putRequest.getValue().contentType()).isEqualTo("image/png");
        assertThat(putRequest.getValue().acl()).isEqualTo(ObjectCannedACL.PRIVATE);

        ArgumentCaptor<DeleteObjectRequest> deleteRequest = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(deleteRequest.capture());
        assertThat(deleteRequest.getValue().bucket()).isEqualTo("race-media");
        assertThat(deleteRequest.getValue().key()).isEqualTo(key);
    }

    @Test
    void minioProviderUsesTheSameS3CompatibleOperations() {
        MinioMediaStorage storage = new MinioMediaStorage(client, "race-media");
        byte[] thumbnail = new byte[]{4, 5, 6};

        String key = storage.storeBytes(thumbnail, ".jpg", "race-1");

        ArgumentCaptor<PutObjectRequest> putRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(putRequest.capture(), any(RequestBody.class));
        assertThat(key).startsWith("race-1/").endsWith(".jpg");
        assertThat(putRequest.getValue().bucket()).isEqualTo("race-media");
        assertThat(putRequest.getValue().key()).isEqualTo(key);
        assertThat(putRequest.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void missingObjectMapsToExistingNotFoundSemantics() {
        AwsS3MediaStorage storage = new AwsS3MediaStorage(client, "race-media");
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().statusCode(404).build());

        assertThatThrownBy(() -> storage.load("race-1/missing.jpg"))
            .isInstanceOf(com.racetracker.exception.ResourceNotFoundException.class)
            .hasMessage("Media file not found");
    }

    @Test
    void rejectsTraversalKeysBeforeCallingObjectStorage() {
        AwsS3MediaStorage storage = new AwsS3MediaStorage(client, "race-media");

        assertThatThrownBy(() -> storage.load("race-1/../private.jpg"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Invalid storage key");
        verifyNoInteractions(client);
    }

    @Test
    void appliesTheSameSizeLimitToGeneratedObjects() {
        AwsS3MediaStorage storage = new AwsS3MediaStorage(client, "race-media");

        assertThatThrownBy(() -> storage.storeBytes(new byte[(15 * 1024 * 1024) + 1], ".jpg", "race-1"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Stored content exceeds the maximum allowed size of 15MB");
        verifyNoInteractions(client);
    }

    @Test
    void surfacesObjectDeletionFailures() {
        AwsS3MediaStorage storage = new AwsS3MediaStorage(client, "race-media");
        when(client.deleteObject(any(DeleteObjectRequest.class)))
            .thenThrow(S3Exception.builder().statusCode(503).build());

        assertThatThrownBy(() -> storage.delete("race-1/photo.jpg"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Failed to delete file");
    }
}
