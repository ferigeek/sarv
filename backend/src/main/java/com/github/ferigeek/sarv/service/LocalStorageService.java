package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.StoredObject;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalStorageService implements ObjectStorageService {

    @Override
    public StoredObject uploadObject(MultipartFile file) {
        return null;
    }

    @Override
    public Resource download(String objectKey) {
        // TODO
        return null;
    }

    @Override
    public void delete(String objectKey) {
        // TODO
    }
}