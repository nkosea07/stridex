package com.racetracker.service.impl;

import software.amazon.awssdk.services.s3.S3Client;

public class MinioMediaStorage extends S3MediaStorageSupport {

    public MinioMediaStorage(S3Client client, String bucket) {
        super(client, bucket);
    }
}
