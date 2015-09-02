package com.feedxl;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpledb.AmazonSimpleDBAsyncClient;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;

public class SimpleDBConnectionHelper {

    public AmazonSimpleDBClient createSimpleDBConnection(String profileName, String regionName) {
        AWSCredentials credentials = getAwsCredentials(profileName);
        AmazonSimpleDBClient simpleDBClient = new AmazonSimpleDBClient(credentials);
        setRegion(simpleDBClient, regionName);
        return simpleDBClient;
    }

    public AmazonSimpleDBAsyncClient createSimpleDBAsyncConnection(String profileName, String regionName) {
        AWSCredentials credentials = getAwsCredentials(profileName);
        AmazonSimpleDBAsyncClient simpleDBClient = new AmazonSimpleDBAsyncClient(credentials);
        setRegion(simpleDBClient, regionName);
        return simpleDBClient;
    }

    private void setRegion(AmazonSimpleDBClient simpleDBClient, String regionName) {
        Region region = Region.getRegion(Regions.fromName(regionName));
        simpleDBClient.setRegion(region);
    }

    private AWSCredentials getAwsCredentials(String profileName) {
        return new ProfileCredentialsProvider(profileName).getCredentials();
    }
}