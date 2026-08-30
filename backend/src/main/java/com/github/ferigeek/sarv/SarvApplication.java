package com.github.ferigeek.sarv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableAspectJAutoProxy
// Serializes `Page` responses through a stable DTO instead of the unstable default `PageImpl` serialization.
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class SarvApplication {

	public static void main(String[] args) {
		SpringApplication.run(SarvApplication.class, args);
	}

}
