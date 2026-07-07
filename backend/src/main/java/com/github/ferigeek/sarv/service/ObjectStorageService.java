package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.StoredObject;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface ObjectStorageService {

    StoredObject uploadObject(MultipartFile file);
    Resource download(String objectKey);
    void delete(String objectKey);
}
