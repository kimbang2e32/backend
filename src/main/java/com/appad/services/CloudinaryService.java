package com.appad.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(@org.springframework.beans.factory.annotation.Value("${cloudinary.cloud_name}") String cloudName,
                             @org.springframework.beans.factory.annotation.Value("${cloudinary.api_key}") String apiKey,
                             @org.springframework.beans.factory.annotation.Value("${cloudinary.api_secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", apiSecret
        ));
    }

    public Map uploadFile(MultipartFile file, String folder) {
        try {
            Map params = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto"
            );
            return cloudinary.uploader().upload(file.getBytes(), params);
        } catch (IOException e) {
            throw new RuntimeException("Cloudinary upload failed: " + e.getMessage());
        }
    }
    
    public void deleteFile(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
