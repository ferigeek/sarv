package com.github.ferigeek.sarv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class SarvApplication {

	public static void main(String[] args) {
		SpringApplication.run(SarvApplication.class, args);
	}

}
