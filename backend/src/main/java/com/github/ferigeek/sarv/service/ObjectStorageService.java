package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.StoredObject;
import org.springframework.core.io.Resource;

public interface ObjectStorageService {

    StoredObject uploadObject(byte[] bytes, String mimeType);
    Resource download(String objectKey);
    void delete(String objectKey);
}
