package com.racetracker.service.impl;

import software.amazon.awssdk.services.s3.S3Client;

public class AwsS3MediaStorage extends S3MediaStorageSupport {

    public AwsS3MediaStorage(S3Client client, String bucket) {
        super(client, bucket);
    }
}
