package com.flashdrop;

import org.springframework.boot.SpringApplication;

public class TestFlashdropApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlashdropApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
